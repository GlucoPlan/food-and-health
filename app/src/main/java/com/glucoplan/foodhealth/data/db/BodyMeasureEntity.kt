package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Обхваты тела (ТЗ 15.2), см; любое поле можно пропустить. Правая сторона. */
@Entity(tableName = "body_measure", indices = [Index(value = ["profile_id", "measured_at"])])
data class BodyMeasureEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    val neck: Double?,
    val chest: Double?,
    val waist: Double?,
    /** Живот по пупку. */
    val belly: Double?,
    /** Бёдра (таз). */
    val hips: Double?,
    /** Бедро (нога). */
    val thigh: Double?,
    val calf: Double?,
    /** Плечо. */
    val arm: Double?,
    val wrist: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Dao
interface BodyMeasureDao {

    @Query("SELECT * FROM body_measure WHERE profile_id = :profileId AND deleted = 0 ORDER BY measured_at DESC LIMIT :limit")
    fun observeRecent(profileId: String, limit: Int): Flow<List<BodyMeasureEntity>>

    @Query("SELECT * FROM body_measure WHERE profile_id = :profileId AND deleted = 0")
    fun observeAll(profileId: String): Flow<List<BodyMeasureEntity>>

    @Query("SELECT * FROM body_measure WHERE id = :id")
    suspend fun getById(id: String): BodyMeasureEntity?

    @Upsert
    suspend fun upsert(measure: BodyMeasureEntity)
}
