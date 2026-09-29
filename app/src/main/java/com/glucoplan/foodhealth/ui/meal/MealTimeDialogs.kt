package com.glucoplan.foodhealth.ui.meal

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone

/**
 * Выбор времени приёма: сначала дата (будущие дни недоступны), потом часы и минуты.
 * DatePicker работает с полуночью UTC, поэтому дата переводится в местную вручную.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTimeDialogs(initial: Long, onSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val local = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }

    if (pickedDateUtc == null) {
        val todayUtc = utcMidnight(Calendar.getInstance())
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = utcMidnight(local),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDateUtc = dateState.selectedDateMillis ?: utcMidnight(local) }) {
                    Text("Далее")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = local.get(Calendar.HOUR_OF_DAY),
            initialMinute = local.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Время") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedDateUtc!! }
                    val result = Calendar.getInstance().apply {
                        clear()
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                            timeState.hour, timeState.minute)
                    }
                    onSelected(result.timeInMillis)
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
    }
}

/** Полночь UTC той же календарной даты, что и [local]. */
private fun utcMidnight(local: Calendar): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
