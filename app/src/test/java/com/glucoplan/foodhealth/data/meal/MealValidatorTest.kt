package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.testProduct
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MealValidatorTest {

    private val milk = testProduct("молоко", kcal = 60.0)
    private val now = 1_000_000_000L

    private fun resolve(vararg items: DraftItem) =
        MealCalculator.resolve(items.toList(), mapOf("молоко" to milk), emptyMap())

    @Test
    fun `нормальный приём без ошибок`() {
        assertThat(MealValidator.validate(resolve(DraftItem("a", MealItemType.PRODUCT, "молоко", "200")), null, now))
            .isEmpty()
    }

    @Test
    fun `пустой приём — ошибка`() {
        assertThat(MealValidator.validate(emptyList(), null, now)).containsKey(MealField.ITEMS)
    }

    @Test
    fun `вес больше нуля`() {
        val e = MealValidator.validate(
            resolve(
                DraftItem("a", MealItemType.PRODUCT, "молоко", "0"),
                DraftItem("b", MealItemType.PRODUCT, "молоко", "abc"),
            ),
            null, now,
        )
        assertThat(e).containsKey(MealField.item("a"))
        assertThat(e).containsKey(MealField.item("b"))
    }

    @Test
    fun `продукта нет в базе — ошибка`() {
        val e = MealValidator.validate(resolve(DraftItem("a", MealItemType.PRODUCT, "нет", "100")), null, now)
        assertThat(e).containsKey(MealField.item("a"))
    }

    @Test
    fun `прошлое время можно, будущее нельзя`() {
        val items = resolve(DraftItem("a", MealItemType.PRODUCT, "молоко", "200"))
        assertThat(MealValidator.validate(items, now - 3_600_000, now)).isEmpty()
        assertThat(MealValidator.validate(items, now + 30_000, now)).isEmpty()
        assertThat(MealValidator.validate(items, now + 3_600_000, now)).containsKey(MealField.TIME)
    }
}
