package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Профиль члена семьи (ТЗ 2, 5.2, 15.3). Служебные поля — по 5.1. */
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
    // Этап 2 (ТЗ 15.3). Пол и дата рождения обязательны в редакторе, но в базе могут быть пустыми:
    // у профилей этапа 1 их нет, их может прислать и старая версия приложения
    /** male / female. */
    val sex: String? = null,
    /** ГГГГ-ММ-ДД. */
    @ColumnInfo(name = "birth_date") val birthDate: String? = null,
    @ColumnInfo(name = "water_enabled", defaultValue = "0") val waterEnabled: Boolean = false,
    @ColumnInfo(name = "water_ml_per_kg", defaultValue = "30") val waterMlPerKg: Double = 30.0,
)
