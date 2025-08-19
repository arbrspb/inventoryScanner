package com.example.inventoryscanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.inventoryscanner.ui.theme.InventoryScannerTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

private const val CAMERA_REQ = 123

class MainActivity : ComponentActivity() {

    private var pendingScan = false
    private val inventoryViewModel: InventoryViewModel by viewModels()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { inventoryViewModel.onBarcodeScanned(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureCameraPermission()

        setContent {
            InventoryScannerTheme {
                val uiState by inventoryViewModel.uiState.collectAsState()
                val kitCheckState by inventoryViewModel.kitCheckState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    InventoryScannerScreen(
                        modifier = Modifier.padding(padding),
                        message = uiState.message,
                        isProcessing = uiState.isProcessing,
                        itemStatus = uiState.itemStatus,
                        itemsFlow = inventoryViewModel.items,
                        kitCheckState = kitCheckState,
                        onScanClicked = {
                            if (!hasCameraPermission()) {
                                pendingScan = true
                                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQ)
                            } else {
                                startScan()
                            }
                        },
                        onResetClicked = { inventoryViewModel.resetAllCounts() },
                        onReturnClicked = { inventoryViewModel.markReturned() },
                        onToggleCode = { code -> inventoryViewModel.onBarcodeScanned(code) },
                        onDeleteCode = { code -> inventoryViewModel.deleteItem(code, deleteLogs = false) },
                        onIncQuantity = { code -> inventoryViewModel.incQuantity(code) },
                        onDecQuantity = { code -> inventoryViewModel.decQuantity(code) },
                        onSetQuantity = { code, q -> inventoryViewModel.setQuantity(code, q) },
                        onKitCheck = { inventoryViewModel.runKitCheck() },
                        onDismissKitDialog = { inventoryViewModel.dismissKitDialog() },
                        useLiteRow = true
                    )
                }
            }
        }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setPrompt("Наведите камеру на штрих-код")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun ensureCameraPermission() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingScan) {
                    pendingScan = false
                    startScan()
                }
            }
        }
    }
}

// ToneGenerator (ленивое создание)
private var toneGen: ToneGenerator? = null
private fun playTone(tone: Int, durationMs: Int = 120) {
    if (toneGen == null) {
        toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }
    toneGen?.startTone(tone, durationMs)
}

