package com.glucoplan.foodhealth.data.product

import com.glucoplan.foodhealth.data.NumberText

/** То, что введено в карточке продукта: числа строками, как в полях. */
data class ProductForm(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val gi: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val salt: String = "",
    val pieceWeight: String = "",
    val source: ProductSource = ProductSource.MANUAL,
    val notes: String = "",
    /** Код нутриента → введённый текст. */
    val micro: Map<String, String> = emptyMap(),
) {
    companion object {
        fun from(p: Product) = ProductForm(
            name = p.name,
            brand = p.brand.orEmpty(),
            barcode = p.barcode.orEmpty(),
            kcal = NumberText.format(p.kcal),
            protein = NumberText.format(p.protein),
            fat = NumberText.format(p.fat),
            carbs = NumberText.format(p.carbs),
            gi = p.gi?.toString().orEmpty(),
            fiber = p.fiber?.let(NumberText::format).orEmpty(),
            sugar = p.sugar?.let(NumberText::format).orEmpty(),
            salt = p.salt?.let(NumberText::format).orEmpty(),
            pieceWeight = p.pieceWeightG?.let(NumberText::format).orEmpty(),
            source = p.source,
            notes = p.notes.orEmpty(),
            micro = p.micro.mapValues { NumberText.format(it.value, maxFraction = 3) },
        )
    }
}

/** Ключи ошибок: имена полей формы, «macros» для суммы БЖУ, «micro:<код>» для нутриентов. */
object ProductField {
    const val NAME = "name"
    const val BARCODE = "barcode"
    const val KCAL = "kcal"
    const val PROTEIN = "protein"
    const val FAT = "fat"
    const val CARBS = "carbs"
    const val GI = "gi"
    const val FIBER = "fiber"
    const val SUGAR = "sugar"
    const val SALT = "salt"
    const val PIECE_WEIGHT = "pieceWeight"
    const val MACROS = "macros"
    fun micro(code: String) = "micro:$code"
}

sealed interface ProductValidation {
    data class Valid(val product: Product) : ProductValidation
    data class Invalid(val errors: Map<String, String>) : ProductValidation
}

object ProductValidator {
    const val MAX_KCAL = 900.0
    const val MAX_GRAMS = 100.0
    const val MAX_GI = 100

    /** Проверка формы без обращения к базе; уникальность штрихкода проверяет репозиторий. */
    fun validate(id: String, form: ProductForm): ProductValidation {
        val errors = mutableMapOf<String, String>()

        val name = form.name.trim()
        if (name.isEmpty()) errors[ProductField.NAME] = "Введите название"

        val barcode = form.barcode.trim()
        if (barcode.isNotEmpty() && !barcode.all { it in '0'..'9' }) {
            errors[ProductField.BARCODE] = "Только цифры"
        }

        fun required(field: String, text: String, max: Double): Double? = when (val v = NumberText.parse(text)) {
            null -> { errors[field] = if (text.isBlank()) "Обязательное поле" else "Введите число"; null }
            else -> if (v < 0 || v > max) { errors[field] = "От 0 до ${NumberText.format(max)}"; null } else v
        }

        fun optional(field: String, text: String, max: Double): Double? {
            if (text.isBlank()) return null
            val v = NumberText.parse(text)
            return when {
                v == null -> { errors[field] = "Введите число"; null }
                v < 0 || v > max -> { errors[field] = "От 0 до ${NumberText.format(max)}"; null }
                else -> v
            }
        }

        val kcal = required(ProductField.KCAL, form.kcal, MAX_KCAL)
        val protein = required(ProductField.PROTEIN, form.protein, MAX_GRAMS)
        val fat = required(ProductField.FAT, form.fat, MAX_GRAMS)
        val carbs = required(ProductField.CARBS, form.carbs, MAX_GRAMS)
        if (protein != null && fat != null && carbs != null && protein + fat + carbs > MAX_GRAMS + 1e-9) {
            errors[ProductField.MACROS] = "Белков, жиров и углеводов вместе больше 100 г на 100 г"
        }

        var gi: Int? = null
        if (form.gi.isNotBlank()) {
            gi = form.gi.trim().toIntOrNull()?.takeIf { it in 0..MAX_GI }
            if (gi == null) errors[ProductField.GI] = "Целое число от 0 до $MAX_GI"
        }

        val fiber = optional(ProductField.FIBER, form.fiber, MAX_GRAMS)
        val sugar = optional(ProductField.SUGAR, form.sugar, MAX_GRAMS)
        val salt = optional(ProductField.SALT, form.salt, MAX_GRAMS)

        var pieceWeight: Double? = null
        if (form.pieceWeight.isNotBlank()) {
            pieceWeight = NumberText.parse(form.pieceWeight)?.takeIf { it > 0 }
            if (pieceWeight == null) errors[ProductField.PIECE_WEIGHT] = "Число больше 0"
        }

        val micro = mutableMapOf<String, Double>()
        form.micro.forEach { (code, text) ->
            if (text.isBlank()) return@forEach
            val v = NumberText.parse(text)
            if (v == null || v < 0) errors[ProductField.micro(code)] = "Число не меньше 0" else micro[code] = v
        }

        if (errors.isNotEmpty()) return ProductValidation.Invalid(errors)

        return ProductValidation.Valid(
            Product(
                id = id,
                name = name,
                brand = form.brand.trim().ifEmpty { null },
                barcode = barcode.ifEmpty { null },
                kcal = kcal!!,
                protein = protein!!,
                fat = fat!!,
                carbs = carbs!!,
                fiber = fiber,
                sugar = sugar,
                salt = salt,
                gi = gi,
                pieceWeightG = pieceWeight,
                source = form.source,
                notes = form.notes.trim().ifEmpty { null },
                micro = micro,
            )
        )
    }
}
