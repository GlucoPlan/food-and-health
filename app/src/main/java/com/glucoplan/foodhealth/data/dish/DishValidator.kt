package com.glucoplan.foodhealth.data.dish

import com.glucoplan.foodhealth.data.NumberText

/** Ингредиент в редакторе. [key] — id сохранённого ингредиента или новый UUID. */
data class IngredientDraft(val key: String, val productId: String, val weight: String)

data class DishForm(
    val name: String = "",
    val panId: String? = null,
    val grossWeight: String = "",
    val ingredients: List<IngredientDraft> = emptyList(),
)

/** Ключи ошибок в [DishValidation.Invalid.errors]. */
object DishField {
    const val NAME = "name"
    const val INGREDIENTS = "ingredients"
    const val GROSS = "gross"
    fun ingredient(key: String) = "ingredient:$key"
}

sealed interface DishValidation {
    data class Valid(
        val name: String,
        val panId: String?,
        val grossWeightG: Double?,
        val netWeightG: Double,
        val ingredients: List<Pair<IngredientDraft, Double>>,
    ) : DishValidation

    data class Invalid(val errors: Map<String, String>) : DishValidation
}

object DishValidator {
    const val MAX_WEIGHT_G = 50_000.0

    /** [panWeightG] — вес выбранной кастрюли или null без кастрюли. */
    fun validate(form: DishForm, panWeightG: Double?): DishValidation {
        val errors = mutableMapOf<String, String>()

        val name = form.name.trim()
        if (name.isEmpty()) errors[DishField.NAME] = "Введите название"

        if (form.ingredients.isEmpty()) errors[DishField.INGREDIENTS] = "Добавьте продукты в состав"
        val weights = form.ingredients.mapNotNull { draft ->
            val w = NumberText.parse(draft.weight)
            if (w == null || w <= 0 || w > MAX_WEIGHT_G) {
                errors[DishField.ingredient(draft.key)] = "Вес больше 0"
                null
            } else draft to w
        }

        var gross: Double? = null
        if (form.grossWeight.isNotBlank()) {
            gross = NumberText.parse(form.grossWeight)?.takeIf { it > 0 && it <= MAX_WEIGHT_G }
            if (gross == null) errors[DishField.GROSS] = "Число больше 0"
        }

        var net = 0.0
        if (errors.isEmpty()) {
            when (val result = DishCalculator.netWeight(gross, panWeightG, weights.sumOf { it.second })) {
                is NetWeight.Ok -> net = result.grams
                is NetWeight.Error -> errors[DishField.GROSS] = result.message
            }
        }

        return if (errors.isEmpty()) {
            DishValidation.Valid(name, form.panId, gross, net, weights)
        } else {
            DishValidation.Invalid(errors)
        }
    }
}
