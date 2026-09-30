package com.glucoplan.foodhealth.ui.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.measure.MeasureSave
import com.glucoplan.foodhealth.data.measure.SleepQuality
import com.glucoplan.foodhealth.data.measure.SleepRepository
import com.glucoplan.foodhealth.data.measure.SleepTime
import com.glucoplan.foodhealth.ui.profile.DateField
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Начальные значения диалога сна. */
data class SleepDialogState(
    val editingId: String? = null,
    val wakeDate: LocalDate,
    val wakeTime: LocalTime,
    val asleepTime: LocalTime,
    val quality: SleepQuality? = null,
    val errors: Map<String, String> = emptyMap(),
)

/**
 * Сон (ТЗ 15.2): время засыпания и пробуждения (дата засыпания — сама), дата пробуждения
 * для прошлых ночей, длительность сразу, необязательная оценка.
 */
@Composable
fun SleepDialog(
    state: SleepDialogState,
    onSave: (wakeDate: LocalDate, wakeTime: LocalTime, asleepTime: LocalTime, quality: SleepQuality?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = state.editingId != null
    var wakeDate by remember { mutableStateOf(state.wakeDate) }
    var wakeTime by remember { mutableStateOf(state.wakeTime) }
    var asleepTime by remember { mutableStateOf(state.asleepTime) }
    var quality by remember { mutableStateOf(state.quality) }
    var picking by remember { mutableStateOf<String?>(null) }

    val (asleepAt, wokeAt) = SleepTime.moments(wakeDate, wakeTime, asleepTime)
    val minutes = SleepTime.minutes(asleepAt, wokeAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Исправить сон" else "Сон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { picking = ASLEEP },
                        label = { Text("Заснул ${HM.format(asleepTime)}") },
                        leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { picking = WOKE },
                        label = { Text("Проснулся ${HM.format(wakeTime)}") },
                        leadingIcon = { Icon(Icons.Filled.WbSunny, contentDescription = null) },
                    )
                }
                Text(SleepTime.durationText(minutes), style = MaterialTheme.typography.titleMedium)
                state.errors[SleepRepository.FIELD_DURATION]?.let { ErrorLine(it) }
                DateField(label = "Дата пробуждения", date = wakeDate, error = state.errors[MeasureSave.TIME], onPick = { wakeDate = it })
                Text("Как спалось", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SleepQuality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            // Повторное нажатие снимает оценку — она необязательна
                            onClick = { quality = if (quality == q) null else q },
                            label = { Text(q.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(wakeDate, wakeTime, asleepTime, quality) }) {
                Text(if (editing) "Сохранить" else "Записать")
            }
        },
        dismissButton = {
            Row {
                if (editing) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )

    picking?.let { which ->
        TimeDialog(
            title = if (which == ASLEEP) "Заснул" else "Проснулся",
            initial = if (which == ASLEEP) asleepTime else wakeTime,
            onPick = { if (which == ASLEEP) asleepTime = it else wakeTime = it; picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ErrorLine(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private const val ASLEEP = "asleep"
private const val WOKE = "woke"
