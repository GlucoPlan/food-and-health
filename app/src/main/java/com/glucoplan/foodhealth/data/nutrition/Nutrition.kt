package com.glucoplan.foodhealth.data.nutrition

import com.glucoplan.foodhealth.data.product.Product

/** КБЖУ и витамины с минералами (код нутриента → количество) для порции или на 100 г. */
data class Nutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val micro: Map<String, Double> = emptyMap(),
) {
    operator fun plus(other: Nutrition) = Nutrition(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carbs = carbs + other.carbs,
        micro = (micro.keys + other.micro.keys).associateWith { (micro[it] ?: 0.0) + (other.micro[it] ?: 0.0) },
    )

    fun scaled(factor: Double) = Nutrition(
        kcal = kcal * factor,
        protein = protein * factor,
        fat = fat * factor,
        carbs = carbs * factor,
        micro = micro.mapValues { it.value * factor },
    )

    companion object {
        val ZERO = Nutrition()

        fun per100(product: Product) =
            Nutrition(product.kcal, product.protein, product.fat, product.carbs, product.micro)

        /** ТЗ 5.3: значение на 100 г × вес / 100. */
        fun portion(product: Product, weightG: Double) = per100(product).scaled(weightG / 100.0)
    }
}
