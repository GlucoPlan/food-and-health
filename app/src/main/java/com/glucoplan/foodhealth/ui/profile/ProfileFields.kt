package com.glucoplan.foodhealth.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.profile.ProfileValidator
import com.glucoplan.foodhealth.data.profile.Sex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

fun formatDate(date: LocalDate): String = DATE.format(date)

/** Пол (ТЗ 15.3): два чипа, ошибка под ними. */
@Composable
fun SexChips(selected: Sex?, error: String?, onSelect: (Sex) -> Unit) {
    Column {
        Text("Пол", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { sex ->
                FilterChip(selected = sex == selected, onClick = { onSelect(sex) }, label = { Text(sex.label) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Дата рождения: кнопка с датой, по нажатию — календарь (будущее недоступно).
 * DatePicker работает с полуночью UTC, поэтому дата переводится как календарная, без часовых поясов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    error: String?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    extra: String? = null,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Text(
                (date?.let(::formatDate) ?: "Выбрать") + (extra?.let { " · $it" } ?: ""),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (picking) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isAfter(today) && !d.isBefore(ProfileValidator.MIN_BIRTH_DATE)
                }

                override fun isSelectableYear(year: Int) = year in ProfileValidator.MIN_BIRTH_DATE.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state)
        }
    }
}
