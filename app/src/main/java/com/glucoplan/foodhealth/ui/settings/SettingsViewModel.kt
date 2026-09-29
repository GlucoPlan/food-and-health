package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.db.SyncDao
import com.glucoplan.foodhealth.data.sync.FirstSyncChoice
import com.glucoplan.foodhealth.data.sync.ServerConfig
import com.glucoplan.foodhealth.data.sync.SyncBackend
import com.glucoplan.foodhealth.data.sync.SyncEngine
import com.glucoplan.foodhealth.data.sync.SyncException
import com.glucoplan.foodhealth.data.sync.SyncResult
import com.glucoplan.foodhealth.data.sync.SyncSettings
import com.glucoplan.foodhealth.data.sync.SyncStatus
import com.glucoplan.foodhealth.update.UpdateRepository
import com.glucoplan.foodhealth.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Карточка «Сервер»: введённые адрес и ключ, результат проверки. */
data class ServerUi(
    val url: String = "",
    val key: String = "",
    val checking: Boolean = false,
    /** Текст результата проверки и успешна ли она. */
    val checkMessage: String? = null,
    val checkOk: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val devicePrefs: DevicePrefs,
    profiles: ProfileRepository,
    private val syncSettings: SyncSettings,
    private val syncEngine: SyncEngine,
    private val backend: SyncBackend,
    syncDao: SyncDao,
) : ViewModel() {

    private val _server = MutableStateFlow(ServerUi())
    val server: StateFlow<ServerUi> = _server.asStateFlow()

    val configured: StateFlow<Boolean> =
        syncSettings.config.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val syncStatus: StateFlow<SyncStatus> =
        syncSettings.status.stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus())

    val syncing: StateFlow<Boolean> = syncEngine.running

    /** Сколько изменений ещё не отправлено. */
    val pending: StateFlow<Int> = syncDao.observeOutboxCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _choice = MutableStateFlow<Int?>(null)

    /** Первое подключение к непустому серверу: число записей на сервере, ждём выбора (ТЗ 6.1, п. 5). */
    val choice: StateFlow<Int?> = _choice.asStateFlow()

    init {
        viewModelScope.launch {
            syncSettings.currentConfig()?.let { c -> _server.update { it.copy(url = c.url, key = c.key) } }
            if (_server.value.url.isEmpty()) _server.update { it.copy(url = "https://") }
        }
    }

    fun onUrlChange(v: String) = _server.update { it.copy(url = v, checkMessage = null) }
    fun onKeyChange(v: String) = _server.update { it.copy(key = v, checkMessage = null) }

    /** «Проверить соединение»: адрес и ключ сохраняются, если сервер их принял. */
    fun saveAndCheck() {
        val parsed = ServerConfig.parse(_server.value.url, _server.value.key)
        val config = parsed.getOrElse { e ->
            _server.update { it.copy(checkMessage = e.message, checkOk = false) }
            return
        }
        _server.update { it.copy(checking = true, checkMessage = null) }
        viewModelScope.launch {
            val message = try {
                val records = backend.health(config)
                syncSettings.saveConfig(config)
                _server.update { it.copy(url = config.url, key = config.key) }
                true to "Соединение есть, записей на сервере: $records"
            } catch (e: SyncException) {
                false to (e.message ?: "Ошибка")
            }
            _server.update { it.copy(checking = false, checkOk = message.first, checkMessage = message.second) }
            if (message.first) syncNow()
        }
    }

    fun syncNow(choice: FirstSyncChoice? = null) {
        _choice.value = null
        viewModelScope.launch {
            val result = syncEngine.sync(choice)
            if (result is SyncResult.NeedsChoice) _choice.value = result.serverRecords
        }
    }

    fun dismissChoice() {
        _choice.value = null
    }

    val update: StateFlow<UpdateState> = updates.state

    val profileList: StateFlow<List<Profile>> =
        profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ownerId: StateFlow<String?> =
        devicePrefs.ownerId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setOwner(profile: Profile) {
        viewModelScope.launch { devicePrefs.setOwner(profile.id) }
    }

    /** ТЗ 8.2: проверять обновление при каждом открытии настроек. */
    fun onOpened() = updates.checkNow()

    fun checkNow() = updates.checkNow()

    fun startUpdate() = updates.startUpdate()
}
