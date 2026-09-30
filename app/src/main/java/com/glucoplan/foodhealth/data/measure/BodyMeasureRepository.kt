package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.db.BodyMeasureDao
import com.glucoplan.foodhealth.data.db.BodyMeasureEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Обхваты в порядке ТЗ 15.2. [code] — ключ ошибки поля. */
enum class BodyPart(val code: String, val label: String) {
    NECK("neck", "Шея"),
    CHEST("chest", "Грудь"),
    WAIST("waist", "Талия"),
    BELLY("belly", "Живот"),
    HIPS("hips", "Бёдра"),
    THIGH("thigh", "Бедро"),
    CALF("calf", "Голень"),
    ARM("arm", "Плечо"),
    WRIST("wrist", "Запястье"),
}

data class BodyRecord(val id: String, val profileId: String, val measuredAt: Long, val values: Map<BodyPart, Double>) {
    /** «талия 92, живот 98, бёдра 104» — в порядке ТЗ, только заполненные. */
    val text: String
        get() = BodyPart.entries.mapNotNull { p -> values[p]?.let { "${p.label.lowercase()} ${NumberText.format(it, 1)}" } }
            .joinToString(", ")
}

/** Обхваты тела (ТЗ 15.2). */
@Singleton
class BodyMeasureRepository @Inject constructor(
    private val dao: BodyMeasureDao,
    private val devicePrefs: DevicePrefs,
) {
    fun observeRecent(profileId: String, limit: Int = WeightRepository.RECENT): Flow<List<BodyRecord>> =
        dao.observeRecent(profileId, limit).map { list -> list.map { it.toRecord() } }

    /** Все записи профиля — для «Истории» (15.4). */
    fun observeAll(profileId: String): Flow<List<BodyRecord>> =
        dao.observeAll(profileId).map { list -> list.map { it.toRecord() } }

    suspend fun get(id: String): BodyRecord? = dao.getById(id)?.takeIf { !it.deleted }?.toRecord()

    /**
     * Новый замер ([id] = null) или исправление. [texts] — по одному на [BodyPart], в его порядке;
     * пустое поле — не мерили. Хотя бы одно должно быть заполнено.
     */
    suspend fun save(
        id: String?,
        profileId: String,
        texts: List<String>,
        at: Long?,
        now: Long = System.currentTimeMillis(),
    ): MeasureSave {
        val errors = mutableMapOf<String, String>()
        val values = mutableMapOf<BodyPart, Double>()
        BodyPart.entries.forEachIndexed { i, part ->
            val text = texts.getOrElse(i) { "" }
            if (text.isBlank()) return@forEachIndexed
            val cm = MeasureInput.number(text, MIN_CM, MAX_CM, fraction = 1)
            if (cm == null) errors[part.code] = "От ${MIN_CM.toInt()} до ${MAX_CM.toInt()} см" else values[part] = cm
        }
        if (values.isEmpty() && errors.isEmpty()) errors[FIELD_ALL] = "Заполните хотя бы один обхват"
        val measuredAt = at ?: now
        MeasureInput.timeError(measuredAt, now)?.let { errors[MeasureSave.TIME] = it }
        if (errors.isNotEmpty()) return MeasureSave.Invalid(errors)

        val existing = id?.let { dao.getById(it) }
        dao.upsert(
            BodyMeasureEntity(
                id = id ?: UUID.randomUUID().toString(),
                profileId = existing?.profileId ?: profileId,
                measuredAt = measuredAt,
                neck = values[BodyPart.NECK],
                chest = values[BodyPart.CHEST],
                waist = values[BodyPart.WAIST],
                belly = values[BodyPart.BELLY],
                hips = values[BodyPart.HIPS],
                thigh = values[BodyPart.THIGH],
                calf = values[BodyPart.CALF],
                arm = values[BodyPart.ARM],
                wrist = values[BodyPart.WRIST],
                updatedAt = now,
                deleted = false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return MeasureSave.Saved("Записано: обхваты (${values.size})")
    }

    /** Мягкое удаление (5.1). */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun BodyMeasureEntity.toRecord() = BodyRecord(
        id, profileId, measuredAt,
        listOfNotNull(
            neck?.let { BodyPart.NECK to it },
            chest?.let { BodyPart.CHEST to it },
            waist?.let { BodyPart.WAIST to it },
            belly?.let { BodyPart.BELLY to it },
            hips?.let { BodyPart.HIPS to it },
            thigh?.let { BodyPart.THIGH to it },
            calf?.let { BodyPart.CALF to it },
            arm?.let { BodyPart.ARM to it },
            wrist?.let { BodyPart.WRIST to it },
        ).toMap(),
    )

    companion object {
        const val MIN_CM = 5.0
        const val MAX_CM = 250.0
        const val FIELD_ALL = "all"
    }
}
