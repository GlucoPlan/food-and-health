package com.glucoplan.foodhealth.data.profile

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.db.HeightDao
import com.glucoplan.foodhealth.data.db.HeightEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Запись роста. */
data class HeightRecord(val id: String, val profileId: String, val date: LocalDate, val heightCm: Double)

/** История роста (ТЗ 15.3). */
@Singleton
class HeightRepository @Inject constructor(
    private val dao: HeightDao,
    private val devicePrefs: DevicePrefs,
) {
    /** История профиля, новые сверху; первая запись — текущий рост. */
    fun observeHistory(profileId: String): Flow<List<HeightRecord>> =
        dao.observeForProfile(profileId).map { list -> list.map { it.toRecord() } }

    /** Записать рост; null — записано, иначе текст ошибки. */
    suspend fun add(profileId: String, heightText: String, date: LocalDate, today: LocalDate = LocalDate.now()): String? {
        val cm = NumberText.parse(heightText)
        if (cm == null || cm < MIN_CM || cm > MAX_CM) return "Рост от ${MIN_CM.toInt()} до ${MAX_CM.toInt()} см"
        if (date.isAfter(today)) return "Дата не может быть в будущем"
        dao.upsert(
            HeightEntity(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                measuredAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                heightCm = cm,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return null
    }

    /** Мягкое удаление ошибочной записи (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun HeightEntity.toRecord() = HeightRecord(
        id = id,
        profileId = profileId,
        date = Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()).toLocalDate(),
        heightCm = heightCm,
    )

    companion object {
        const val MIN_CM = 30.0
        const val MAX_CM = 250.0
    }
}
