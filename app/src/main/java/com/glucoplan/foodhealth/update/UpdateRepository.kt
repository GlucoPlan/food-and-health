package com.glucoplan.foodhealth.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glucoplan.foodhealth.BuildConfig
import com.glucoplan.foodhealth.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Последний релиз на GitHub. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val apkSize: Long,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    /** progress от 0 до 1, null — размер неизвестен. */
    data class Downloading(val progress: Float?) : DownloadState
    /** Скачано, но нет разрешения на установку: пользователь дал его и нажимает «Обновить» ещё раз. */
    data object NeedsPermission : DownloadState
    data class Failed(val message: String) : DownloadState
}

data class UpdateState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latest: ReleaseInfo? = null,
    val lastCheckedAt: Long? = null,
    val checking: Boolean = false,
    val checkError: String? = null,
    val download: DownloadState = DownloadState.Idle,
) {
    val updateAvailable: Boolean
        get() = latest != null && VersionComparator.isNewer(latest.version, currentVersion)
}

/**
 * Проверка обновлений через GitHub Releases (ТЗ 8.2).
 * Результат последней проверки хранится в DataStore, поэтому точка на вкладке
 * «Настройки» видна сразу после запуска, даже если проверять ещё рано.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: DataStore<Preferences>,
    private val installer: ApkInstaller,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private val cacheLoaded = scope.launch { loadCache() }

    /** Проверка при запуске: не чаще раза в час. */
    fun checkIfDue() {
        scope.launch {
            cacheLoaded.join()
            val last = _state.value.lastCheckedAt ?: 0L
            if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) check()
        }
    }

    /** Проверка при открытии настроек и по кнопке «Проверить сейчас». */
    fun checkNow() {
        scope.launch {
            cacheLoaded.join()
            check()
        }
    }

    /** Кнопка «Обновить»: скачать APK (если ещё не скачан) и запустить установку. */
    fun startUpdate() {
        val current = _state.value
        val release = current.latest ?: return
        if (!current.updateAvailable || current.download is DownloadState.Downloading) return
        val url = release.apkUrl ?: run {
            setDownload(DownloadState.Failed("В релизе нет APK"))
            return
        }
        // Сразу, до запуска корутины: повторное нажатие уже увидит Downloading
        setDownload(DownloadState.Downloading(null))
        scope.launch {
            val apk = installer.downloaded(release.version) ?: try {
                installer.download(url, release.version, release.apkSize) {
                    setDownload(DownloadState.Downloading(it))
                }
            } catch (e: Exception) {
                setDownload(DownloadState.Failed("Не удалось скачать: ${e.message ?: e.javaClass.simpleName}"))
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (!installer.canInstall()) {
                    setDownload(DownloadState.NeedsPermission)
                    installer.openInstallPermissionSettings()
                } else if (installer.install(apk)) {
                    setDownload(DownloadState.Idle)
                } else {
                    setDownload(DownloadState.Failed("Не удалось открыть установщик"))
                }
            }
        }
    }

    private suspend fun check() = checkMutex.withLock {
        _state.update { it.copy(checking = true, checkError = null) }
        try {
            val release = fetchLatest()
            val now = System.currentTimeMillis()
            saveCache(release, now)
            _state.update { it.copy(latest = release, lastCheckedAt = now, checking = false) }
        } catch (e: Exception) {
            _state.update {
                it.copy(checking = false, checkError = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** null — в репозитории ещё нет релизов. */
    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FoodHealth-Android")
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> return@withContext null
                response.code == 403 || response.code == 429 ->
                    throw Exception("GitHub временно ограничил запросы, попробуйте позже")
                !response.isSuccessful -> throw Exception("GitHub ответил ${response.code}")
            }
            val json = JSONObject(response.body.string())
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
            ReleaseInfo(
                version = json.getString("tag_name").trim().removePrefix("v"),
                notes = json.optString("body").trim(),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
                apkSize = apk?.optLong("size") ?: 0L,
            )
        }
    }

    private suspend fun loadCache() {
        val p = prefs.data.first()
        val version = p[KEY_VERSION]
        val release = version?.let {
            ReleaseInfo(it, p[KEY_NOTES].orEmpty(), p[KEY_APK_URL], p[KEY_APK_SIZE] ?: 0L)
        }
        _state.update { it.copy(latest = release, lastCheckedAt = p[KEY_CHECKED_AT]) }
    }

    private suspend fun saveCache(release: ReleaseInfo?, checkedAt: Long) {
        prefs.edit { p ->
            p[KEY_CHECKED_AT] = checkedAt
            if (release == null) {
                p.remove(KEY_VERSION); p.remove(KEY_NOTES); p.remove(KEY_APK_URL); p.remove(KEY_APK_SIZE)
            } else {
                p[KEY_VERSION] = release.version
                p[KEY_NOTES] = release.notes
                if (release.apkUrl != null) p[KEY_APK_URL] = release.apkUrl else p.remove(KEY_APK_URL)
                p[KEY_APK_SIZE] = release.apkSize
            }
        }
    }

    private fun setDownload(download: DownloadState) = _state.update { it.copy(download = download) }

    private companion object {
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        val KEY_CHECKED_AT = longPreferencesKey("update_checked_at")
        val KEY_VERSION = stringPreferencesKey("update_latest_version")
        val KEY_NOTES = stringPreferencesKey("update_latest_notes")
        val KEY_APK_URL = stringPreferencesKey("update_latest_apk_url")
        val KEY_APK_SIZE = longPreferencesKey("update_latest_apk_size")
    }
}
