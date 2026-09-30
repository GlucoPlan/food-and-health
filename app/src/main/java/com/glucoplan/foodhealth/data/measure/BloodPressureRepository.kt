package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.db.BloodPressureDao
import com.glucoplan.foodhealth.data.db.BloodPressureEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PressureRecord(
    val id: String,
    val profileId: String,
    val measuredAt: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
) {
    /** «120/80, пульс 72» или «120/80». */
    val text: String get() = "$systolic/$diastolic" + (pulse?.let { ", пульс $it" } ?: "")
}

/** Давление и пульс (ТЗ 15.2). */
@Singleton
class BloodPressureRepository @Inject constructor(
    private val dao: BloodPressureDao,
    private val devicePrefs: DevicePrefs,
) {
    fun observeRecent(profileId: String, limit: Int = WeightRepository.RECENT): Flow<List<PressureRecord>> =
        dao.observeRecent(profileId, limit).map { list -> list.map { it.toRecord() } }

    suspend fun get(id: String): PressureRecord? = dao.getById(id)?.takeIf { !it.deleted }?.toRecord()

    /** Новое измерение ([id] = null) или исправление; [at] null — «сейчас»; пустой пульс — не указан. */
    suspend fun save(
        id: String?,
        profileId: String,
        systolicText: String,
        diastolicText: String,
        pulseText: String,
        at: Long?,
        now: Long = System.currentTimeMillis(),
    ): MeasureSave {
        val systolic = MeasureInput.integer(systolicText, SYS_MIN, SYS_MAX)
        val diastolic = MeasureInput.integer(diastolicText, DIA_MIN, DIA_MAX)
        val pulse = if (pulseText.isBlank()) null else MeasureInput.integer(pulseText, PULSE_MIN, PULSE_MAX)
        val measuredAt = at ?: now
        val errors = buildMap {
            if (systolic == null) put(FIELD_SYSTOLIC, "От $SYS_MIN до $SYS_MAX")
            if (diastolic == null) put(FIELD_DIASTOLIC, "От $DIA_MIN до $DIA_MAX")
            if (systolic != null && diastolic != null && systolic <= diastolic) {
                put(FIELD_DIASTOLIC, "Нижнее должно быть меньше верхнего")
            }
            if (pulseText.isNotBlank() && pulse == null) put(FIELD_PULSE, "От $PULSE_MIN до $PULSE_MAX")
            MeasureInput.timeError(measuredAt, now)?.let { put(MeasureSave.TIME, it) }
        }
        if (errors.isNotEmpty() || systolic == null || diastolic == null) return MeasureSave.Invalid(errors)

        val existing = id?.let { dao.getById(it) }
        val record = BloodPressureEntity(
            id = id ?: UUID.randomUUID().toString(),
            profileId = existing?.profileId ?: profileId,
            measuredAt = measuredAt,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            updatedAt = now,
            deleted = false,
            deviceId = devicePrefs.deviceId(),
        )
        dao.upsert(record)
        return MeasureSave.Saved("Записано: ${record.toRecord().text}")
    }

    /** Мягкое удаление (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun BloodPressureEntity.toRecord() = PressureRecord(id, profileId, measuredAt, systolic, diastolic, pulse)

    companion object {
        const val SYS_MIN = 50
        const val SYS_MAX = 260
        const val DIA_MIN = 30
        const val DIA_MAX = 160
        const val PULSE_MIN = 30
        const val PULSE_MAX = 220
        const val FIELD_SYSTOLIC = "systolic"
        const val FIELD_DIASTOLIC = "diastolic"
        const val FIELD_PULSE = "pulse"
    }
}
