package com.glucoplan.foodhealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.sync.ServerConfig
import com.glucoplan.foodhealth.data.sync.SyncBackend
import com.glucoplan.foodhealth.data.sync.SyncException
import com.glucoplan.foodhealth.data.sync.SyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUi(
    val url: String = "https://",
    val key: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
)

/** Первый запуск (ТЗ 6.1, п. 1, 4): адрес сервера и ключ семьи или «работать без сервера». */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val backend: SyncBackend,
    private val syncSettings: SyncSettings,
    private val devicePrefs: DevicePrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUi())
    val state: StateFlow<ConnectUi> = _state.asStateFlow()

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, error = null) }
    fun onKeyChange(v: String) = _state.update { it.copy(key = v, error = null) }

    /** Проверить соединение и сохранить; дальше экран загрузки (RootDecision). */
    fun connect() {
        val config = ServerConfig.parse(_state.value.url, _state.value.key).getOrElse { e ->
            _state.update { it.copy(error = e.message) }
            return
        }
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            try {
                backend.health(config)
                syncSettings.saveConfig(config)
                devicePrefs.setSetupDone()
            } catch (e: SyncException) {
                _state.update { it.copy(connecting = false, error = e.message) }
            }
        }
    }

    /** «Работать без сервера» (ТЗ 6.1, п. 4): подключить можно позже в настройках. */
    fun skip() {
        viewModelScope.launch { devicePrefs.setSetupDone() }
    }
}
