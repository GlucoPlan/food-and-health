package com.glucoplan.foodhealth.ui.measure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.WeightRecord
import com.glucoplan.foodhealth.ui.format.mealTime
import com.glucoplan.foodhealth.ui.meal.MealTimeDialogs

/** Экран «Замеры» (ТЗ 15.4). Новый замер записан — [onRecorded] с текстом сообщения. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasuresScreen(
    onBack: () -> Unit,
    onRecorded: (message: String) -> Unit,
    viewModel: MeasuresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weightDialog by viewModel.weightDialog.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()

    LaunchedEffect(recorded) {
        recorded?.let {
            viewModel.onRecordedHandled()
            onRecorded(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Замеры") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Кому
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                state.profiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == state.profileId,
                        onClick = { viewModel.onProfileSelected(profile.id) },
                        label = { Text(profile.name) },
                    )
                }
            }

            // Вес — первым: вводится чаще всего (15.4)
            WeightCard(state.weights, onNew = viewModel::newWeight, onEdit = viewModel::editWeight)
        }
    }

    weightDialog?.let { dialog ->
        WeightEntryDialog(
            state = dialog,
            last = state.weights.firstOrNull(),
            onSave = viewModel::saveWeight,
            onDelete = viewModel::deleteWeight,
            onDismiss = viewModel::dismissWeight,
        )
    }
}

@Composable
private fun WeightCard(weights: List<WeightRecord>, onNew: () -> Unit, onEdit: (WeightRecord) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNew).padding(16.dp),
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Вес", style = MaterialTheme.typography.titleMedium)
                Text(
                    weights.firstOrNull()?.let { weightLine(it) } ?: "Ещё не записывали",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("+", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (weights.isNotEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(vertical = 4.dp)) {
                weights.forEach { w ->
                    Text(
                        weightLine(w),
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(w) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun weightLine(w: WeightRecord) = "${NumberText.format(w.kg, 1)} кг · ${mealTime(w.measuredAt)}"

/** Ввод или правка веса: цифровая клавиатура сразу, подсказка с последним значением, время. */
@Composable
private fun WeightEntryDialog(
    state: WeightDialogState,
    last: WeightRecord?,
    onSave: (text: String, at: Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = state.record
    var text by remember(editing) { mutableStateOf(editing?.let { NumberText.format(it.kg, 1) } ?: "") }
    // null — «сейчас»
    var at by remember(editing) { mutableStateOf(editing?.measuredAt) }
    var choosingTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Вес" else "Исправить вес") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Вес, кг") },
                    placeholder = last?.takeIf { editing == null }?.let { l -> @Composable { Text(NumberText.format(l.kg, 1)) } },
                    singleLine = true,
                    isError = state.valueError != null,
                    supportingText = (state.valueError ?: last?.takeIf { editing == null }?.let { "Прошлый раз: ${weightLine(it)}" })
                        ?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text, at) }),
                    modifier = Modifier.focusRequester(focus),
                )
                // Внутри диалога: поле уже в композиции, клавиатура откроется сразу
                LaunchedEffect(Unit) { focus.requestFocus() }
                AssistChip(
                    onClick = { choosingTime = true },
                    label = { Text(at?.let { mealTime(it) } ?: "Сейчас") },
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                )
                state.timeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, at) }) { Text(if (editing == null) "Записать" else "Сохранить") } },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )

    if (choosingTime) {
        MealTimeDialogs(
            initial = at ?: System.currentTimeMillis(),
            onSelected = { at = it; choosingTime = false },
            onDismiss = { choosingTime = false },
        )
    }
}
