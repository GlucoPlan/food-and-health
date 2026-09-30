package com.glucoplan.foodhealth.data.history

import com.glucoplan.foodhealth.data.measure.PressureRecord
import com.glucoplan.foodhealth.data.measure.SleepRecord
import com.glucoplan.foodhealth.data.measure.WaterNorm
import com.glucoplan.foodhealth.data.measure.WaterRecord
import com.glucoplan.foodhealth.data.measure.WeightRecord
import com.glucoplan.foodhealth.data.meal.HistoryDays
import com.glucoplan.foodhealth.data.meal.HistoryMeal
import com.glucoplan.foodhealth.data.nutrition.Nutrition

/** Строка ленты дня: приём пищи или замер (ТЗ 15.4). [time] — по нему сортировка. */
sealed interface FeedEntry {
    val time: Long
    val key: String

    data class Meal(val meal: HistoryMeal) : FeedEntry {
        override val time get() = meal.eatenAt
        override val key get() = "meal:${meal.id}"
    }

    data class Weight(val record: WeightRecord) : FeedEntry {
        override val time get() = record.measuredAt
        override val key get() = "weight:${record.id}"
    }

    data class Pressure(val record: PressureRecord) : FeedEntry {
        override val time get() = record.measuredAt
        override val key get() = "pressure:${record.id}"
    }

    /** Сон стоит по времени пробуждения, в дне пробуждения. */
    data class Sleep(val record: SleepRecord) : FeedEntry {
        override val time get() = record.wokeAt
        override val key get() = "sleep:${record.id}"
    }
}

/** Вода за день — одна строка (15.4). [normMl] — по весу на тот день; null — веса ещё не было. */
data class WaterDay(val totalMl: Int, val normMl: Int?, val entries: List<WaterRecord>)

/** День ленты: [food] — итоги КБЖУ по еде, null — в этот день только замеры. */
data class FeedDay(
    val dayStart: Long,
    val entries: List<FeedEntry>,
    val food: Nutrition?,
    val water: WaterDay?,
)

/** Всё, из чего собирается лента одного человека. */
data class FeedData(
    val meals: List<HistoryMeal> = emptyList(),
    val weights: List<WeightRecord> = emptyList(),
    val pressures: List<PressureRecord> = emptyList(),
    val sleeps: List<SleepRecord> = emptyList(),
    val waters: List<WaterRecord> = emptyList(),
    /** Норма воды профиля, мл на 1 кг; null — вода у профиля не считается. */
    val waterMlPerKg: Double? = null,
)

/** Лента «Истории» (ТЗ 4.2, 15.4): еда и замеры по дням, новые сверху. */
object HistoryFeed {

    /** [day] — показать только этот день (любой момент внутри него). */
    fun build(data: FeedData, day: Long? = null): List<FeedDay> {
        val entries = data.meals.map { FeedEntry.Meal(it) } +
            data.weights.map { FeedEntry.Weight(it) } +
            data.pressures.map { FeedEntry.Pressure(it) } +
            data.sleeps.map { FeedEntry.Sleep(it) }
        val byDay = entries.groupBy { HistoryDays.dayStart(it.time) }
        val waterByDay = data.waters.groupBy { HistoryDays.dayStart(it.drunkAt) }
        val filterStart = day?.let(HistoryDays::dayStart)

        return (byDay.keys + waterByDay.keys)
            .filter { filterStart == null || it == filterStart }
            .sortedDescending()
            .map { start ->
                val dayEntries = byDay[start].orEmpty().sortedByDescending { it.time }
                val meals = dayEntries.filterIsInstance<FeedEntry.Meal>()
                FeedDay(
                    dayStart = start,
                    entries = dayEntries,
                    food = if (meals.isEmpty()) null else meals.fold(Nutrition.ZERO) { acc, m -> acc + m.meal.total },
                    water = waterByDay[start]?.let { list -> waterDay(list, start, data) },
                )
            }
    }

    private fun waterDay(list: List<WaterRecord>, dayStart: Long, data: FeedData): WaterDay {
        val dayEnd = HistoryDays.dayStart(dayStart + 36 * HOUR)
        // Норма — по весу, записанному на этот день или раньше: прошлые дни показывают норму, какой она была
        val weight = data.weights.filter { it.measuredAt < dayEnd }.maxByOrNull { it.measuredAt }
        return WaterDay(
            totalMl = list.sumOf { it.ml },
            normMl = data.waterMlPerKg?.let { WaterNorm.dailyNormMl(it, weight?.kg) },
            entries = list.sortedByDescending { it.drunkAt },
        )
    }

    private const val HOUR = 3_600_000L
}
