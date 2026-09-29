package com.glucoplan.foodhealth.data.dish

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.product.Product

sealed interface NetWeight {
    data class Ok(val grams: Double, val byRawIngredients: Boolean) : NetWeight
    data class Error(val message: String) : NetWeight
}

/** Расчёт блюда по ТЗ 5.3. */
object DishCalculator {

    /**
     * Вес нетто: вес с кастрюлей − вес кастрюли; без кастрюли — сам вес;
     * вес не указан — сумма сырых ингредиентов [rawWeightG].
     */
    fun netWeight(grossWeightG: Double?, panWeightG: Double?, rawWeightG: Double): NetWeight = when {
        grossWeightG == null ->
            if (rawWeightG > 0) NetWeight.Ok(rawWeightG, byRawIngredients = true)
            else NetWeight.Error("Добавьте продукты в состав")
        grossWeightG <= 0 -> NetWeight.Error("Вес должен быть больше 0")
        panWeightG == null -> NetWeight.Ok(grossWeightG, byRawIngredients = false)
        grossWeightG <= panWeightG ->
            NetWeight.Error("Вес с кастрюлей должен быть больше веса кастрюли (${NumberText.format(panWeightG, 0)} г)")
        else -> NetWeight.Ok(grossWeightG - panWeightG, byRawIngredients = false)
    }

    /** Сумма КБЖУ ингредиентов (продукт и его вес в граммах). */
    fun total(ingredients: List<Pair<Product, Double>>): Nutrition =
        ingredients.fold(Nutrition.ZERO) { acc, (product, weight) -> acc + Nutrition.portion(product, weight) }

    /** На 100 г готового блюда: сумма по ингредиентам / вес нетто × 100. */
    fun per100(ingredients: List<Pair<Product, Double>>, netWeightG: Double): Nutrition =
        total(ingredients).scaled(100.0 / netWeightG)

    /**
     * На 100 г сохранённой варки. Продукты берутся текущие (ТЗ 5.3: дополненный продукт
     * пересчитывает блюдо), удалённые тоже считаются. Продукт, которого нет в базе, пропускается.
     */
    fun per100(version: DishVersion, products: Map<String, Product>): Nutrition =
        per100(version.ingredients.mapNotNull { i -> products[i.productId]?.let { it to i.weightG } }, version.netWeightG)
}
