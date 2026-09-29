package com.glucoplan.foodhealth.ui.meal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.meal.MealField
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.ui.format.grams
import com.glucoplan.foodhealth.ui.format.mealTime
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate
import com.glucoplan.foodhealth.ui.picker.PickedItem
import com.glucoplan.foodhealth.ui.picker.WeightDialog

/** Главный экран (ТЗ 4.1): кому, когда, позиции, итоги, «Записать». */
@Composable
fun MealScreen(
    onAdd: (profileId: String?) -> Unit,
    onScan: () -> Unit,
    onCreateProduct: (barcode: String) -> Unit,
    picked: PickedItem?,
    scannedBarcode: String?,
    createdProductId: String?,
    onResultsHandled: () -> Unit,
    viewModel: MealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val createWithBarcode by viewModel.createWithBarcode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var choosingTime by remember { mutableStateOf(false) }

    LaunchedEffect(picked, scannedBarcode, createdProductId) {
        picked?.let(viewModel::onPicked)
        scannedBarcode?.let(viewModel::onScanned)
        createdProductId?.let(viewModel::onProductCreated)
        if (picked != null || scannedBarcode != null || createdProductId != null) onResultsHandled()
    }
    LaunchedEffect(createWithBarcode) {
        createWithBarcode?.let {
            viewModel.onCreateWithBarcodeHandled()
            onCreateProduct(it)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            viewModel.onMessageShown()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (state.loaded) TotalsBar(state, onRecord = viewModel::record) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Кому
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                state.profiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == state.profileId,
                        onClick = { viewModel.onProfileSelected(profile.id) },
                        label = { Text(profile.name) },
                    )
                }
            }

            // Когда
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                AssistChip(
                    onClick = { choosingTime = true },
                    label = { Text(state.eatenAt?.let { mealTime(it) } ?: "Сейчас") },
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                )
                if (state.eatenAt != null) {
                    TextButton(onClick = { viewModel.onTimeSelected(null) }) { Text("Сейчас") }
                }
            }
            state.errors[MealField.TIME]?.let { ErrorText(it, Modifier.padding(horizontal = 16.dp)) }

            // Добавить
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            ) {
                OutlinedCard(
                    onClick = { onAdd(state.profileId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Text("Добавить продукт или блюдо", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onScan) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать штрихкод")
                }
            }
            state.errors[MealField.ITEMS]?.let { ErrorText(it, Modifier.padding(horizontal = 16.dp)) }

            if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        "Добавьте, что едите: поиском, из недавних или сканером штрихкода.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.rows, key = { it.item.key }) { row ->
                        MealRowItem(
                            row = row,
                            onInput = { viewModel.onInputChange(row.item.key, it) },
                            onRemove = { viewModel.onRemove(row.item.key) },
                            onChooseVersion = { viewModel.onChooseVersion(row.item.key) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.pending?.let { p ->
        WeightDialog(
            title = p.title,
            pieceWeightG = p.pieceWeightG,
            per100 = p.per100,
            onConfirm = viewModel::onWeightConfirmed,
            onDismiss = viewModel::onWeightDismissed,
        )
    }

    if (state.versionChoiceKey != null) {
        AlertDialog(
            onDismissRequest = viewModel::onVersionDismissed,
            title = { Text("Какая варка?") },
            text = {
                Column {
                    state.versions.forEach { v ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onVersionChosen(v.id) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(shortDate(v.createdAt) + if (v.isCurrent) " · текущая" else "")
                            Text(
                                nutritionLine(v.per100) + " на 100 г",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::onVersionDismissed) { Text("Отмена") } },
        )
    }

    if (choosingTime) {
        MealTimeDialogs(
            initial = state.eatenAt ?: System.currentTimeMillis(),
            onSelected = { viewModel.onTimeSelected(it); choosingTime = false },
            onDismiss = { choosingTime = false },
        )
    }
}

@Composable
private fun MealRowItem(row: MealRow, onInput: (String) -> Unit, onRemove: () -> Unit, onChooseVersion: () -> Unit) {
    val item = row.item
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name + if (item.deleted) " (удалён)" else "")
            item.portion?.let {
                Text(
                    nutritionLine(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.byPieces && item.grams != null) {
                Text(grams(item.grams), style = MaterialTheme.typography.bodySmall)
            }
            if (item.type == MealItemType.DISH && item.cookedAt != null) {
                Text(
                    "Варка ${shortDate(item.cookedAt)} · сменить",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onChooseVersion),
                )
            }
        }
        OutlinedTextField(
            value = row.input,
            onValueChange = onInput,
            suffix = { Text(if (row.byPieces) "шт" else "г") },
            singleLine = true,
            isError = row.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Убрать ${item.name}")
        }
    }
}

@Composable
private fun TotalsBar(state: MealUi, onRecord: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Итого: " + nutritionLine(state.total), style = MaterialTheme.typography.titleSmall)
            Button(onClick = onRecord, enabled = state.rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Записать")
            }
        }
    }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}
