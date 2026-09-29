package com.glucoplan.foodhealth.data.dish

import com.glucoplan.foodhealth.data.nutrition.Nutrition

data class DishIngredient(val id: String, val productId: String, val weightG: Double)

/** Варка блюда (ТЗ 5.4). */
data class DishVersion(
    val id: String,
    val dishId: String,
    val createdAt: Long,
    val panId: String?,
    val grossWeightG: Double?,
    val netWeightG: Double,
    val ingredients: List<DishIngredient>,
) {
    /** Вес не указан — считали по сумме сырых ингредиентов. */
    val byRawIngredients: Boolean get() = grossWeightG == null
}

data class Dish(val id: String, val name: String, val currentVersionId: String, val deleted: Boolean)

/** Строка списка блюд: КБЖУ на 100 г текущей варки и её дата. */
data class DishSummary(
    val id: String,
    val name: String,
    val currentVersionId: String,
    val lastCookedAt: Long,
    val per100: Nutrition,
    val byRawIngredients: Boolean,
)

/** Строка списка «Варки». */
data class VersionSummary(
    val id: String,
    val createdAt: Long,
    val netWeightG: Double,
    val byRawIngredients: Boolean,
    val panName: String?,
    val panDeleted: Boolean,
    val per100: Nutrition,
    val isCurrent: Boolean,
)

/** Варка для приёма пищи: чья она, когда сварена, КБЖУ на 100 г. */
data class VersionInfo(
    val versionId: String,
    val dishId: String,
    val dishName: String,
    val dishDeleted: Boolean,
    val createdAt: Long,
    val per100: Nutrition,
    val isCurrent: Boolean,
)
