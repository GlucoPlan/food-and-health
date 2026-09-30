package com.glucoplan.foodhealth.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.history.FeedDay
import com.glucoplan.foodhealth.data.history.FeedEntry
import com.glucoplan.foodhealth.data.measure.SleepRecord
import com.glucoplan.foodhealth.data.measure.SleepTime
import com.glucoplan.foodhealth.data.measure.WaterNorm
import com.glucoplan.foodhealth.ui.measure.MeasuresViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.foundation.layout.size
import com.glucoplan.foodhealth.data.meal.HistoryMeal
import com.glucoplan.foodhealth.data.meal.Sd1
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoryScreen(
    onOpenMeal: (mealId: String) -> Unit,
    /** Открыть замер на правку: вид («weight», «pressure», «sleep», «water»), id, чей. */
    onOpenMeasure: (kind: String, id: String, profileId: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingDay by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
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
        Row(Modifier.padding(horizontal = 16.dp)) {
            val day = state.day
            if (day == null) {
                AssistChip(
                    onClick = { choosingDay = true },
                    label = { Text("Все дни") },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                )
            } else {
                InputChip(
                    selected = true,
                    onClick = { viewModel.onDaySelected(null) },
                    label = { Text(dayTitle(day)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Показать все дни") },
                )
            }
        }

        when {
            !state.loaded -> Unit
            state.days.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    if (state.day == null) "Записей пока нет" else "В этот день записей нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                state.days.forEach { d ->
                    item(key = "day${d.dayStart}") {
                        DayHeader(
                            day = d,
                            waterExpanded = d.dayStart in state.waterExpanded,
                            onToggleWater = { viewModel.toggleWater(d.dayStart) },
                            onOpenWater = { id -> onOpenMeasure(MeasuresViewModel.KIND_WATER, id, state.profileId) },
                        )
                    }
                    items(d.entries, key = { it.key }) { entry ->
                        when (entry) {
                            is FeedEntry.Meal -> MealCard(entry.meal, state.sd1Profile, onClick = { onOpenMeal(entry.meal.id) })
                            is FeedEntry.Weight -> MeasureRow(
                                Icons.Filled.MonitorWeight, entry.time, "Вес ${NumberText.format(entry.record.kg, 1)} кг",
                            ) { onOpenMeasure(MeasuresViewModel.KIND_WEIGHT, entry.record.id, state.profileId) }
                            is FeedEntry.Pressure -> MeasureRow(
                                Icons.Filled.Favorite, entry.time, "Давление ${entry.record.text}",
                            ) { onOpenMeasure(MeasuresViewModel.KIND_PRESSURE, entry.record.id, state.profileId) }
                            is FeedEntry.Sleep -> MeasureRow(Icons.Filled.Bedtime, entry.time, sleepText(entry.record)) {
                                onOpenMeasure(MeasuresViewModel.KIND_SLEEP, entry.record.id, state.profileId)
                            }
                        }
                    }
                }
            }
        }
    }

    if (choosingDay) {
        DayDialog(onSelected = { viewModel.onDaySelected(it); choosingDay = false }, onDismiss = { choosingDay = false })
    }
}

/** Заголовок дня: дата, итоги КБЖУ по еде, вода одной строкой с раскрытием (15.4). */
@Composable
private fun DayHeader(day: FeedDay, waterExpanded: Boolean, onToggleWater: () -> Unit, onOpenWater: (String) -> Unit) {
    Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(dayTitle(day.dayStart), style = MaterialTheme.typography.titleMedium)
        day.food?.let {
            Text(nutritionLine(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        day.water?.let { w ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(onClick = onToggleWater).padding(vertical = 4.dp),
            ) {
                Icon(Icons.Filled.LocalDrink, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "Вода " + WaterNorm.litres(w.totalMl) + (w.normMl?.let { " из ${WaterNorm.litres(it)}" } ?: "") + " л",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    if (waterExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (waterExpanded) "Свернуть" else "Показать записи воды",
                    modifier = Modifier.size(18.dp),
                )
            }
            if (waterExpanded) {
                w.entries.forEach { e ->
                    Text(
                        "${e.ml} мл · ${HM.format(Date(e.drunkAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenWater(e.id) }.padding(start = 24.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** Замер в ленте дня — одна строка, отличная от карточек приёмов. */
@Composable
private fun MeasureRow(icon: ImageVector, time: Long, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(HM.format(Date(time)), style = MaterialTheme.typography.titleSmall)
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val HM = SimpleDateFormat("HH:mm", Locale.getDefault())

/** «Сон 23:40 → 07:15, 7 ч 35 мин · хорошо». */
private fun sleepText(s: SleepRecord): String =
    "Сон ${HM.format(Date(s.asleepAt))} → ${HM.format(Date(s.wokeAt))}, ${SleepTime.durationText(s.minutes)}" +
        (s.quality?.let { " · ${it.label.lowercase()}" } ?: "")

@Composable
private fun MealCard(meal: HistoryMeal, sd1: Profile?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meal.eatenAt)), style = MaterialTheme.typography.titleSmall)
            Text(
                meal.items.joinToString(", ") {
                    it.name + (if (it.deleted) " (удалён)" else "") + " " + NumberText.format(it.grams, 0) + " г"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                nutritionLine(meal.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sd1 != null) {
                Text(sd1Line(meal, sd1), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** «Сахар 6,5 · Доза 4 ед. · Углеводы 45 г · 3,8 ХЕ» (раздел 7); пустые значения пропускаются. */
private fun sd1Line(meal: HistoryMeal, profile: Profile): String = listOfNotNull(
    meal.glucose?.let { "Сахар ${NumberText.format(it, 1)}" },
    meal.insulinDose?.let { "Доза ${NumberText.format(it, 2)} ед." },
    "Углеводы ${NumberText.format(meal.total.carbs, 0)} г",
    Sd1.xe(meal.total.carbs, profile.carbsPerXe)?.takeIf { profile.showXe }?.let { "${NumberText.format(it, 1)} ХЕ" },
).joinToString(" · ")

/** «Сегодня», «Вчера», «27 сент.». */
private fun dayTitle(dayStart: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
    return when (dayStart) {
        today -> "Сегодня"
        yesterday -> "Вчера"
        else -> shortDate(dayStart)
    }
}

/** Выбор дня для фильтра: будущие дни недоступны. DatePicker работает с полуночью UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(onSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = Calendar.getInstance()
    val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = state.selectedDateMillis ?: todayUtc
                }
                val local = Calendar.getInstance().apply {
                    clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
                }
                onSelected(local.timeInMillis)
            }) { Text("Показать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = state)
    }
}
