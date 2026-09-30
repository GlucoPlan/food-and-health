package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.db.WaterDao
import com.glucoplan.foodhealth.data.db.WaterEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class WaterRecord(val id: String, val profileId: String, val drunkAt: Long, val ml: Int)

/** Вода (ТЗ 15.2–15.4). */
@Singleton
class WaterRepository @Inject constructor(
    private val dao: WaterDao,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val devicePrefs: DevicePrefs,
) {
    /** Записи за сегодня (по местному времени), новые сверху. */
    fun observeToday(profileId: String, now: Long = System.currentTimeMillis()): Flow<List<WaterRecord>> {
        val (from, to) = WaterNorm.dayBounds(now)
        return dao.observeBetween(profileId, from, to).map { list -> list.map { it.toRecord() } }
    }

    /** Объём для следующей записи: последний введённый, без записей — 250 мл. */
    fun observeLastVolume(profileId: String): Flow<Int> =
        dao.observeLatest(profileId).map { it?.ml ?: WaterNorm.DEFAULT_ML }

    suspend fun get(id: String): WaterRecord? = dao.getById(id)?.takeIf { !it.deleted }?.toRecord()

    /** Записать новую воду ([id] = null) или исправить; [at] null — «сейчас». */
    suspend fun save(
        id: String?,
        profileId: String,
        mlText: String,
        at: Long?,
        now: Long = System.currentTimeMillis(),
    ): MeasureSave {
        val ml = MeasureInput.integer(mlText, MIN_ML, MAX_ML)
        val drunkAt = at ?: now
        val errors = buildMap {
            if (ml == null) put(FIELD_ML, "От $MIN_ML до $MAX_ML мл")
            MeasureInput.timeError(drunkAt, now)?.let { put(MeasureSave.TIME, it) }
        }
        if (ml == null || errors.isNotEmpty()) return MeasureSave.Invalid(errors)

        val existing = id?.let { dao.getById(it) }
        val owner = existing?.profileId ?: profileId
        dao.upsert(
            WaterEntity(
                id = id ?: UUID.randomUUID().toString(),
                profileId = owner,
                drunkAt = drunkAt,
                ml = ml,
                updatedAt = now,
                deleted = false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return MeasureSave.Saved("Записано: вода $ml мл · ${todaySummary(owner, now)}")
    }

    /** «сегодня 1,45 из 2,5 л» с нормой по профилю и последнему весу. */
    suspend fun todaySummary(profileId: String, now: Long = System.currentTimeMillis()): String {
        val (from, to) = WaterNorm.dayBounds(now)
        val norm = profiles.get(profileId)?.let { p -> WaterNorm.dailyNormMl(p.waterMlPerKg, weights.latest(profileId)?.kg) }
        return WaterNorm.today(dao.sumBetween(profileId, from, to), norm)
    }

    /** Мягкое удаление (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun WaterEntity.toRecord() = WaterRecord(id, profileId, drunkAt, ml)

    companion object {
        const val MIN_ML = 1
        const val MAX_ML = 3000
        const val FIELD_ML = "ml"
    }
}
