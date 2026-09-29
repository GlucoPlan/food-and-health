package com.glucoplan.foodhealth.data.product

/** Товарные штрихкоды: EAN-13, EAN-8, UPC-A, UPC-E. */
object Barcodes {

    private val retailLengths = setOf(8, 12, 13)

    /** Код из сканера или поля ввода без пробелов; null, если пусто. */
    fun normalize(raw: String): String? = raw.filterNot { it.isWhitespace() }.ifEmpty { null }

    /** Похоже на товарный код: только цифры, длина 8, 12 или 13. */
    fun isRetail(code: String): Boolean = code.length in retailLengths && code.all { it in '0'..'9' }
}

/**
 * Защита от ошибочного считывания: код принимается, только когда он
 * распознан [required] кадров подряд.
 */
class ScanConfirmer(private val required: Int = 2) {
    private var last: String? = null
    private var count = 0

    /** Возвращает код, когда он подтверждён; иначе null. */
    fun offer(code: String): String? {
        if (code == last) count++ else { last = code; count = 1 }
        return code.takeIf { count >= required }
    }
}
