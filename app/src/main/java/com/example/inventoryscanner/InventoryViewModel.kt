package com.example.inventoryscanner

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventoryscanner.data.AppDatabase
import com.example.inventoryscanner.data.InventoryRepository
import com.example.inventoryscanner.data.ItemDbStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Статусы для UI ---
enum class ItemStatus {
    UNKNOWN, AVAILABLE, CHECKED_OUT, ERROR
}

// --- UI состояния сканирования ---
data class ScanUIState(
    val rawCode: String? = null,
    val message: String = "Результат сканирования будет тут",
    val itemStatus: ItemStatus = ItemStatus.UNKNOWN,
    val isProcessing: Boolean = false
)

// --- Модель элемента в списке (добавили lastStatusTs) ---
data class InventoryListItem(
    val code: String,
    val name: String?,
    val status: ItemStatus,
    val takenCount: Int,
    val quantity: Int,
    val lastStatusTs: Long? = null
)

// --- Состояние диалога сверки комплекта ---
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

//    init {
//        // Подписка на изменения в БД
//        viewModelScope.launch {
//            repository.observeItems().collect { list ->
//                val mapped = list.map { e ->
//                    InventoryListItem(
//                        code = e.code,
//                        name = e.name,
//                        status = if (e.status == ItemDbStatus.CHECKED_OUT)
//                            ItemStatus.CHECKED_OUT else ItemStatus.AVAILABLE,
//                        takenCount = e.takenCount,
//                        quantity = e.quantity,
//                        lastStatusTs = e.lastActionTs
//                    )
//                }
//                // Сортировка: ВЗЯТО сверху, свежие (lastStatusTs) первыми
//                val sorted = mapped.sortedWith(
//                    compareByDescending<InventoryListItem> { it.status == ItemStatus.CHECKED_OUT }
//                        .thenByDescending { it.lastStatusTs ?: 0L }
//                )
//                _items.value = sorted
//            }
//        }
//    }

    init {
        // Подписка на изменения в БД
        viewModelScope.launch {
            repository.observeItems().collect { list ->
                val mapped = list.map { e ->
                    InventoryListItem(
                        code = e.code,
                        name = e.name,
                        status = if (e.status == ItemDbStatus.CHECKED_OUT)
                            ItemStatus.CHECKED_OUT else ItemStatus.AVAILABLE,
                        takenCount = e.takenCount,
                        quantity = e.quantity,
                        lastStatusTs = e.lastActionTs
                    )
                }
                // Сортировка: ВЗЯТО сверху, свежие (lastStatusTs) первыми
                val sorted = mapped.sortedWith(
                    compareByDescending<InventoryListItem> { it.status == ItemStatus.CHECKED_OUT }
                        .thenByDescending { it.lastStatusTs ?: 0L }
                )

                // --- Оптимизация: мутировать только изменённые элементы ---
                val oldList = _items.value
                if (oldList.size == sorted.size) {
                    val newList = oldList.toMutableList()
                    var changed = false
                    for (i in sorted.indices) {
                        if (oldList[i] != sorted[i]) {
                            newList[i] = sorted[i]
                            changed = true
                        }
                    }
                    if (changed) {
                        _items.value = newList
                    }
                    // Если ничего не изменилось — не триггерим обновление
                } else {
                    _items.value = sorted
                }
            }
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
                            "Код $code: ВЗЯТО (всего: ${existing.takenCount + 1})"
                        )
                        _scanEvents.tryEmit(ScanEvent.Taken(code))
                    } else {
                        // Был CHECKED_OUT
                        if (REQUIRE_DOUBLE_SCAN_TO_RETURN) {
                            if (pendingReturnCode == code &&
                                (now - pendingReturnSetAt) <= doubleScanReturnWindowMs
                            ) {
                                repository.markReturned(existing, now, auto = false)
                                pendingReturnCode = null
                                updateUI(ItemStatus.AVAILABLE, "Код $code: ВОЗВРАЩЁН")
                                _scanEvents.tryEmit(ScanEvent.Returned(code))
                            } else {
                                pendingReturnCode = code
                                pendingReturnSetAt = now
                                updateUI(
                                    ItemStatus.CHECKED_OUT,
                                    "Код $code уже ВЗЯТО. Повторите скан в течение ${(doubleScanReturnWindowMs / 1000)}с чтобы вернуть."
                                )
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

    // --- Оптимизированные функции изменения списка ---

    fun incQuantity(code: String) {
        viewModelScope.launch {
            try {
                repository.incQuantity(code, 1)
                // Мутируем только нужный элемент локального списка
                val currentList = _items.value.toMutableList()
                val idx = currentList.indexOfFirst { it.code == code }
                if (idx >= 0) {
                    val item = currentList[idx]
                    currentList[idx] = item.copy(quantity = item.quantity + 1)
                    _items.value = currentList
                }
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка увеличения количества: ${e.message}")
            }
        }
    }

    fun decQuantity(code: String) {
        viewModelScope.launch {
            try {
                repository.decQuantity(code)
                // Мутируем только нужный элемент локального списка
                val currentList = _items.value.toMutableList()
                val idx = currentList.indexOfFirst { it.code == code }
                if (idx >= 0 && currentList[idx].quantity > 0) {
                    val item = currentList[idx]
                    currentList[idx] = item.copy(quantity = item.quantity - 1)
                    _items.value = currentList
                }
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка уменьшения количества: ${e.message}")
            }
        }
    }

    fun setQuantity(code: String, quantity: Int) {
        viewModelScope.launch {
            try {
                val q = quantity.coerceAtLeast(0)
                repository.setQuantity(code, q)
                // Мутируем только нужный элемент локального списка
                val currentList = _items.value.toMutableList()
                val idx = currentList.indexOfFirst { it.code == code }
                if (idx >= 0) {
                    val item = currentList[idx]
                    currentList[idx] = item.copy(quantity = q)
                    _items.value = currentList
                }
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка установки количества: ${e.message}")
            }
        }
    }

    fun resetCurrentItemCount() {
        val code = _uiState.value.rawCode ?: return
        viewModelScope.launch {
            try {
                repository.resetTakenCount(code)
                updateUI(_uiState.value.itemStatus, "Счётчик взятий обнулён для $code")
            } catch (e: Exception) {
                updateUI(ItemStatus.ERROR, "Ошибка сброса счётчика: ${e.message}")
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
                // Мутируем только нужный элемент локального списка
                val currentList = _items.value.toMutableList()
                val idx = currentList.indexOfFirst { it.code == code }
                if (idx >= 0) {
                    currentList.removeAt(idx)
                    _items.value = currentList
                }
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

    // --- Сверка комплекта (из assets/kit_template.json) ---
    fun runKitCheck() {
        val ctx: Context = getApplication()
        viewModelScope.launch {
            try {
                val template = loadKitTemplate(ctx)
                val templateCodes = template.map { it.code }.toSet()
                val currentCodes = items.value.map { it.code }.toSet()
                val missing = (templateCodes - currentCodes).sorted()
                val extra = (currentCodes - templateCodes).sorted()
                val matched = templateCodes.size - missing.size

                _kitCheckState.value = KitCheckState(
                    missing = missing,
                    extra = extra,
                    totalTemplate = templateCodes.size,
                    matched = matched,
                    showDialog = true
                )
            } catch (_: Exception) {
                // Можно добавить лог (Log.d / Timber) при необходимости
            }
        }
    }

    fun dismissKitDialog() {
        _kitCheckState.update { it.copy(showDialog = false) }
    }

    fun resetUiOnly() {
        _uiState.value = ScanUIState()
    }

    private fun updateUI(status: ItemStatus, message: String) {
        _uiState.value = _uiState.value.copy(
            message = message,
            itemStatus = status,
            isProcessing = false
        )
    }
}