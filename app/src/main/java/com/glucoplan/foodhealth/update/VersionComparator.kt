package com.glucoplan.foodhealth.update

/** Сравнение версий вида MAJOR.MINOR.PATCH, в том числе тегов релизов «v1.2.3». */
object VersionComparator {

    /**
     * true, если [candidate] строго новее [current].
     * Если одну из версий не удалось разобрать, считаем, что обновления нет.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v0.1.10" → [0, 1, 10]; null, если строка не версия. */
    fun parse(version: String): List<Int>? {
        val s = version.trim().removePrefix("v").removePrefix("V")
        if (s.isEmpty()) return null
        return s.split('.').map { part ->
            part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
    }
}
