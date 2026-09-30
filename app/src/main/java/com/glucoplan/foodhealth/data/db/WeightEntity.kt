package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Вес (ТЗ 15.2): кг с точностью 0,1, дата и время замера. */
@Entity(tableName = "weight", indices = [Index(value = ["profile_id", "measured_at"])])
data class WeightEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface WeightDao {

    /** Последние замеры профиля, новые сверху. */
    @Query("SELECT * FROM weight WHERE profile_id = :profileId AND deleted = 0 ORDER BY measured_at DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight WHERE profile_id = :profileId AND deleted = 0 ORDER BY measured_at DESC LIMIT 1")
    suspend fun latest(profileId: String): WeightEntity?

    /** Все замеры профиля — для «Истории» (15.4). */
    @Query("SELECT * FROM weight WHERE profile_id = :profileId AND deleted = 0")
    fun observeAll(profileId: String): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight WHERE id = :id")
    suspend fun getById(id: String): WeightEntity?

    @Upsert
    suspend fun upsert(weight: WeightEntity)
}
