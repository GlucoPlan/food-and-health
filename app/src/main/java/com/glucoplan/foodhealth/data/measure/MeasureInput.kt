package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.NumberText
import java.math.BigDecimal
import java.math.RoundingMode

/** Разбор и проверка ввода замеров (ТЗ 15.2): границы — от опечаток, а не медицинские. */
object MeasureInput {

    /** Допуск на расхождение часов при проверке «не в будущем». */
    private const val CLOCK_SLACK_MS = 60_000L

    /** Число в границах, округлённое до [fraction] знаков; null — не число или вне границ. */
    fun number(text: String, min: Double, max: Double, fraction: Int): Double? {
        val v = NumberText.parse(text) ?: return null
        val rounded = BigDecimal.valueOf(v).setScale(fraction, RoundingMode.HALF_UP).toDouble()
        return rounded.takeIf { it in min..max }
    }

    /** Ошибка времени замера или null. */
    fun timeError(at: Long, now: Long): String? = if (at > now + CLOCK_SLACK_MS) "Время в будущем" else null
}

/** Результат сохранения замера. */
sealed interface MeasureSave {
    /** [message] — для сообщения «Записано: …». */
    data class Saved(val message: String) : MeasureSave
    data class Invalid(val valueError: String?, val timeError: String?) : MeasureSave
}
