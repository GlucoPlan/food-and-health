package com.glucoplan.foodhealth.ui.dishes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.dish.DishField
import com.glucoplan.foodhealth.data.dish.DishSaveMode
import com.glucoplan.foodhealth.data.dish.NetWeight
import com.glucoplan.foodhealth.data.dish.VersionSummary
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.pan.Pan
import com.glucoplan.foodhealth.ui.format.grams
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate
import com.glucoplan.foodhealth.ui.pans.PanThumbnail
import com.glucoplan.foodhealth.ui.picker.WeightDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditScreen(
    onDone: () -> Unit,
    onAddProduct: () -> Unit,
    pickedProductId: String?,
    onPickedHandled: () -> Unit,
    viewModel: DishEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingPan by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(pickedProductId) {
        if (pickedProductId != null) {
            viewModel.onProductPicked(pickedProductId)
            onPickedHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            DishSaveMode.NEW -> "Новое блюдо"
                            DishSaveMode.EDIT -> "Блюдо"
                            DishSaveMode.RECOOK -> "Новая варка"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.mode == DishSaveMode.EDIT) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val form = state.form
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.mode == DishSaveMode.RECOOK) {
                Text(
                    "Сохранится новая варка. Прошлые варки и записанные с ними приёмы пищи не изменятся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Название") },
                singleLine = true,
                isError = state.errors[DishField.NAME] != null,
                supportingText = state.errors[DishField.NAME]?.let { msg -> @Composable { Text(msg) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            val locked = state.compositionLocked
            if (locked) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        "Эта варка уже записана в приёмах пищи. Чтобы изменить состав или вес, " +
                            "нажмите «Сварил заново». Название можно менять.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Section("Состав")
            state.errors[DishField.INGREDIENTS]?.let { ErrorText(it) }
            state.rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name + if (row.productDeleted) " (удалён)" else "")
                        row.portionKcal?.let {
                            Text(
                                "${NumberText.format(it, 0)} ккал",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = row.weight,
                        onValueChange = { viewModel.onWeightChange(row.key, it) },
                        suffix = { Text("г") },
                        singleLine = true,
                        readOnly = locked,
                        isError = row.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                    if (!locked) {
                        IconButton(onClick = { viewModel.onRemoveIngredient(row.key) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Убрать ${row.name}")
                        }
                    }
                }
            }
            if (!locked) OutlinedButton(onClick = onAddProduct) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Добавить продукт", modifier = Modifier.padding(start = 8.dp))
            }

            Section("Вес готового блюда")
            PanChoice(state.selectedPan, onClick = { if (!locked) choosingPan = true })
            OutlinedTextField(
                value = form.grossWeight,
                onValueChange = viewModel::onGrossChange,
                readOnly = locked,
                label = { Text(if (state.selectedPan != null) "Вес вместе с кастрюлей" else "Вес готового блюда") },
                suffix = { Text("г") },
                singleLine = true,
                isError = state.errors[DishField.GROSS] != null,
                supportingText = {
                    Text(state.errors[DishField.GROSS] ?: "Пусто — расчёт по сумме сырых ингредиентов")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Summary(state.net, state.per100)

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Сохранить")
            }
            if (state.mode == DishSaveMode.EDIT) {
                OutlinedButton(onClick = viewModel::recook, modifier = Modifier.fillMaxWidth()) {
                    Text("Сварил заново")
                }
            }

            if (state.versions.isNotEmpty()) {
                Section("Варки")
                state.versions.forEach { VersionRow(it) }
            }
        }
    }

    state.pendingProduct?.let { product ->
        WeightDialog(
            product,
            onConfirm = { grams, _ -> viewModel.onWeightConfirmed(grams) },
            onDismiss = viewModel::onWeightDismissed,
        )
    }

    if (choosingPan) {
        PanDialog(
            pans = state.pans,
            selectedId = state.form.panId,
            onSelect = { viewModel.onPanSelected(it); choosingPan = false },
            onDismiss = { choosingPan = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить блюдо?") },
            text = { Text("Оно пропадёт из списков и поиска. Приёмы пищи, записанные раньше, не изменятся.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PanChoice(pan: Pan?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        PanThumbnail(pan?.photo)
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    pan == null -> "Без кастрюли"
                    pan.deleted -> "${pan.name} (удалена)"
                    else -> pan.name
                }
            )
            Text(
                pan?.let { grams(it.weightG) } ?: "Нажмите, чтобы выбрать кастрюлю",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PanDialog(pans: List<Pan>, selectedId: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кастрюля") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PanOption(null, selectedId == null) { onSelect(null) }
                pans.forEach { pan -> PanOption(pan, pan.id == selectedId) { onSelect(pan.id) } }
                if (pans.isEmpty()) {
                    Text(
                        "Кастрюли добавляются кнопкой «Кастрюли» на вкладке «Блюда» или в настройках.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun PanOption(pan: Pan?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (pan != null) PanThumbnail(pan.photo, size = 40.dp)
        Column {
            Text(pan?.name ?: "Без кастрюли")
            pan?.let {
                Text(grams(it.weightG), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Summary(net: NetWeight?, per100: Nutrition?) {
    if (net == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (net) {
                is NetWeight.Error -> ErrorText(net.message)
                is NetWeight.Ok -> {
                    Text(
                        if (net.byRawIngredients) "По сырым ингредиентам: ${grams(net.grams)}"
                        else "Вес нетто: ${grams(net.grams)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    per100?.let { Text("На 100 г: ${nutritionLine(it)}") }
                    if (net.byRawIngredients) {
                        Text(
                            "Вес готового блюда не указан: при варке вода уходит или впитывается, " +
                                "поэтому цифры приблизительные.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(v: VersionSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            shortDate(v.createdAt) + if (v.isCurrent) " · текущая" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            (if (v.byRawIngredients) "по сырым ${grams(v.netWeightG)}" else "нетто ${grams(v.netWeightG)}") +
                (v.panName?.let { " · $it" + if (v.panDeleted) " (удалена)" else "" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            nutritionLine(v.per100) + " на 100 г",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
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
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
