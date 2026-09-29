package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.NumberText

/**
 * Дневник СД1 (ТЗ, раздел 7): ХЕ и необязательные сахар и доза.
 * Это просто числа для истории — никаких расчётов доз.
 */
object Sd1 {
    const val MIN_GLUCOSE = 1.0
    const val MAX_GLUCOSE = 35.0
    const val MIN_DOSE = 0.0
    const val MAX_DOSE = 50.0

    /** ХЕ = углеводы / граммов углеводов в 1 ХЕ; null, если граммы в ХЕ не заданы. */
    fun xe(carbs: Double, carbsPerXe: Double): Double? = carbsPerXe.takeIf { it > 0 }?.let { carbs / it }

    /** Пустые поля допустимы; ошибки — по ключам [MealField.GLUCOSE] и [MealField.DOSE]. */
    fun validate(glucose: String, dose: String): Map<String, String> = buildMap {
        if (glucose.isNotBlank()) {
            val v = NumberText.parse(glucose)
            if (v == null || v < MIN_GLUCOSE || v > MAX_GLUCOSE) {
                put(MealField.GLUCOSE, "От ${NumberText.format(MIN_GLUCOSE)} до ${NumberText.format(MAX_GLUCOSE)}")
            }
        }
        if (dose.isNotBlank()) {
            val v = NumberText.parse(dose)
            if (v == null || v < MIN_DOSE || v > MAX_DOSE) {
                put(MealField.DOSE, "От ${NumberText.format(MIN_DOSE)} до ${NumberText.format(MAX_DOSE)}")
            }
        }
    }

    /** Значение для базы: пусто — null. Вызывать после [validate]. */
    fun value(text: String): Double? = if (text.isBlank()) null else NumberText.parse(text)
}
