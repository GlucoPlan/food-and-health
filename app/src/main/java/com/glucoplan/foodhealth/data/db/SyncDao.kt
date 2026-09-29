package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Для экранов и планировщика; сама синхронизация работает с базой напрямую (SyncEngine). */
@Dao
interface SyncDao {

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeOutboxCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox")
    suspend fun outbox(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun state(): SyncStateEntity?

    /** Первая синхронизация (полная загрузка) завершена; null — строки ещё нет. */
    @Query("SELECT initialized FROM sync_state WHERE id = 1")
    fun observeInitialized(): Flow<Boolean?>
}
