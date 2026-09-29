package com.glucoplan.foodhealth.data.dish

import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.testProduct
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DishCalculatorTest {

    private val buckwheat = testProduct("гречка", kcal = 313.0, protein = 12.6, fat = 3.3, carbs = 62.0, micro = mapOf("fe" to 6.7))
    private val butter = testProduct("масло", kcal = 748.0, protein = 0.5, fat = 82.5, carbs = 0.8)
    private val water = testProduct("вода", kcal = 0.0)

    private fun ok(n: NetWeight) = n as NetWeight.Ok

    @Test
    fun `порция продукта — значение на 100 г × вес на 100`() {
        val n = Nutrition.portion(buckwheat, 50.0)
        assertThat(n.kcal).isWithin(1e-9).of(156.5)
        assertThat(n.carbs).isWithin(1e-9).of(31.0)
        assertThat(n.micro["fe"]).isWithin(1e-9).of(3.35)
    }

    @Test
    fun `вес нетто — вес с кастрюлей минус кастрюля`() {
        val n = ok(DishCalculator.netWeight(grossWeightG = 2300.0, panWeightG = 800.0, rawWeightG = 700.0))
        assertThat(n.grams).isEqualTo(1500.0)
        assertThat(n.byRawIngredients).isFalse()
    }

    @Test
    fun `без кастрюли вес — это вес самого блюда`() {
        assertThat(ok(DishCalculator.netWeight(1500.0, null, 700.0)).grams).isEqualTo(1500.0)
    }

    @Test
    fun `вес не указан — по сумме сырых ингредиентов, даже если кастрюля выбрана`() {
        val n = ok(DishCalculator.netWeight(null, 800.0, 700.0))
        assertThat(n.grams).isEqualTo(700.0)
        assertThat(n.byRawIngredients).isTrue()
    }

    @Test
    fun `вес не больше кастрюли — ошибка`() {
        assertThat(DishCalculator.netWeight(800.0, 800.0, 700.0)).isInstanceOf(NetWeight.Error::class.java)
        assertThat(DishCalculator.netWeight(500.0, 800.0, 700.0)).isInstanceOf(NetWeight.Error::class.java)
    }

    @Test
    fun `пустой состав без веса — ошибка`() {
        assertThat(DishCalculator.netWeight(null, null, 0.0)).isInstanceOf(NetWeight.Error::class.java)
    }

    @Test
    fun `на 100 г — сумма по ингредиентам делённая на нетто`() {
        // Гречка 200 г + масло 20 г + вода 400 г, после варки нетто 550 г
        val ingredients = listOf(buckwheat to 200.0, butter to 20.0, water to 400.0)
        val per100 = DishCalculator.per100(ingredients, 550.0)
        val kcalTotal = 313.0 * 2 + 748.0 * 0.2
        assertThat(per100.kcal).isWithin(1e-9).of(kcalTotal / 550.0 * 100)
        assertThat(per100.fat).isWithin(1e-9).of((3.3 * 2 + 82.5 * 0.2) / 550.0 * 100)
        assertThat(per100.micro["fe"]).isWithin(1e-9).of(6.7 * 2 / 550.0 * 100)
    }

    @Test
    fun `варка считается от текущих продуктов, удалённые тоже считаются, отсутствующие пропускаются`() {
        val version = DishVersion(
            id = "v", dishId = "d", createdAt = 0, panId = null, grossWeightG = null, netWeightG = 300.0,
            ingredients = listOf(
                DishIngredient("i1", "гречка", 100.0),
                DishIngredient("i2", "масло", 100.0),
                DishIngredient("i3", "неизвестно", 100.0),
            ),
        )
        val products = mapOf("гречка" to buckwheat, "масло" to butter.copy(deleted = true))
        val per100 = DishCalculator.per100(version, products)
        assertThat(per100.kcal).isWithin(1e-9).of((313.0 + 748.0) / 300.0 * 100)
    }

    @Test
    fun `дополненный продукт пересчитывает блюдо`() {
        val version = DishVersion("v", "d", 0, null, null, 100.0, listOf(DishIngredient("i", "гречка", 100.0)))
        val before = DishCalculator.per100(version, mapOf("гречка" to buckwheat))
        val after = DishCalculator.per100(version, mapOf("гречка" to buckwheat.copy(micro = mapOf("fe" to 6.7, "mg" to 200.0))))
        assertThat(before.micro).doesNotContainKey("mg")
        assertThat(after.micro["mg"]).isWithin(1e-9).of(200.0)
    }
}
