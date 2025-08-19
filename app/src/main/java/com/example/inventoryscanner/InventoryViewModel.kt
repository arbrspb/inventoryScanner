package com.example.inventoryscanner

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventoryscanner.data.AppDatabase
import com.example.inventoryscanner.data.InventoryRepository
import com.example.inventoryscanner.data.ItemDbStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Статусы для UI ---
enum class ItemStatus {
    UNKNOWN, AVAILABLE, CHECKED_OUT, ERROR
}

// --- UI состояния сканирования ---
@Immutable
data class ScanUIState(
    val rawCode: String? = null,
    val message: String = "Результат сканирования будет тут",
    val itemStatus: ItemStatus = ItemStatus.UNKNOWN,
    val isProcessing: Boolean = false
)

// --- Модель элемента в списке ---
@Immutable
data class InventoryListItem(
    val code: String,
    val name: String?,
    val status: ItemStatus,
    val takenCount: Int,
    val quantity: Int,
    val lastStatusTs: Long? = null,
    val lastStatusText: String = "—" // предформатированная дата для UI
)

// --- Состояние диалога сверки комплекта ---
@Immutable
data class KitCheckState(
    val missing: List<String> = emptyList(),
    val extra: List<String> = emptyList(),
    val totalTemplate: Int = 0,
    val matched: Int = 0,
    val showDialog: Boolean = false
)

// --- События для звука/вибро ---
sealed class ScanEvent {
    data class Taken(val code: String) : ScanEvent()
    data class ReturnPending(val code: String) : ScanEvent()
    data class Returned(val code: String) : ScanEvent()
}

class InventoryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repository = InventoryRepository(db.itemDao(), db.logDao())

    // --- Настройки двойного скана для возврата ---
    private val REQUIRE_DOUBLE_SCAN_TO_RETURN = true
    private val doubleScanReturnWindowMs = 10_000L

    private var pendingReturnCode: String? = null
    private var pendingReturnSetAt: Long = 0L

    // Мини-настройки сглаживания списка
    private val LIST_DEBOUNCE_MS = 24L

    // Управление политикой сортировки: по времени или стабильно по коду
    private val SORT_BY_TIME = false

    // UI состояния
    private val _uiState = MutableStateFlow(ScanUIState())
    val uiState: StateFlow<ScanUIState> = _uiState

    // Список предметов
    private val _items = MutableStateFlow<List<InventoryListItem>>(emptyList())
    val items: StateFlow<List<InventoryListItem>> = _items

    // Состояние сверки комплекта
    private val _kitCheckState = MutableStateFlow(KitCheckState())
    val kitCheckState: StateFlow<KitCheckState> = _kitCheckState

    // События сканирования (для MainActivity)
    private val _scanEvents = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 8)
    val scanEvents: SharedFlow<ScanEvent> = _scanEvents

    init {
        // Подписка на изменения в БД с вынесенным маппингом и сортировкой с главного потока
        viewModelScope.launch {
            repository.observeItems()
                .map { list ->
                    // ОДИН форматер на одну эмиссию (не на каждый элемент и не в UI)
                    val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    val mapped = list.map { e ->
                        val ts = e.lastActionTs
                        val text = ts?.let { df.format(Date(it)) } ?: "—"
                        InventoryListItem(
                            code = e.code,
                            name = e.name,
                            status = if (e.status == ItemDbStatus.CHECKED_OUT)
                                ItemStatus.CHECKED_OUT else ItemStatus.AVAILABLE,
                            takenCount = e.takenCount,
                            quantity = e.quantity,
                            lastStatusTs = ts,
                            lastStatusText = text
                        )
                    }
                    if (SORT_BY_TIME) {
                        mapped.sortedWith(
                            compareByDescending<InventoryListItem> { it.status == ItemStatus.CHECKED_OUT }
                                .thenByDescending { it.lastStatusTs ?: 0L }
                        )
                    } else {
                        // Стабильная сортировка: меньше перестановок при скролле
                        mapped.sortedWith(
                            compareByDescending<InventoryListItem> { it.status == ItemStatus.CHECKED_OUT }
                                .thenBy { it.code }
                        )
                    }
                }
                .flowOn(Dispatchers.Default) // тяжёлую работу — не на Main
                .distinctUntilChanged()      // пропуск одинаковых списков
                .conflate()                  // скидываем промежуточные обновления
                .debounce(LIST_DEBOUNCE_MS)  // сгладить бурсты
                .collect { sorted -> _items.value = sorted }
        }
    }

    fun onBarcodeScanned(code: String) {
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            rawCode = code,
            message = "Скан: $code",
            isProcessing = true
        )
        viewModelScope.launch {
            try {
                val existing = repository.getItem(code)
                if (existing == null) {
                    // Создаём и сразу берём
                    val created = repository.createItem(code, now)
                    repository.markTaken(created, now)
                    pendingReturnCode = null
                    updateUI(ItemStatus.CHECKED_OUT, "Код $code: добавлен и ВЗЯТО")
                    _scanEvents.tryEmit(ScanEvent.Taken(code))
                } else {
                    if (existing.status == ItemDbStatus.AVAILABLE) {
                        repository.markTaken(existing, now)
                        pendingReturnCode = null
                        updateUI(
                            ItemStatus.CHECKED_OUT,
                            "Код $code: ВЗЯТО (счётчик: ${existing.takenCount + 1})"
                        )
                        _scanEvents.tryEmit(ScanEvent.Taken(code))
                    } else {
                        // Уже CHECKED_OUT → возврат
                        if (REQUIRE_DOUBLE_SCAN_TO_RETURN) {
                            val same = (pendingReturnCode == existing.code)
                            val stillInWindow = (now - pendingReturnSetAt) <= doubleScanReturnWindowMs
                            if (same && stillInWindow) {
                                repository.markReturned(existing, now, auto = false)
                                pendingReturnCode = null
                                updateUI(ItemStatus.AVAILABLE, "Код $code: ВОЗВРАЩЁН")
                                _scanEvents.tryEmit(ScanEvent.Returned(code))
                            } else {
                                pendingReturnCode = existing.code
                                pendingReturnSetAt = now
                                updateUI(ItemStatus.CHECKED_OUT, "Код $code: повторное сканирование для возврата")
                                _scanEvents.tryEmit(ScanEvent.ReturnPending(code))
                            }
                        } else {
                            repository.markReturned(existing, now, auto = false)
                            pendingReturnCode = null
                            updateUI(ItemStatus.AVAILABLE, "Код $code: ВОЗВРАЩЁН")
                            _scanEvents.tryEmit(ScanEvent.Returned(code))
                        }
                    }
                }
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка: ${e.message}")
            }
        }
    }

    fun incQuantity(code: String) {
        viewModelScope.launch {
            try {
                repository.incQuantity(code, 1)
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка увеличения количества: ${e.message}")
            }
        }
    }

    fun decQuantity(code: String) {
        viewModelScope.launch {
            try {
                repository.decQuantity(code)
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка уменьшения количества: ${e.message}")
            }
        }
    }

    fun setQuantity(code: String, q: Int) {
        viewModelScope.launch {
            try {
                repository.setQuantity(code, q)
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка установки количества: ${e.message}")
            }
        }
    }

    fun resetAllCounts() {
        viewModelScope.launch {
            try {
                repository.resetAllTakenCounts()
                updateUI(_uiState.value.itemStatus, "Все счётчики взятий обнулены")
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка массового сброса: ${e.message}")
            }
        }
    }

    fun deleteItem(code: String, deleteLogs: Boolean = false) {
        viewModelScope.launch {
            try {
                repository.deleteItem(code, deleteLogs)
                if (_uiState.value.rawCode == code) _uiState.value = ScanUIState()
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка удаления $code: ${e.message}")
            }
        }
    }

    fun markReturned() {
        val code = _uiState.value.rawCode ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val item = db.itemDao().getByCode(code)
                if (item == null) {
                    updateUI(ItemStatus.UNKNOWN, "Код $code не найден")
                    return@launch
                }
                if (item.status == ItemDbStatus.CHECKED_OUT) {
                    repository.markReturned(item, now, auto = false)
                    updateUI(ItemStatus.AVAILABLE, "Код $code: ВОЗВРАЩЁН")
                    _scanEvents.tryEmit(ScanEvent.Returned(code))
                } else {
                    updateUI(ItemStatus.AVAILABLE, "Код $code уже доступен")
                }
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка возврата: ${e.message}")
            }
        }
    }

    // --- Сверка комплекта (assets/kit_template.json) ---
    fun runKitCheck() {
        val ctx: Context = getApplication()
        viewModelScope.launch {
            try {
                val template = loadKitTemplate(ctx)
                val templateCodes = template.map { it.code }.toSet()
                val currentCodes = _items.value.map { it.code }.toSet()

                val missing = (templateCodes - currentCodes).toList().sorted()
                val extra = (currentCodes - templateCodes).toList().sorted()
                val matched = (templateCodes intersect currentCodes).size
                val total = templateCodes.size

                _kitCheckState.value = KitCheckState(
                    missing = missing,
                    extra = extra,
                    totalTemplate = total,
                    matched = matched,
                    showDialog = true
                )
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка сверки: ${e.message}")
            }
        }
    }

    fun dismissKitDialog() {
        _kitCheckState.update { it.copy(showDialog = false) }
    }

    private fun updateUI(status: ItemStatus, message: String) {
        _uiState.update { it.copy(itemStatus = status, message = message, isProcessing = false) }
    }

    // --- Загрузка шаблона комплекта из assets/kit_template.json ---
    private data class KitTemplateEntry(val code: String)

    private fun loadKitTemplate(ctx: Context): List<KitTemplateEntry> {
        val input = ctx.assets.open("kit_template.json")
        val text = input.use { it.readBytes().toString(Charset.forName("UTF-8")) }
        val arr = JSONArray(text)
        val res = ArrayList<KitTemplateEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val code = obj.optString("code").trim()
            if (code.isNotEmpty()) res.add(KitTemplateEntry(code))
        }
        return res
    }
}