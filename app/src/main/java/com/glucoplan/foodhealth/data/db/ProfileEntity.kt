package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Профиль члена семьи (ТЗ 2, 5.2). Служебные поля — по 5.1. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sd1_enabled") val sd1Enabled: Boolean,
    @ColumnInfo(name = "show_xe") val showXe: Boolean,
    @ColumnInfo(name = "carbs_per_xe") val carbsPerXe: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)
