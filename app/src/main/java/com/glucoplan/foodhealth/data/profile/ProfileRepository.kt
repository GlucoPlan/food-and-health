package com.glucoplan.foodhealth.data.profile

import com.glucoplan.foodhealth.data.db.ProfileDao
import com.glucoplan.foodhealth.data.db.ProfileEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val devicePrefs: DevicePrefs,
) {
    /** Профили без удалённых, по имени. */
    fun observeProfiles(): Flow<List<Profile>> =
        dao.observeActive().map { list -> list.map { it.toProfile() } }

    suspend fun get(id: String): Profile? = dao.getById(id)?.takeIf { !it.deleted }?.toProfile()

    /**
     * Создаёт профиль ([id] = null) или сохраняет изменения.
     * Если форма заполнена с ошибками, ничего не пишет и возвращает Invalid.
     */
    suspend fun save(id: String?, form: ProfileForm, today: LocalDate = LocalDate.now()): ProfileValidation {
        val existing = id?.let { dao.getById(it) }
        val others = dao.getActive().filter { it.id != id }.map { it.name }
        val result = ProfileValidator.validate(
            form,
            others,
            currentCarbs = existing?.carbsPerXe ?: ProfileValidator.DEFAULT_CARBS_PER_XE,
            currentWater = existing?.waterMlPerKg ?: ProfileValidator.DEFAULT_WATER_ML_PER_KG,
            today = today,
        )
        if (result is ProfileValidation.Valid) {
            dao.upsert(
                ProfileEntity(
                    id = id ?: UUID.randomUUID().toString(),
                    name = result.name,
                    sd1Enabled = form.sd1Enabled,
                    showXe = form.showXe,
                    carbsPerXe = result.carbsPerXe,
                    updatedAt = System.currentTimeMillis(),
                    deleted = existing?.deleted ?: false,
                    deviceId = devicePrefs.deviceId(),
                    sex = form.sex?.code,
                    birthDate = form.birthDate?.toString(),
                    waterEnabled = form.waterEnabled,
                    waterMlPerKg = result.waterMlPerKg,
                )
            )
        }
        return result
    }

    private fun ProfileEntity.toProfile() = Profile(
        id = id,
        name = name,
        sd1Enabled = sd1Enabled,
        showXe = showXe,
        carbsPerXe = carbsPerXe,
        sex = Sex.fromCode(sex),
        birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        waterEnabled = waterEnabled,
        waterMlPerKg = waterMlPerKg,
    )
}
