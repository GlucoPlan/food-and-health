package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.testProduct
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class HistoryDaysTest {

    private val milk = testProduct("молоко", kcal = 60.0, protein = 3.0, fat = 3.2, carbs = 4.7)
    private val products = mapOf("молоко" to milk)
    private val soup = VersionInfo("v", "d", "Суп", false, 1L, Nutrition(40.0, 2.0, 1.0, 6.0), true)
    private val versions = mapOf("v" to soup)

    /** Местное время: [day] сентября 2026, [hour]:[minute]. */
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, day, hour, minute) }.timeInMillis

    private fun meal(id: String, eatenAt: Long, deleted: Boolean = false) =
        MealEntity(id, "p", eatenAt, null, null, null, 1L, deleted, "dev")

    private fun item(
        id: String, mealId: String, type: String, ref: String, weight: Double,
        snapshotKcal: Double = 999.0, deleted: Boolean = false,
    ) = MealItemEntity(
        id, mealId, type, ref.takeIf { type == "product" }, ref.takeIf { type == "dish" }, weight, null,
        snapshotKcal, 0.0, 0.0, 0.0, 1L, deleted, "dev",
    )

    @Test
    fun `КБЖУ позиции по текущим данным, а не по снимку`() {
        val n = HistoryDays.itemNutrition(item("i", "m", "product", "молоко", 200.0), products, versions)
        assertThat(n.kcal).isWithin(1e-9).of(120.0)
        val dish = HistoryDays.itemNutrition(item("i", "m", "dish", "v", 300.0), products, versions)
        assertThat(dish.kcal).isWithin(1e-9).of(120.0)
    }

    @Test
    fun `продукта нет в базе — берётся снимок`() {
        val n = HistoryDays.itemNutrition(item("i", "m", "product", "нет", 200.0, snapshotKcal = 77.0), products, versions)
        assertThat(n.kcal).isEqualTo(77.0)
    }

    @Test
    fun `удалённые приёмы и позиции не показываются`() {
        val meals = HistoryDays.meals(
            meals = listOf(meal("a", at(28, 9)), meal("b", at(28, 13), deleted = true)),
            items = listOf(
                item("1", "a", "product", "молоко", 100.0),
                item("2", "a", "product", "молоко", 100.0, deleted = true),
                item("3", "b", "product", "молоко", 100.0),
            ),
            products = products,
            versions = versions,
        )
        assertThat(meals.map { it.id }).containsExactly("a")
        assertThat(meals.single().items).hasSize(1)
        assertThat(meals.single().total.kcal).isWithin(1e-9).of(60.0)
    }

    @Test
    fun `по дням, новые сверху, итоги дня — сумма приёмов`() {
        val meals = HistoryDays.meals(
            meals = listOf(meal("m1", at(27, 23, 50)), meal("m2", at(28, 0, 10)), meal("m3", at(28, 19))),
            items = listOf(
                item("1", "m1", "product", "молоко", 100.0),
                item("2", "m2", "product", "молоко", 200.0),
                item("3", "m3", "dish", "v", 250.0),
            ),
            products = products,
            versions = versions,
        )
        val days = HistoryDays.group(meals)
        assertThat(days.map { it.dayStart }).containsExactly(at(28, 0), at(27, 0)).inOrder()
        assertThat(days[0].meals.map { it.id }).containsExactly("m3", "m2").inOrder()
        assertThat(days[0].total.kcal).isWithin(1e-9).of(120.0 + 100.0)
        assertThat(days[1].total.kcal).isWithin(1e-9).of(60.0)
    }

    @Test
    fun `фильтр по дате оставляет один день`() {
        val meals = HistoryDays.meals(
            meals = listOf(meal("m1", at(27, 12)), meal("m2", at(28, 12))),
            items = emptyList(),
            products = products,
            versions = versions,
        )
        val days = HistoryDays.group(meals, day = at(27, 15, 30))
        assertThat(days.single().meals.single().id).isEqualTo("m1")
        assertThat(HistoryDays.group(meals, day = at(20, 12))).isEmpty()
    }
}
