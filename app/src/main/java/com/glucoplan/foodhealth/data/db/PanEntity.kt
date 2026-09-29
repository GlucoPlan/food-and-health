package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Кастрюля (ТЗ 4.5, 5.2). [photo] — имя файла в папке pan_photos. */
@Entity(tableName = "pan")
data class PanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "weight_g") val weightG: Double,
    val photo: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)
