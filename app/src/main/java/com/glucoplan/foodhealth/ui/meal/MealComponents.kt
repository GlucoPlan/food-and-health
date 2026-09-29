package com.glucoplan.foodhealth.ui.meal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.meal.MealField
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.ui.format.grams
import com.glucoplan.foodhealth.ui.format.mealTime
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate
import com.glucoplan.foodhealth.ui.picker.PickedItem
import com.glucoplan.foodhealth.ui.picker.WeightDialog

/**
 * Результаты других экранов (выбор, сканер, создание продукта) для редактора приёма
 * и переход к созданию продукта по неизвестному коду.
 */
@Composable
fun MealEditorEffects(
    viewModel: MealEditorViewModel,
    picked: PickedItem?,
    scannedBarcode: String?,
    createdProductId: String?,
    onResultsHandled: () -> Unit,
    onCreateProduct: (barcode: String) -> Unit,
) {
    val createWithBarcode by viewModel.createWithBarcode.collectAsStateWithLifecycle()
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
}

/**
 * Кому, когда, добавление и позиции приёма (ТЗ 4.1), с диалогами веса, варки и времени.
 * [allowNow] — можно вернуть время к «сейчас» (у записанного приёма время всегда задано).
 */
@Composable
fun MealEditor(
    viewModel: MealEditorViewModel,
    state: MealUi,
    onAdd: (profileId: String?) -> Unit,
    onScan: () -> Unit,
    allowNow: Boolean,
    modifier: Modifier = Modifier,
) {
    var choosingTime by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            AssistChip(
                onClick = { choosingTime = true },
                label = { Text(state.eatenAt?.let { mealTime(it) } ?: "Сейчас") },
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
            )
            if (allowNow && state.eatenAt != null) {
                TextButton(onClick = { viewModel.onTimeSelected(null) }) { Text("Сейчас") }
            }
        }
        state.errors[MealField.TIME]?.let { ErrorText(it, Modifier.padding(horizontal = 16.dp)) }

        // Добавить
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
            OutlinedCard(onClick = { onAdd(state.profileId) }, modifier = Modifier.weight(1f)) {
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
internal fun MealRowItem(row: MealRow, onInput: (String) -> Unit, onRemove: () -> Unit, onChooseVersion: () -> Unit) {
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
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}
