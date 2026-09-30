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
    // Этап 3 (ТЗ 17.3): нормы. Пустые нормы — расчёт
    /** sedentary / light / moderate / high; пусто — moderate. */
    val activity: String? = null,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double? = null,
    @ColumnInfo(name = "weight_pace_kg", defaultValue = "0.5") val weightPaceKg: Double = 0.5,
    @ColumnInfo(name = "norm_kcal") val normKcal: Double? = null,
    @ColumnInfo(name = "norm_protein") val normProtein: Double? = null,
    @ColumnInfo(name = "norm_fat") val normFat: Double? = null,
    @ColumnInfo(name = "norm_carbs") val normCarbs: Double? = null,
    /** Целевой диапазон сахара, ммоль/л; только у профиля с «Дневником СД1». */
    @ColumnInfo(name = "glucose_low") val glucoseLow: Double? = null,
    @ColumnInfo(name = "glucose_high") val glucoseHigh: Double? = null,
)
