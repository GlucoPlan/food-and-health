package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Сон (ТЗ 15.2): когда заснул и проснулся, необязательная оценка.
 * [source] и [externalId] — для пробного импорта из Health Connect (15.5).
 */
@Entity(tableName = "sleep", indices = [Index(value = ["profile_id", "woke_at"])])
data class SleepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "asleep_at") val asleepAt: Long,
    @ColumnInfo(name = "woke_at") val wokeAt: Long,
    /** bad / normal / good или null. */
    val quality: String?,
    /** manual / health_connect. */
    val source: String,
    @ColumnInfo(name = "external_id") val externalId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface SleepDao {

    @Query("SELECT * FROM sleep WHERE profile_id = :profileId AND deleted = 0 ORDER BY woke_at DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep WHERE profile_id = :profileId AND deleted = 0")
    fun observeAll(profileId: String): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep WHERE id = :id")
    suspend fun getById(id: String): SleepEntity?

    @Upsert
    suspend fun upsert(sleep: SleepEntity)
}