@Composable
fun InventoryScannerScreen(
    modifier: Modifier = Modifier,
    message: String,
    isProcessing: Boolean,
    itemStatus: ItemStatus,
    itemsFlow: StateFlow<List<InventoryListItem>>,
    kitCheckState: KitCheckState,
    onScanClicked: () -> Unit,
    onResetClicked: () -> Unit,
    onReturnClicked: () -> Unit,
    onToggleCode: (String) -> Unit,
    onDeleteCode: (String) -> Unit,
    onIncQuantity: (String) -> Unit,
    onDecQuantity: (String) -> Unit,
    onSetQuantity: (String, Int) -> Unit,
    onKitCheck: () -> Unit,
    onDismissKitDialog: () -> Unit,
    useLiteRow: Boolean
) {
    val listState = rememberLazyListState()

    // «Заморозка» данных — применяем новые списки только когда скролл не идёт
    var uiItems by remember { mutableStateOf(itemsFlow.value) }
    LaunchedEffect(itemsFlow, listState) {
        combine(
            itemsFlow,
            snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged()
        ) { list, scrolling -> list to scrolling }
            .collect { (list, scrolling) ->
                if (!scrolling) uiItems = list
            }
    }

    // Диалоги на уровне экрана
    var qtyDialogItem by remember { mutableStateOf<InventoryListItem?>(null) }
    var qtyInput by remember { mutableStateOf(TextFieldValue("")) }
    var deleteDialogItem by remember { mutableStateOf<InventoryListItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScanClicked,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) { Text("Сканировать") }

            Button(
                onClick = onResetClicked,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) { Text("Сброс") }

            Button(
                onClick = onKitCheck,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) { Text("Сверка") }
        }

        Spacer(Modifier.height(8.dp))

        if (itemStatus == ItemStatus.CHECKED_OUT && !isProcessing) {
            Button(
                onClick = onReturnClicked,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Вернуть последний") }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = if (isProcessing) "$message ..." else message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(
                items = uiItems,
                key = { it.code },
                contentType = { "inventory_row_lite" }
            ) { rowItem ->
                if (useLiteRow) {
                    EquipmentRowLite(
                        item = rowItem,
                        onToggle = onToggleCode,
                        onRequestDelete = { deleteDialogItem = it },
                        onRequestSetQuantity = {
                            qtyDialogItem = it
                            qtyInput = TextFieldValue(it.quantity.toString())
                        },
                        onIncQuantity = onIncQuantity,
                        onDecQuantity = onDecQuantity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                } else {
                    EquipmentRowMaterial(
                        item = rowItem,
                        onToggle = onToggleCode,
                        onRequestDelete = { deleteDialogItem = it },
                        onRequestSetQuantity = {
                            qtyDialogItem = it
                            qtyInput = TextFieldValue(it.quantity.toString())
                        },
                        onIncQuantity = onIncQuantity,
                        onDecQuantity = onDecQuantity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
                Divider()
            }
        }
    }

    // Диалог установки количества
    qtyDialogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { qtyDialogItem = null },
            title = { Text("Установить количество") },
            text = {
                OutlinedTextField(
                    value = qtyInput,
                    onValueChange = { v ->
                        if (v.text.all { it.isDigit() } || v.text.isEmpty()) {
                            qtyInput = v
                        }
                    },
                    singleLine = true,
                    label = { Text("Количество") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = qtyInput.text.toIntOrNull()
                    if (value != null) onSetQuantity(item.code, value)
                    qtyDialogItem = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { qtyDialogItem = null }) { Text("Отмена") }
            }
        )
    }

    // Диалог удаления
    deleteDialogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteDialogItem = null },
            title = { Text("Подтверждение") },
            text = { Text("Удалить код ${item.code}?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogItem = null
                    onDeleteCode(item.code)
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogItem = null }) { Text("Отмена") }
            }
        )
    }

    // Диалог сверки комплекта
    if (kitCheckState.showDialog) {
        val coverage = if (kitCheckState.totalTemplate == 0) 0
        else (kitCheckState.matched * 100 / kitCheckState.totalTemplate)
        AlertDialog(
            onDismissRequest = onDismissKitDialog,
            title = { Text("Сверка комплекта") },
            text = {
                Column {
                    Text("Шаблон: ${kitCheckState.matched}/${kitCheckState.totalTemplate} (${coverage}%)")
                    if (kitCheckState.missing.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Не найдено (${kitCheckState.missing.size}):", style = MaterialTheme.typography.labelSmall)
                        Text(kitCheckState.missing.joinToString(", "))
                    }
                    if (kitCheckState.extra.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Лишние (${kitCheckState.extra.size}):", style = MaterialTheme.typography.labelSmall)
                        Text(kitCheckState.extra.joinToString(", "))
                    }
                    if (kitCheckState.missing.isEmpty() && kitCheckState.extra.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Всё соответствует шаблону ✅")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissKitDialog) { Text("OK") } }
        )
    }
}

/*
 Лёгкая строка списка (без Material-кнопок и без рипплов).
 Используем indication = null и собственный MutableInteractionSource у каждого clickable.
*/
@Composable
fun EquipmentRowLite(
    item: InventoryListItem,
    onToggle: (String) -> Unit,
    onRequestDelete: (InventoryListItem) -> Unit,
    onRequestSetQuantity: (InventoryListItem) -> Unit,
    onIncQuantity: (String) -> Unit,
    onDecQuantity: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        // Верхняя строка: имя/код + чип действия
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                BasicText(text = item.name ?: item.code, modifier = Modifier.padding(end = 8.dp))
                BasicText(text = "Код: ${item.code}", modifier = Modifier.padding(top = 2.dp))
            }
            val isTaken = item.status == ItemStatus.CHECKED_OUT
            val bg = if (isTaken) Color(0xFFFFE5E5) else Color(0xFFE6F4EA)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = interaction,
                        indication = null
                    ) { onToggle(item.code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(text = if (isTaken) "Вернуть" else "Взять")
            }
        }

        // Статус и дата
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusText = if (item.status == ItemStatus.CHECKED_OUT) "ВЗЯТО" else "Свободно"
            BasicText(text = "Статус: $statusText (взят раз: ${item.takenCount})", modifier = Modifier.weight(1f))
            BasicText(text = "Изм: ${item.lastStatusText}", modifier = Modifier.padding(start = 8.dp))
        }

        // Кол-во и удалить
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF1F1F1))
                    .clickable(interactionSource = interaction, indication = null) { onDecQuantity(item.code) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { BasicText(text = "−") }

            Spacer(Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF1F1F1))
                    .clickable(interactionSource = interaction, indication = null) { onIncQuantity(item.code) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { BasicText(text = "+") }

            Spacer(Modifier.width(10.dp))

            BasicText(
                text = "Кол: ${item.quantity}",
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(interactionSource = interaction, indication = null) { onRequestSetQuantity(item) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            Spacer(Modifier.weight(1f))

            BasicText(
                text = "Удалить",
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFF3E0))
                    .clickable(interactionSource = interaction, indication = null) { onRequestDelete(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/*
 «Материальная» строка для сравнения (можно выключить useLiteRow, чтобы использовать её).
*/
@Composable
fun EquipmentRowMaterial(
    item: InventoryListItem,
    onToggle: (String) -> Unit,
    onRequestDelete: (InventoryListItem) -> Unit,
    onRequestSetQuantity: (InventoryListItem) -> Unit,
    onIncQuantity: (String) -> Unit,
    onDecQuantity: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(text = item.name ?: item.code, style = MaterialTheme.typography.bodyLarge)
                Text(text = "Код: ${item.code}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onToggle(item.code) }) {
                Text(if (item.status == ItemStatus.CHECKED_OUT) "Вернуть" else "Взять")
            }
        }

        val statusText = if (item.status == ItemStatus.CHECKED_OUT) "ВЗЯТО" else "Свободно"
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Статус: $statusText (взят раз: ${item.takenCount})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(text = "Изм: ${item.lastStatusText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onDecQuantity(item.code) }) { Text("−") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { onIncQuantity(item.code) }) { Text("+") }
            Spacer(Modifier.width(10.dp))
            Text(text = "Кол: ${item.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { onRequestSetQuantity(item) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onRequestDelete(item) }) { Text("Удалить") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InventoryScannerPreview() {
    InventoryScannerScreen(
        message = "Пример",
        isProcessing = false,
        itemStatus = ItemStatus.AVAILABLE,
        itemsFlow = MutableStateFlow(
            listOf(
                InventoryListItem("CODE1", "Trubosound iq", ItemStatus.AVAILABLE, 0, 5, System.currentTimeMillis(), "19.08.2025 19:00"),
                InventoryListItem("CODE2", null, ItemStatus.CHECKED_OUT, 3, 2, System.currentTimeMillis() - 3600_000, "19.08.2025 18:00")
            )
        ),
        kitCheckState = KitCheckState(),
        onScanClicked = {},
        onResetClicked = {},
        onReturnClicked = {},
        onToggleCode = {},
        onDeleteCode = {},
        onIncQuantity = {},
        onDecQuantity = {},
        onSetQuantity = { _, _ -> },
        onKitCheck = {},
        onDismissKitDialog = {},
        useLiteRow = true
    )
}