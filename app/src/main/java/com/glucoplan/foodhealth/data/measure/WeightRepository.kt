package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.db.WeightDao
import com.glucoplan.foodhealth.data.db.WeightEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class WeightRecord(val id: String, val profileId: String, val measuredAt: Long, val kg: Double)

/** Вес (ТЗ 15.2). */
@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightDao,
    private val devicePrefs: DevicePrefs,
) {
    fun observeRecent(profileId: String, limit: Int = RECENT): Flow<List<WeightRecord>> =
        dao.observeRecent(profileId, limit).map { list -> list.map { it.toRecord() } }

    /** Последний вес — для нормы воды (15.3). */
    suspend fun latest(profileId: String): WeightRecord? = dao.latest(profileId)?.toRecord()

    suspend fun get(id: String): WeightRecord? = dao.getById(id)?.takeIf { !it.deleted }?.toRecord()

    /**
     * Записать новый вес ([id] = null) или исправить существующий.
     * [at] — время замера; null — «сейчас».
     */
    suspend fun save(
        id: String?,
        profileId: String,
        text: String,
        at: Long?,
        now: Long = System.currentTimeMillis(),
    ): MeasureSave {
        val kg = MeasureInput.number(text, MIN_KG, MAX_KG, fraction = 1)
        val measuredAt = at ?: now
        val errors = buildMap {
            if (kg == null) put(FIELD_KG, "Вес от ${MIN_KG.toInt()} до ${MAX_KG.toInt()} кг")
            MeasureInput.timeError(measuredAt, now)?.let { put(MeasureSave.TIME, it) }
        }
        if (kg == null || errors.isNotEmpty()) return MeasureSave.Invalid(errors)

        val existing = id?.let { dao.getById(it) }
        dao.upsert(
            WeightEntity(
                id = id ?: UUID.randomUUID().toString(),
                profileId = existing?.profileId ?: profileId,
                measuredAt = measuredAt,
                weightKg = kg,
                updatedAt = now,
                deleted = false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return MeasureSave.Saved("Записано: ${NumberText.format(kg, 1)} кг")
    }

    /** Мягкое удаление (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun WeightEntity.toRecord() = WeightRecord(id, profileId, measuredAt, weightKg)

    companion object {
        const val MIN_KG = 2.0
        const val MAX_KG = 300.0
        const val RECENT = 5
        const val FIELD_KG = "kg"
    }
}
