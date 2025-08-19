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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material3.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventoryscanner.ui.theme.InventoryScannerTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

//private val overscroll: Any
//private val overscroll: Any
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
                val uiState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
                val items by inventoryViewModel.items.collectAsStateWithLifecycle()
                val kitCheckState by inventoryViewModel.kitCheckState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    InventoryScannerScreen(
                        modifier = Modifier.padding(padding),
                        message = uiState.message,
                        isProcessing = uiState.isProcessing,
                        itemStatus = uiState.itemStatus,
                        items = items,
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
                        onDismissKitDialog = { inventoryViewModel.dismissKitDialog() }
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
    items: List<InventoryListItem>,
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
    onDismissKitDialog: () -> Unit
) {

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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
            ) { Text("Скан") }

            Button(
                onClick = onResetClicked,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) { Text("Сброс") }

            OutlinedButton(
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
                items = items,
                key = { it.code }
            ) { rowItem ->
                EquipmentRow(
                    item = rowItem,
                    onToggle = onToggleCode,
                    onDelete = onDeleteCode,
                    onIncQuantity = onIncQuantity,
                    onDecQuantity = onDecQuantity,
                    onSetQuantity = onSetQuantity
                )
                Divider()
            }
        }
    }

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
                        Text(
                            "Не найдено (${kitCheckState.missing.size}):",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(kitCheckState.missing.joinToString(", "))
                    }
                    if (kitCheckState.extra.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Лишние (${kitCheckState.extra.size}):",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(kitCheckState.extra.joinToString(", "))
                    }
                    if (kitCheckState.missing.isEmpty() && kitCheckState.extra.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Все соответствует шаблону ✅")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissKitDialog) { Text("OK") }
            }
        )
    }
}

@Composable
fun EquipmentRow(
    item: InventoryListItem,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onIncQuantity: (String) -> Unit,
    onDecQuantity: (String) -> Unit,
    onSetQuantity: (String, Int) -> Unit
) {
    var showQtyDialog by remember { mutableStateOf(false) }
    var qtyInput by remember { mutableStateOf(TextFieldValue(item.quantity.toString())) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showQtyDialog) {
        AlertDialog(
            onDismissRequest = { showQtyDialog = false },
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
                    showQtyDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showQtyDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Удалить код ${item.code}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(item.code)
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    val statusText = if (item.status == ItemStatus.CHECKED_OUT) "ВЗЯТО" else "Свободно"
    val statusColor = if (item.status == ItemStatus.CHECKED_OUT)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = item.name ?: item.code, style = MaterialTheme.typography.bodyLarge)
            Text(text = "Код: ${item.code}", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Статус: $statusText (взят раз: ${item.takenCount})",
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
            Text(
                text = "Изм: ${formatStatusTime(item.lastStatusTs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onDecQuantity(item.code) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("−", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = { onIncQuantity(item.code) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Кол: ${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable {
                            qtyInput = TextFieldValue(item.quantity.toString())
                            showQtyDialog = true
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.status == ItemStatus.CHECKED_OUT) {
                Button(onClick = { onToggle(item.code) }) { Text("Вернуть") }
            } else {
                OutlinedButton(onClick = { onToggle(item.code) }) { Text("Взять") }
            }
            OutlinedButton(onClick = { showDeleteDialog = true }) { Text("Удалить") }
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
        items = listOf(
            InventoryListItem("CODE1", "Trubosound iq", ItemStatus.AVAILABLE, 0, 5, System.currentTimeMillis()),
            InventoryListItem("CODE2", null, ItemStatus.CHECKED_OUT, 3, 2, System.currentTimeMillis() - 3600_000)
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
        onDismissKitDialog = {}
    )
}