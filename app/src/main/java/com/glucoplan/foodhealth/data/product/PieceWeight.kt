package com.glucoplan.foodhealth.data.product

/** Ввод количества штук вместо граммов для продуктов со средним весом штуки (ТЗ 4.1). */
object PieceWeight {

    fun toGrams(pieces: Double, pieceWeightG: Double): Double = pieces * pieceWeightG

    /** Сколько штук в [grams]; null, если вес штуки не задан. */
    fun toPieces(grams: Double, pieceWeightG: Double?): Double? =
        pieceWeightG?.takeIf { it > 0 }?.let { grams / it }
}
