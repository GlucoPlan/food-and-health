package com.glucoplan.foodhealth.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glucoplan.foodhealth.data.db.SyncDao
import com.glucoplan.foodhealth.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Когда синхронизировать (ТЗ 6): при запуске, через 5 секунд после правок (пачкой),
 * в фоне раз в 15 минут при наличии сети, по кнопке.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val engine: SyncEngine,
    private val syncDao: SyncDao,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            syncDao.observeOutboxCount()
                .filter { it > 0 }
                .debounce(CHANGE_DELAY_MS)
                .collect { engine.sync() }
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Синхронизировать сейчас, не дожидаясь результата (запуск приложения). */
    fun syncNow() {
        scope.launch { engine.sync() }
    }

    private companion object {
        const val CHANGE_DELAY_MS = 5_000L
        const val WORK_NAME = "sync"
    }
}

/** Фоновая синхронизация раз в 15 минут. Ошибка пишется в статус, повторит следующий запуск. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        engine.sync()
        return Result.success()
    }
}
