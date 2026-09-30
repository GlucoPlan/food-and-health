package com.glucoplan.foodhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.profile.HeightRecord
import com.glucoplan.foodhealth.data.profile.ProfileValidator
import com.glucoplan.foodhealth.ui.profile.DateField
import com.glucoplan.foodhealth.ui.profile.SexChips
import com.glucoplan.foodhealth.ui.profile.formatDate
import java.time.LocalDate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onDone: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val heightHistory by viewModel.heightHistory.collectAsStateWithLifecycle()
    val norms by viewModel.norms.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                // Строку состояния уже учёл внешний Scaffold
                windowInsets = WindowInsets(0),
                title = { Text(if (state.isNew) "Новый профиль" else "Профиль") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        // Отступы системных панелей уже учтены внешним Scaffold с нижней навигацией
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val form = state.form
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Имя") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { msg -> @Composable { Text(msg) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            SexChips(form.sex, state.sexError, viewModel::onSexChange)
            DateField(
                label = "Дата рождения",
                date = form.birthDate,
                error = state.birthDateError,
                onPick = viewModel::onBirthDateChange,
                extra = form.birthDate?.let { java.time.Period.between(it, LocalDate.now()).years }
                    ?.let(ProfileValidator::ageText),
            )
            HeightSection(
                isNew = state.isNew,
                history = heightHistory,
                onAdd = viewModel::addHeight,
                onDelete = viewModel::deleteHeight,
            )
            SwitchRow(
                title = "Дневник СД1",
                subtitle = "Сахар, доза и крупные углеводы при записи еды",
                checked = form.sd1Enabled,
                onChange = viewModel::onSd1Change,
            )
            if (form.sd1Enabled) {
                GlucoseRangeFields(
                    low = form.glucoseLow,
                    high = form.glucoseHigh,
                    error = state.glucoseError,
                    onLow = viewModel::onGlucoseLowChange,
                    onHigh = viewModel::onGlucoseHighChange,
                )
            }
            SwitchRow(
                title = "Показывать ХЕ",
                subtitle = null,
                checked = form.showXe,
                onChange = viewModel::onShowXeChange,
            )
            if (form.showXe) {
                OutlinedTextField(
                    value = form.carbsPerXe,
                    onValueChange = viewModel::onCarbsChange,
                    label = { Text("Граммов углеводов в 1 ХЕ") },
                    singleLine = true,
                    isError = state.carbsError != null,
                    supportingText = state.carbsError?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SwitchRow(
                title = "Считать воду",
                subtitle = "Дневная норма = норма на 1 кг × последний вес",
                checked = form.waterEnabled,
                onChange = viewModel::onWaterEnabledChange,
            )
            if (form.waterEnabled) {
                OutlinedTextField(
                    value = form.waterMlPerKg,
                    onValueChange = viewModel::onWaterNormChange,
                    label = { Text("Норма, мл на 1 кг веса") },
                    singleLine = true,
                    isError = state.waterError != null,
                    supportingText = state.waterError?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NormsSection(state, norms, viewModel)
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
        }
    }
}

/** Рост (ТЗ 15.3): текущий, «Записать рост», история с удалением ошибочных записей. */
@Composable
private fun HeightSection(
    isNew: Boolean,
    history: List<HeightRecord>,
    onAdd: (String, LocalDate, (String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Рост", style = MaterialTheme.typography.bodyLarge)
        if (isNew) {
            Text(
                "Рост можно записать после сохранения профиля",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val current = history.firstOrNull()
        Text(
            current?.let { "${NumberText.format(it.heightCm, 1)} см, с ${formatDate(it.date)}" } ?: "Не записан",
            color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(onClick = { adding = true }) { Text("Записать рост") }
        if (history.size > 1 || current != null) {
            Text(
                "История роста",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            history.forEach { record ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDate(record.date)} — ${NumberText.format(record.heightCm, 1)} см",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDelete(record.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить запись роста")
                    }
                }
            }
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Рост") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        label = { Text("Рост, см") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { msg -> @Composable { Text(msg) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    DateField(label = "Дата", date = date, error = null, onPick = { date = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAdd(text, date) { e -> if (e == null) adding = false else error = e }
                }) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
