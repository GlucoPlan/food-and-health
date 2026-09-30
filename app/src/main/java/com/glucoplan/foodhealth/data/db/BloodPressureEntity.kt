package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Давление и пульс (ТЗ 15.2): одно измерение; пульс необязателен. */
@Entity(tableName = "blood_pressure", indices = [Index(value = ["profile_id", "measured_at"])])
data class BloodPressureEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface BloodPressureDao {

    @Query(
        "SELECT * FROM blood_pressure WHERE profile_id = :profileId AND deleted = 0 ORDER BY measured_at DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure WHERE profile_id = :profileId AND deleted = 0")
    fun observeAll(profileId: String): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure WHERE id = :id")
    suspend fun getById(id: String): BloodPressureEntity?

    @Upsert
    suspend fun upsert(pressure: BloodPressureEntity)
}
