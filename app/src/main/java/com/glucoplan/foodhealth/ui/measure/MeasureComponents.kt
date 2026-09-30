package com.glucoplan.foodhealth.ui.measure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.measure.MeasureSave
import com.glucoplan.foodhealth.ui.format.mealTime
import com.glucoplan.foodhealth.ui.meal.MealTimeDialogs

/** Строка списка последних записей замера. */
data class MeasureEntry(val id: String, val line: String)

/**
 * Карточка вида замера (ТЗ 15.4): название, последнее значение, «+» — новая запись,
 * под ней последние записи — нажатие открывает правку.
 */
@Composable
fun MeasureCard(
    title: String,
    icon: ImageVector,
    entries: List<MeasureEntry>,
    onNew: () -> Unit,
    onEdit: (id: String) -> Unit,
    /** Дополнительный блок под заголовком (например, «Из Health Connect» у сна). */
    extra: (@Composable () -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNew).padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    entries.firstOrNull()?.line ?: "Ещё не записывали",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("+", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        extra?.let { Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { it() } }
        if (entries.isNotEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(vertical = 4.dp)) {
                entries.forEach { e ->
                    Text(
                        e.line,
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(e.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Числовое поле замера. [key] — ключ ошибки из [MeasureSave.Invalid]. */
data class MeasureField(
    val key: String,
    val label: String,
    val integer: Boolean = false,
    val placeholder: String? = null,
)

/**
 * Ввод или правка замера с несколькими числовыми полями: клавиатура сразу на первом поле,
 * «Далее» — к следующему, «Готово» на последнем — записать. Время: «сейчас» или прошедшее.
 */
@Composable
fun MeasureEntryDialog(
    title: String,
    fields: List<MeasureField>,
    initial: List<String>,
    initialAt: Long?,
    editing: Boolean,
    hint: String?,
    errors: Map<String, String>,
    onSave: (values: List<String>, at: Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Сколько полей в ряд; по умолчанию все в одном ряду. */
    perRow: Int = fields.size,
) {
    val values = remember { mutableStateListOf(*initial.toTypedArray()) }
    // null — «сейчас»
    var at by remember { mutableStateOf(initialAt) }
    var choosingTime by remember { mutableStateOf(false) }
    val focus = remember { List(fields.size) { FocusRequester() } }
    val save = { onSave(values.toList(), at) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.indices.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        val field = fields[i]
                        val last = i == fields.lastIndex
                        OutlinedTextField(
                            value = values[i],
                            onValueChange = { values[i] = it },
                            label = { Text(field.label, maxLines = 1) },
                            placeholder = field.placeholder?.let { p -> @Composable { Text(p) } },
                            singleLine = true,
                            isError = errors[field.key] != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field.integer) KeyboardType.Number else KeyboardType.Decimal,
                                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focus[i + 1].requestFocus() },
                                onDone = { save() },
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focus[i]),
                        )
                    }
                    // Неполный последний ряд — пустые места, чтобы поля были одной ширины
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
                }
                // Внутри диалога: поля уже в композиции, клавиатура откроется сразу
                LaunchedEffect(Unit) { focus[0].requestFocus() }
                // Ошибки полей и общие (например, «заполните хотя бы одно»), кроме времени — оно ниже
                (fields.mapNotNull { errors[it.key] } +
                    errors.filterKeys { k -> k != MeasureSave.TIME && fields.none { it.key == k } }.values)
                    .distinct().forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (hint != null && !editing) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = { choosingTime = true },
                    label = { Text(at?.let { mealTime(it) } ?: "Сейчас") },
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                )
                errors[MeasureSave.TIME]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = save) { Text(if (editing) "Сохранить" else "Записать") } },
        dismissButton = {
            Row {
                if (editing) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
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
