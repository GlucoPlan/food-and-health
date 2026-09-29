package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Рост (ТЗ 15.3): история по датам, в профиле — последнее значение. [measuredAt] — местная полночь дня замера. */
@Entity(tableName = "height", indices = [Index("profile_id")])
data class HeightEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface HeightDao {

    /** История роста профиля, новые сверху. */
    @Query("SELECT * FROM height WHERE profile_id = :profileId AND deleted = 0 ORDER BY measured_at DESC, updated_at DESC")
    fun observeForProfile(profileId: String): Flow<List<HeightEntity>>

    @Query("SELECT * FROM height WHERE id = :id")
    suspend fun getById(id: String): HeightEntity?

    @Upsert
    suspend fun upsert(height: HeightEntity)
}
