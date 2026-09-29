package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.testProduct
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MealCalculatorTest {

    private val bread = testProduct("хлеб", kcal = 250.0, protein = 8.0, fat = 3.0, carbs = 48.0, pieceWeightG = 30.0)
    private val milk = testProduct("молоко", kcal = 60.0, protein = 3.0, fat = 3.2, carbs = 4.7)
    private val products = mapOf("хлеб" to bread, "молоко" to milk)
    private val soup = VersionInfo(
        versionId = "v1", dishId = "d", dishName = "Суп", dishDeleted = false, createdAt = 5L,
        per100 = Nutrition(kcal = 40.0, protein = 2.0, fat = 1.0, carbs = 6.0), isCurrent = true,
    )
    private val versions = mapOf("v1" to soup)

    private fun resolve(vararg items: DraftItem) = MealCalculator.resolve(items.toList(), products, versions)

    @Test
    fun `порция продукта по граммам`() {
        val r = resolve(DraftItem("a", MealItemType.PRODUCT, "молоко", "200")).single()
        assertThat(r.grams).isEqualTo(200.0)
        assertThat(r.portion!!.kcal).isWithin(1e-9).of(120.0)
        assertThat(r.portion!!.carbs).isWithin(1e-9).of(9.4)
    }

    @Test
    fun `порция продукта по штукам`() {
        val r = resolve(DraftItem("a", MealItemType.PRODUCT, "хлеб", "", pieces = "2")).single()
        assertThat(r.pieces).isEqualTo(2.0)
        assertThat(r.grams).isEqualTo(60.0)
        assertThat(r.portion!!.kcal).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `порция блюда — КБЖУ варки на 100 г × вес`() {
        val r = resolve(DraftItem("a", MealItemType.DISH, "v1", "350")).single()
        assertThat(r.name).isEqualTo("Суп")
        assertThat(r.dishId).isEqualTo("d")
        assertThat(r.cookedAt).isEqualTo(5L)
        assertThat(r.portion!!.kcal).isWithin(1e-9).of(140.0)
    }

    @Test
    fun `итоги приёма — сумма порций`() {
        val items = resolve(
            DraftItem("a", MealItemType.PRODUCT, "молоко", "200"),
            DraftItem("b", MealItemType.PRODUCT, "хлеб", "", pieces = "1,5"),
            DraftItem("c", MealItemType.DISH, "v1", "300"),
        )
        val total = MealCalculator.total(items)
        assertThat(total.kcal).isWithin(1e-9).of(120.0 + 112.5 + 120.0)
        assertThat(total.protein).isWithin(1e-9).of(6.0 + 3.6 + 6.0)
        assertThat(total.fat).isWithin(1e-9).of(6.4 + 1.35 + 3.0)
        assertThat(total.carbs).isWithin(1e-9).of(9.4 + 21.6 + 18.0)
    }

    @Test
    fun `позиция без веса не входит в итоги`() {
        val items = resolve(
            DraftItem("a", MealItemType.PRODUCT, "молоко", ""),
            DraftItem("b", MealItemType.PRODUCT, "молоко", "100"),
        )
        assertThat(items[0].grams).isNull()
        assertThat(items[0].portion).isNull()
        assertThat(MealCalculator.total(items).kcal).isWithin(1e-9).of(60.0)
    }

    @Test
    fun `неизвестный продукт или варка помечаются`() {
        val items = resolve(
            DraftItem("a", MealItemType.PRODUCT, "нет", "100"),
            DraftItem("b", MealItemType.DISH, "нет", "100"),
        )
        assertThat(items.map { it.missing }).containsExactly(true, true)
        assertThat(items.map { it.portion }).containsExactly(null, null)
    }

    @Test
    fun `удалённый продукт считается, но помечен`() {
        val r = MealCalculator.resolve(
            listOf(DraftItem("a", MealItemType.PRODUCT, "молоко", "100")),
            mapOf("молоко" to milk.copy(deleted = true)),
            emptyMap(),
        ).single()
        assertThat(r.deleted).isTrue()
        assertThat(r.portion!!.kcal).isWithin(1e-9).of(60.0)
    }
}
