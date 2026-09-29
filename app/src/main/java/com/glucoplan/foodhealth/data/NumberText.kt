package com.glucoplan.foodhealth.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Числа в полях ввода: запятая или точка при вводе, запятая при показе. */
object NumberText {

    /** "12,5" / " 12.5 " → 12.5; пусто или не число → null. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** 10.0 → "10", 12.5 → "12,5", 1.2345 → "1,23" при [maxFraction] = 2. */
    fun format(value: Double, maxFraction: Int = 2): String =
        BigDecimal.valueOf(value)
            .setScale(maxFraction, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', ',')
            .let { if (it == "-0") "0" else it }
}
