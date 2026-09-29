package com.glucoplan.foodhealth.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Настройки, которые принадлежат только этому телефону и не синхронизируются. */
@Singleton
class DevicePrefs @Inject constructor(
    private val prefs: DataStore<Preferences>,
) {
    /** Владелец телефона (ТЗ 2): профиль, выбранный при первом запуске. */
    val ownerId: Flow<String?> = prefs.data.map { it[KEY_OWNER] }

    suspend fun setOwner(profileId: String) {
        prefs.edit { it[KEY_OWNER] = profileId }
    }

    /** Идентификатор телефона для поля device_id (ТЗ 5.1). Создаётся при первом обращении. */
    suspend fun deviceId(): String {
        prefs.data.first()[KEY_DEVICE_ID]?.let { return it }
        var id = ""
        prefs.edit { p ->
            id = p[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { p[KEY_DEVICE_ID] = it }
        }
        return id
    }

    private companion object {
        val KEY_OWNER = stringPreferencesKey("owner_profile_id")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
