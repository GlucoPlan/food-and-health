package com.glucoplan.foodhealth.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.product.Nutrient
import com.glucoplan.foodhealth.data.product.Nutrients
import com.glucoplan.foodhealth.data.product.ProductField
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductSource

/** Карточка продукта (ТЗ 4.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    onDone: (createdId: String?) -> Unit,
    onScan: () -> Unit,
    scannedBarcode: String?,
    onScannedHandled: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.onScanned(scannedBarcode)
            onScannedHandled()
        }
    }

    LaunchedEffect(state.done) { if (state.done) onDone(state.createdId) }

    Scaffold(
        topBar = {
            TopAppBar(
                // Строку состояния уже учёл внешний Scaffold
                windowInsets = WindowInsets(0),
                title = { Text(if (state.isNew) "Новый продукт" else "Продукт") },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val form = state.form
        val errors = state.errors
        val edit = viewModel::edit

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Основное")
            TextInput("Название", form.name, errors[ProductField.NAME], capitalize = true) { v ->
                edit(ProductField.NAME) { it.copy(name = v) }
            }
            TextInput("Производитель", form.brand, null, capitalize = true) { v ->
                edit("brand") { it.copy(brand = v) }
            }
            TextInput(
                "Штрихкод", form.barcode, errors[ProductField.BARCODE], keyboard = KeyboardType.Number,
                trailing = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать штрихкод")
                    }
                },
            ) { v ->
                edit(ProductField.BARCODE) { it.copy(barcode = v) }
            }

            Section("На 100 г")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput("Ккал", form.kcal, errors[ProductField.KCAL], null, Modifier.weight(1f)) { v ->
                    edit(ProductField.KCAL) { it.copy(kcal = v) }
                }
                NumberInput("ГИ", form.gi, errors[ProductField.GI], null, Modifier.weight(1f), integer = true) { v ->
                    edit(ProductField.GI) { it.copy(gi = v) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput("Белки", form.protein, errors[ProductField.PROTEIN], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.PROTEIN) { it.copy(protein = v) }
                }
                NumberInput("Жиры", form.fat, errors[ProductField.FAT], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.FAT) { it.copy(fat = v) }
                }
                NumberInput("Углеводы", form.carbs, errors[ProductField.CARBS], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.CARBS) { it.copy(carbs = v) }
                }
            }
            errors[ProductField.MACROS]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput("Клетчатка", form.fiber, errors[ProductField.FIBER], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.FIBER) { it.copy(fiber = v) }
                }
                NumberInput("Сахара", form.sugar, errors[ProductField.SUGAR], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.SUGAR) { it.copy(sugar = v) }
                }
                NumberInput("Соль", form.salt, errors[ProductField.SALT], "г", Modifier.weight(1f)) { v ->
                    edit(ProductField.SALT) { it.copy(salt = v) }
                }
            }

            Section("Штука")
            NumberInput(
                "Средний вес штуки", form.pieceWeight, errors[ProductField.PIECE_WEIGHT], "г", Modifier.fillMaxWidth(),
            ) { v -> edit(ProductField.PIECE_WEIGHT) { it.copy(pieceWeight = v) } }

            Section("Источник данных")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ProductSource.entries.forEach { source ->
                    FilterChip(
                        selected = form.source == source,
                        onClick = { edit("source") { it.copy(source = source) } },
                        label = { Text(source.label) },
                    )
                }
            }

            MicroBlock(form, errors, state.microExpanded, viewModel::toggleMicro) { code, v ->
                edit(ProductField.micro(code)) { it.copy(micro = it.micro + (code to v)) }
            }

            Section("Заметка")
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> edit("notes") { it.copy(notes = v) } },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Сохранить")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить продукт?") },
            text = { Text("Он пропадёт из списков и поиска. Прошлые приёмы пищи и блюда с ним сохранятся.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

/** Витамины и минералы: раскрывающийся блок, все поля необязательные. */
@Composable
private fun MicroBlock(
    form: ProductForm,
    errors: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (code: String, value: String) -> Unit,
) {
    val filled = form.micro.count { it.value.isNotBlank() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            "Витамины и минералы" + if (filled > 0) " ($filled)" else "",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    if (!expanded) return

    Text("На 100 г", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(Nutrients.vitamins, Nutrients.minerals).forEach { group ->
        group.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { nutrient -> MicroInput(nutrient, form, errors, onChange, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MicroInput(
    nutrient: Nutrient,
    form: ProductForm,
    errors: Map<String, String>,
    onChange: (String, String) -> Unit,
    modifier: Modifier,
) {
    NumberInput(
        label = nutrient.name,
        value = form.micro[nutrient.code].orEmpty(),
        error = errors[ProductField.micro(nutrient.code)],
        suffix = nutrient.unit.label,
        modifier = modifier,
    ) { onChange(nutrient.code, it) }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    error: String?,
    capitalize: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        trailingIcon = trailing,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { msg -> @Composable { Text(msg) } },
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            keyboardType = keyboard,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberInput(
    label: String,
    value: String,
    error: String?,
    suffix: String?,
    modifier: Modifier,
    integer: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        suffix = suffix?.let { s -> @Composable { Text(s) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { msg -> @Composable { Text(msg) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}
