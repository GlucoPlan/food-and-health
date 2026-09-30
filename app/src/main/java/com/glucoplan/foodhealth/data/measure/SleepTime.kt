package com.glucoplan.foodhealth.data.measure

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class SleepQuality(val code: String, val label: String) {
    BAD("bad", "Плохо"),
    NORMAL("normal", "Нормально"),
    GOOD("good", "Хорошо");

    companion object {
        fun fromCode(code: String?): SleepQuality? = entries.firstOrNull { it.code == code }
    }
}

/** Время сна (ТЗ 15.2): вводятся только часы и минуты, дата засыпания определяется сама. */
object SleepTime {
    const val MIN_MINUTES = 10L
    const val MAX_MINUTES = 20 * 60L
    val DEFAULT_ASLEEP: LocalTime = LocalTime.of(23, 0)

    /**
     * Моменты засыпания и пробуждения. Если заснул «позже» по часам, чем проснулся
     * (23:40 и 07:15), — значит, накануне; если раньше (00:30 и 07:15) — в тот же день.
     */
    fun moments(
        wakeDate: LocalDate,
        wakeTime: LocalTime,
        asleepTime: LocalTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        val asleepDate = if (asleepTime >= wakeTime) wakeDate.minusDays(1) else wakeDate
        val asleep = asleepDate.atTime(asleepTime).atZone(zone).toInstant().toEpochMilli()
        val woke = wakeDate.atTime(wakeTime).atZone(zone).toInstant().toEpochMilli()
        return asleep to woke
    }

    fun minutes(asleepAt: Long, wokeAt: Long): Long = (wokeAt - asleepAt) / 60_000

    /** «7 ч 35 мин», «45 мин», «8 ч». */
    fun durationText(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m мин"
            m == 0L -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    /** Время засыпания по умолчанию: как в прошлый раз, без записей — 23:00. */
    fun defaultAsleep(lastAsleepAt: Long?, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        lastAsleepAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().withSecond(0).withNano(0) }
            ?: DEFAULT_ASLEEP
}
