package com.glucoplan.foodhealth.data.product

/** Источник данных о продукте (ТЗ 4.3). [code] хранится в базе. */
enum class ProductSource(val code: String, val label: String) {
    LABEL("label", "Этикетка"),
    REFERENCE("reference", "Справочник"),
    ESTIMATE("estimate", "Оценка"),
    MANUAL("manual", "Вручную");

    companion object {
        fun fromCode(code: String): ProductSource = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}

/** Продукт; все значения на 100 г. null — значение не указано. */
data class Product(
    val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double?,
    val sugar: Double?,
    val salt: Double?,
    val gi: Int?,
    val pieceWeightG: Double?,
    val source: ProductSource,
    val notes: String?,
    /** Код нутриента из [Nutrients] → значение на 100 г. */
    val micro: Map<String, Double>,
    /** Мягко удалён: не показывается в списках, но считается в блюдах и прошлых приёмах. */
    val deleted: Boolean = false,
)
