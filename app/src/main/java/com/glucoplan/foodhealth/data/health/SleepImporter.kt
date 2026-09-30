package com.glucoplan.foodhealth.data.health

import com.glucoplan.foodhealth.data.db.SleepDao
import com.glucoplan.foodhealth.data.measure.SleepRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Импорт сна из Health Connect владельцу телефона (ТЗ 15.5). */
@Singleton
class SleepImporter @Inject constructor(
    private val source: SleepSource,
    private val sleepDao: SleepDao,
    private val sleeps: SleepRepository,
    private val devicePrefs: DevicePrefs,
) {
    private val mutex = Mutex()
    private var lastRunAt = 0L

    fun available(): Boolean = source.available()

    suspend fun hasPermission(): Boolean = source.hasPermission()

    /**
     * Загрузить ночи за последние 14 дней. Возвращает число новых ночей;
     * null — Health Connect недоступен, нет разрешения или не выбран владелец телефона.
     */
    suspend fun run(now: Long = System.currentTimeMillis()): Int? = mutex.withLock {
        if (!source.available() || !source.hasPermission()) return null
        // Данные Health Connect на телефоне — его хозяина
        val owner = devicePrefs.ownerId.first() ?: return null
        val from = now - DAYS * DAY_MS
        val nights = SleepImport.nights(source.sessions(from, now))
        // С запасом на ночь, начавшуюся до окна
        val existing = sleepDao.since(owner, from - DAY_MS).map {
            ExistingSleep(it.externalId, it.asleepAt, it.wokeAt, it.deleted)
        }
        val fresh = SleepImport.toImport(nights, existing)
        fresh.forEach { sleeps.import(owner, it.asleepAt, it.wokeAt, it.externalId, now) }
        lastRunAt = now
        fresh.size
    }

    /** При запуске приложения — не чаще раза в час. */
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): Int? =
        if (now - lastRunAt < HOUR_MS) null else runCatching { run(now) }.getOrNull()

    private companion object {
        const val DAYS = 14
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
