package com.glucoplan.foodhealth.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Адрес сервера и ключ семьи (ТЗ 4.6). В APK и репозиторий не попадают (ТЗ 8.5) — вводятся на телефоне. */
data class ServerConfig(val url: String, val key: String) {
    companion object {
        const val MIN_KEY_LENGTH = 16

        /** Проверка ввода: адрес только https://, без хвостового «/»; ключ не короче 16 символов. */
        fun parse(url: String, key: String): Result<ServerConfig> {
            val u = url.trim().trimEnd('/')
            val host = runCatching { URI(u) }.getOrNull()?.takeIf { it.scheme == "https" }?.host
            if (host.isNullOrBlank()) return Result.failure(IllegalArgumentException("Адрес должен начинаться с https://"))
            val k = key.trim()
            if (k.length < MIN_KEY_LENGTH) return Result.failure(IllegalArgumentException("Ключ семьи слишком короткий"))
            return Result.success(ServerConfig(u, k))
        }
    }
}

data class SyncStatus(val lastSuccessAt: Long? = null, val lastError: String? = null, val lastErrorAt: Long? = null)

/** Настройки синхронизации этого телефона. Курсор хранится в базе (sync_state), не здесь. */
@Singleton
class SyncSettings @Inject constructor(private val prefs: DataStore<Preferences>) {

    val config: Flow<ServerConfig?> = prefs.data.map { p ->
        val url = p[KEY_URL]
        val key = p[KEY_KEY]
        if (url != null && key != null) ServerConfig(url, key) else null
    }

    val status: Flow<SyncStatus> = prefs.data.map { p ->
        SyncStatus(p[KEY_LAST_OK], p[KEY_LAST_ERROR], p[KEY_LAST_ERROR_AT])
    }

    suspend fun currentConfig(): ServerConfig? = config.first()

    suspend fun saveConfig(config: ServerConfig) {
        prefs.edit {
            it[KEY_URL] = config.url
            it[KEY_KEY] = config.key
        }
    }

    suspend fun markSuccess(at: Long) {
        prefs.edit {
            it[KEY_LAST_OK] = at
            it.remove(KEY_LAST_ERROR)
            it.remove(KEY_LAST_ERROR_AT)
        }
    }

    suspend fun markError(message: String, at: Long) {
        prefs.edit {
            it[KEY_LAST_ERROR] = message
            it[KEY_LAST_ERROR_AT] = at
        }
    }

    /** Фото, которые этот телефон уже отправил на сервер (имена файлов). */
    suspend fun uploadedPhotos(): Set<String> = prefs.data.first()[KEY_UPLOADED_PHOTOS].orEmpty()

    suspend fun markPhotoUploaded(name: String) {
        prefs.edit { it[KEY_UPLOADED_PHOTOS] = it[KEY_UPLOADED_PHOTOS].orEmpty() + name }
    }

    private companion object {
        val KEY_UPLOADED_PHOTOS = stringSetPreferencesKey("sync_uploaded_photos")
        val KEY_URL = stringPreferencesKey("server_url")
        val KEY_KEY = stringPreferencesKey("server_family_key")
        val KEY_LAST_OK = longPreferencesKey("sync_last_ok")
        val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")
        val KEY_LAST_ERROR_AT = longPreferencesKey("sync_last_error_at")
    }
}
