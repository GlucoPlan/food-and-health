package com.glucoplan.foodhealth.data.pan

import com.glucoplan.foodhealth.data.db.PanDao
import com.glucoplan.foodhealth.data.db.PanEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanRepository @Inject constructor(
    private val dao: PanDao,
    private val devicePrefs: DevicePrefs,
) {
    /** Кастрюли для выбора, без удалённых. */
    fun observePans(): Flow<List<Pan>> = dao.observeActive().map { list -> list.map { it.toPan() } }

    /** Все кастрюли, в том числе удалённые: на них ссылаются сохранённые варки. */
    fun observeAllIncludingDeleted(): Flow<List<Pan>> = dao.observeAll().map { list -> list.map { it.toPan() } }

    suspend fun get(id: String): Pan? = dao.getById(id)?.toPan()

    /** Создаёт ([id] = null) или сохраняет кастрюлю. Возвращает id или ошибки. */
    suspend fun save(id: String?, form: PanForm): Pair<String?, PanValidation> {
        val result = PanValidator.validate(form)
        if (result !is PanValidation.Valid) return null to result
        val panId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { dao.getById(it) }
        dao.upsert(
            PanEntity(
                id = panId,
                name = result.name,
                weightG = result.weightG,
                photo = result.photo,
                updatedAt = System.currentTimeMillis(),
                deleted = existing?.deleted ?: false,
                deviceId = devicePrefs.deviceId(),
            )
        )
        return panId to result
    }

    /** Мягкое удаление (ТЗ 5.5): пропадает из выбора, сохранённые варки не меняются. */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun PanEntity.toPan() = Pan(id, name, weightG, photo, deleted)
}
