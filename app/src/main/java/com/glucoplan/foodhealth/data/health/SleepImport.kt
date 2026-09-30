package com.glucoplan.foodhealth.data.health

/** Сессия сна из Health Connect. */
data class SleepSession(val id: String, val start: Long, val end: Long)

/** Ночь для импорта; [externalId] — id первой сессии ночи, по нему повторный импорт пропускается. */
data class ImportNight(val externalId: String, val asleepAt: Long, val wokeAt: Long)

/** Уже записанный сон человека — для пропуска повторов и пересечений. */
data class ExistingSleep(val externalId: String?, val asleepAt: Long, val wokeAt: Long, val deleted: Boolean)

/** Правила импорта сна из Health Connect (ТЗ 15.5). */
object SleepImport {
    private const val MINUTE = 60_000L

    /** Сессии с перерывом меньше часа — одна ночь (Mi Fitness делит ночь из-за пробуждений). */
    const val MERGE_GAP_MS = 60 * MINUTE

    /** Короче часа — дневной сон или ошибка браслета; дневной сон не нужен (15.2). */
    const val MIN_NIGHT_MS = 60 * MINUTE

    /** Не длиннее 20 часов — как при ручном вводе. */
    const val MAX_NIGHT_MS = 20 * 60 * MINUTE

    /** Склейка сессий в ночи и отбор по длительности. */
    fun nights(sessions: List<SleepSession>): List<ImportNight> {
        val result = mutableListOf<ImportNight>()
        var current: ImportNight? = null
        for (s in sessions.filter { it.end > it.start }.sortedBy { it.start }) {
            val c = current
            current = if (c != null && s.start - c.wokeAt < MERGE_GAP_MS) {
                c.copy(wokeAt = maxOf(c.wokeAt, s.end))
            } else {
                c?.let(result::add)
                ImportNight(s.id, s.start, s.end)
            }
        }
        current?.let(result::add)
        return result.filter { it.wokeAt - it.asleepAt in MIN_NIGHT_MS..MAX_NIGHT_MS }
    }

    /**
     * Что импортировать: без повторов (уже импортированную, в том числе исправленную или удалённую,
     * не трогаем) и без двойных ночей (если на это время уже есть сон — например, записанный руками).
     */
    fun toImport(nights: List<ImportNight>, existing: List<ExistingSleep>): List<ImportNight> {
        val known = existing.mapNotNull { it.externalId }.toSet()
        val active = existing.filter { !it.deleted }
        return nights.filter { n ->
            n.externalId !in known && active.none { it.asleepAt < n.wokeAt && n.asleepAt < it.wokeAt }
        }
    }
}
