package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Вода (ТЗ 15.2): только чистая вода, мл. */
@Entity(tableName = "water", indices = [Index(value = ["profile_id", "drunk_at"])])
data class WaterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "drunk_at") val drunkAt: Long,
    val ml: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface WaterDao {

    /** Записи за промежуток [from, to), новые сверху. */
    @Query(
        "SELECT * FROM water WHERE profile_id = :profileId AND deleted = 0 AND drunk_at >= :from AND drunk_at < :to " +
            "ORDER BY drunk_at DESC"
    )
    fun observeBetween(profileId: String, from: Long, to: Long): Flow<List<WaterEntity>>

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water WHERE profile_id = :profileId AND deleted = 0 AND drunk_at >= :from AND drunk_at < :to")
    suspend fun sumBetween(profileId: String, from: Long, to: Long): Int

    /** Последняя запись — её объём подставляется в следующую (15.4). */
    @Query("SELECT * FROM water WHERE profile_id = :profileId AND deleted = 0 ORDER BY drunk_at DESC, updated_at DESC LIMIT 1")
    fun observeLatest(profileId: String): Flow<WaterEntity?>

    @Query("SELECT * FROM water WHERE profile_id = :profileId AND deleted = 0 ORDER BY drunk_at DESC, updated_at DESC LIMIT 1")
    suspend fun latest(profileId: String): WaterEntity?

    @Query("SELECT * FROM water WHERE id = :id")
    suspend fun getById(id: String): WaterEntity?

    @Upsert
    suspend fun upsert(water: WaterEntity)
}
