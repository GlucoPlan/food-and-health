package com.glucoplan.foodhealth.data.dish

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DishValidatorTest {

    private val soup = DishForm(
        name = " Суп ",
        ingredients = listOf(IngredientDraft("a", "картофель", "300"), IngredientDraft("b", "вода", "1000,5")),
    )

    @Test
    fun `без веса — по сумме сырых ингредиентов`() {
        val v = DishValidator.validate(soup, panWeightG = null) as DishValidation.Valid
        assertThat(v.name).isEqualTo("Суп")
        assertThat(v.grossWeightG).isNull()
        assertThat(v.netWeightG).isEqualTo(1300.5)
        assertThat(v.ingredients.map { it.second }).containsExactly(300.0, 1000.5).inOrder()
    }

    @Test
    fun `с кастрюлей вычитается её вес`() {
        val v = DishValidator.validate(soup.copy(panId = "p", grossWeight = "2000"), panWeightG = 800.0) as DishValidation.Valid
        assertThat(v.netWeightG).isEqualTo(1200.0)
        assertThat(v.panId).isEqualTo("p")
    }

    @Test
    fun `пустые название и состав — ошибки`() {
        val e = (DishValidator.validate(DishForm(), null) as DishValidation.Invalid).errors
        assertThat(e).containsKey(DishField.NAME)
        assertThat(e).containsKey(DishField.INGREDIENTS)
    }

    @Test
    fun `вес ингредиента больше нуля`() {
        val form = soup.copy(ingredients = listOf(IngredientDraft("a", "x", "0"), IngredientDraft("b", "y", "abc")))
        val e = (DishValidator.validate(form, null) as DishValidation.Invalid).errors
        assertThat(e).containsKey(DishField.ingredient("a"))
        assertThat(e).containsKey(DishField.ingredient("b"))
    }

    @Test
    fun `вес меньше кастрюли — ошибка веса`() {
        val e = (DishValidator.validate(soup.copy(panId = "p", grossWeight = "700"), 800.0) as DishValidation.Invalid).errors
        assertThat(e).containsKey(DishField.GROSS)
    }

    @Test
    fun `нечисловой вес — ошибка`() {
        val e = (DishValidator.validate(soup.copy(grossWeight = "много"), null) as DishValidation.Invalid).errors
        assertThat(e).containsKey(DishField.GROSS)
    }
}
