package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.db.SleepDao
import com.glucoplan.foodhealth.data.db.SleepEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SleepRecord(
    val id: String,
    val profileId: String,
    val asleepAt: Long,
    val wokeAt: Long,
    val quality: SleepQuality?,
    val source: String,
) {
    val minutes: Long get() = SleepTime.minutes(asleepAt, wokeAt)
}

/** Сон (ТЗ 15.2). */
@Singleton
class SleepRepository @Inject constructor(
    private val dao: SleepDao,
    private val devicePrefs: DevicePrefs,
) {
    fun observeRecent(profileId: String, limit: Int = WeightRepository.RECENT): Flow<List<SleepRecord>> =
        dao.observeRecent(profileId, limit).map { list -> list.map { it.toRecord() } }

    suspend fun get(id: String): SleepRecord? = dao.getById(id)?.takeIf { !it.deleted }?.toRecord()

    /** Новая запись ([id] = null) или исправление. */
    suspend fun save(
        id: String?,
        profileId: String,
        asleepAt: Long,
        wokeAt: Long,
        quality: SleepQuality?,
        now: Long = System.currentTimeMillis(),
    ): MeasureSave {
        val minutes = SleepTime.minutes(asleepAt, wokeAt)
        val errors = buildMap {
            if (minutes < SleepTime.MIN_MINUTES || minutes > SleepTime.MAX_MINUTES) {
                put(FIELD_DURATION, "Сон от 10 минут до 20 часов, получилось ${SleepTime.durationText(minutes)}")
            }
            MeasureInput.timeError(wokeAt, now)?.let { put(MeasureSave.TIME, "Пробуждение в будущем") }
        }
        if (errors.isNotEmpty()) return MeasureSave.Invalid(errors)

        val existing = id?.let { dao.getById(it) }
        dao.upsert(
            SleepEntity(
                id = id ?: UUID.randomUUID().toString(),
                profileId = existing?.profileId ?: profileId,
                asleepAt = asleepAt,
                wokeAt = wokeAt,
                quality = quality?.code,
                // Исправленная импортированная запись помнит, откуда пришла (15.5)
                source = existing?.source ?: SOURCE_MANUAL,
                externalId = existing?.externalId,
                updatedAt = now,
                deleted = false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return MeasureSave.Saved("Записано: сон ${SleepTime.durationText(minutes)}")
    }

    /** Мягкое удаление (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun SleepEntity.toRecord() =
        SleepRecord(id, profileId, asleepAt, wokeAt, SleepQuality.fromCode(quality), source)

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_HEALTH_CONNECT = "health_connect"
        const val FIELD_DURATION = "duration"
    }
}
