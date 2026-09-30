package com.glucoplan.foodhealth.data.history

import com.glucoplan.foodhealth.data.meal.HistoryMeal
import com.glucoplan.foodhealth.data.measure.PressureRecord
import com.glucoplan.foodhealth.data.measure.SleepRecord
import com.glucoplan.foodhealth.data.measure.WaterRecord
import com.glucoplan.foodhealth.data.measure.WeightRecord
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

/** Лента «Истории»: еда и замеры по дням (ТЗ 15.4). */
class HistoryFeedTest {

    /** Местное время: [day] сентября 2026, [h]:[m]. */
    private fun at(day: Int, h: Int, m: Int = 0) =
        Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, day, h, m) }.timeInMillis

    private fun meal(id: String, time: Long, kcal: Double = 500.0) =
        HistoryMeal(id, time, emptyList(), Nutrition(kcal = kcal))

    private fun weight(id: String, time: Long, kg: Double) = WeightRecord(id, "me", time, kg)
    private fun water(id: String, time: Long, ml: Int) = WaterRecord(id, "me", time, ml)

    @Test
    fun `еда и замеры вперемешку по времени, новые сверху`() {
        val days = HistoryFeed.build(
            FeedData(
                meals = listOf(meal("обед", at(30, 12, 10)), meal("ужин", at(30, 19, 30))),
                weights = listOf(weight("w", at(30, 7, 40), 84.2)),
                pressures = listOf(PressureRecord("bp", "me", at(30, 12, 40), 120, 80, 72)),
                sleeps = listOf(SleepRecord("s", "me", at(29, 23, 40), at(30, 7, 15), null, "manual")),
            )
        )
        assertThat(days).hasSize(1)
        assertThat(days.single().entries.map { it.key })
            .containsExactly("meal:ужин", "pressure:bp", "meal:обед", "weight:w", "sleep:s").inOrder()
        assertThat(days.single().food!!.kcal).isEqualTo(1000.0)
    }

    @Test
    fun `сон через полночь — в дне пробуждения`() {
        val days = HistoryFeed.build(
            FeedData(sleeps = listOf(SleepRecord("s", "me", at(28, 23, 30), at(29, 6, 50), null, "manual")))
        )
        assertThat(days.single().dayStart).isEqualTo(at(29, 0))
    }

    @Test
    fun `день только с замерами — без итогов еды`() {
        val days = HistoryFeed.build(
            FeedData(meals = listOf(meal("m", at(30, 12))), weights = listOf(weight("w", at(29, 8), 84.5)))
        )
        assertThat(days.map { it.dayStart }).containsExactly(at(30, 0), at(29, 0)).inOrder()
        assertThat(days[1].food).isNull()
        assertThat(days[1].entries.map { it.key }).containsExactly("weight:w")
    }

    @Test
    fun `вода — сумма за день, норма по весу на тот день`() {
        val data = FeedData(
            weights = listOf(weight("old", at(28, 8), 80.0), weight("new", at(30, 8), 90.0)),
            waters = listOf(
                water("a", at(29, 10), 250), water("b", at(29, 15), 500),
                water("c", at(30, 9), 300),
            ),
            waterMlPerKg = 30.0,
        )
        val days = HistoryFeed.build(data).associateBy { it.dayStart }
        val d29 = days.getValue(at(29, 0)).water!!
        assertThat(d29.totalMl).isEqualTo(750)
        // Вес 29-го — ещё старый, 80 кг: вес от 30-го на прошлый день не влияет
        assertThat(d29.normMl).isEqualTo(2400)
        assertThat(d29.entries.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(days.getValue(at(30, 0)).water!!.normMl).isEqualTo(2700)
    }

    @Test
    fun `вода без веса или без «Считать воду» — без нормы`() {
        val noWeight = HistoryFeed.build(FeedData(waters = listOf(water("a", at(30, 10), 250)), waterMlPerKg = 30.0))
        assertThat(noWeight.single().water!!.normMl).isNull()
        val off = HistoryFeed.build(
            FeedData(weights = listOf(weight("w", at(30, 8), 84.0)), waters = listOf(water("a", at(30, 10), 250)))
        )
        assertThat(off.single().water!!.normMl).isNull()
        assertThat(off.single().water!!.totalMl).isEqualTo(250)
    }

    @Test
    fun `фильтр по дате — и для замеров`() {
        val data = FeedData(
            meals = listOf(meal("m", at(30, 12))),
            weights = listOf(weight("w", at(29, 8), 84.5)),
            waters = listOf(water("a", at(29, 10), 250)),
        )
        val days = HistoryFeed.build(data, day = at(29, 15))
        assertThat(days.single().dayStart).isEqualTo(at(29, 0))
        assertThat(days.single().water!!.totalMl).isEqualTo(250)
        assertThat(HistoryFeed.build(data, day = at(20, 12))).isEmpty()
    }
}
