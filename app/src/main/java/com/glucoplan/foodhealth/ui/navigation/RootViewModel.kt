package com.glucoplan.foodhealth.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.db.SyncDao
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.sync.SyncSettings
import com.glucoplan.foodhealth.ui.onboarding.SetupSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RootState {
    Loading,

    /** Первый запуск: подключение к серверу или «работать без сервера» (ТЗ 6.1, п. 1, 4). */
    Connect,

    /** Полная загрузка с сервера: новый телефон, переустановка, очищенная база (ТЗ 6.1, п. 3; 8.4). */
    Download,

    /** Выбор владельца телефона (ТЗ 2, 6.1, п. 2). */
    NeedsOwner,
    Ready,
}

/** Какой экран показать при запуске. */
object RootDecision {
    fun decide(
        ownerId: String?,
        profileIds: Set<String>,
        configured: Boolean,
        setupDone: Boolean,
        syncInitialized: Boolean,
        downloadSkipped: Boolean,
    ): RootState = when {
        ownerId != null && ownerId in profileIds -> RootState.Ready
        // Владельца здесь ещё ни разу не выбирали — это новая установка
        ownerId == null && !configured && !setupDone -> RootState.Connect
        configured && !syncInitialized && !downloadSkipped -> RootState.Download
        else -> RootState.NeedsOwner
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    devicePrefs: DevicePrefs,
    profiles: ProfileRepository,
    syncSettings: SyncSettings,
    syncDao: SyncDao,
    session: SetupSession,
) : ViewModel() {

    private val setup = combine(
        syncSettings.config.map { it != null },
        devicePrefs.setupDone,
        syncDao.observeInitialized().map { it == true },
        session.downloadSkipped,
    ) { configured, setupDone, initialized, skipped -> Setup(configured, setupDone, initialized, skipped) }

    val state: StateFlow<RootState> =
        combine(devicePrefs.ownerId, profiles.observeProfiles(), setup) { owner, list, s ->
            RootDecision.decide(owner, list.map { it.id }.toSet(), s.configured, s.setupDone, s.initialized, s.skipped)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    private data class Setup(val configured: Boolean, val setupDone: Boolean, val initialized: Boolean, val skipped: Boolean)
}
