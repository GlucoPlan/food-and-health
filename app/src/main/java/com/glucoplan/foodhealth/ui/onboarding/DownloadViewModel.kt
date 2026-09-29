package com.glucoplan.foodhealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.db.DatabaseGuard
import com.glucoplan.foodhealth.data.sync.FirstSyncChoice
import com.glucoplan.foodhealth.data.sync.SyncEngine
import com.glucoplan.foodhealth.data.sync.SyncProgress
import com.glucoplan.foodhealth.data.sync.SyncResult
import com.glucoplan.foodhealth.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Полная загрузка с сервера с прогрессом (ТЗ 6.1, п. 3; 8.4). */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val session: SetupSession,
    private val guard: DatabaseGuard,
) : ViewModel() {

    val progress: StateFlow<SyncProgress?> = engine.progress

    /** ТЗ 8.4: база была от более новой версии приложения и очищена. */
    val databaseWasReset: StateFlow<Boolean> = guard.wasReset

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        start()
    }

    fun start() {
        _error.value = null
        viewModelScope.launch {
            var result = engine.sync(withPhotos = false)
            // На пустом телефоне вопроса не бывает; если данные всё же есть — объединяем, ничего не теряя
            if (result is SyncResult.NeedsChoice) result = engine.sync(FirstSyncChoice.MERGE, withPhotos = false)
            when (result) {
                is SyncResult.Success -> {
                    guard.dismissResetNotice()
                    // Фото кастрюль — в фоне, экран их не ждёт
                    scheduler.syncNow()
                }
                is SyncResult.Failed -> _error.value = result.message
                else -> Unit
            }
        }
    }

    /** «Продолжить без загрузки»: уже полученное остаётся, остальное докачает фоновая синхронизация. */
    fun skip() {
        session.downloadSkipped.value = true
    }
}
