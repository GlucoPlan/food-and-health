package com.glucoplan.foodhealth.data

import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.data.product.ProductSource

/** Продукт для тестов: КБЖУ на 100 г. */
fun testProduct(
    id: String,
    kcal: Double,
    protein: Double = 0.0,
    fat: Double = 0.0,
    carbs: Double = 0.0,
    micro: Map<String, Double> = emptyMap(),
    pieceWeightG: Double? = null,
    deleted: Boolean = false,
) = Product(
    id = id, name = id, brand = null, barcode = null, kcal = kcal, protein = protein, fat = fat, carbs = carbs,
    fiber = null, sugar = null, salt = null, gi = null, pieceWeightG = pieceWeightG, source = ProductSource.MANUAL,
    notes = null, micro = micro, deleted = deleted,
)
