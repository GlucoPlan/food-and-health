package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Приём пищи (ТЗ 5.2). [glucose] и [insulinDose] — для дневника СД1 (раздел 7),
 * заполняются только у профилей с включённым дневником.
 */
@Entity(tableName = "meal", indices = [Index(value = ["profile_id", "eaten_at"])])
data class MealEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
    val notes: String?,
    val glucose: Double?,
    @ColumnInfo(name = "insulin_dose") val insulinDose: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

/**
 * Позиция приёма: продукт ([productId]) или конкретная варка блюда ([dishVersionId], ТЗ 5.4).
 * snapshot_* — КБЖУ порции на момент записи (ТЗ 5.3).
 */
@Entity(tableName = "meal_item", indices = [Index("meal_id"), Index("dish_version_id")])
data class MealItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "meal_id") val mealId: String,
    val type: String,
    @ColumnInfo(name = "product_id") val productId: String?,
    @ColumnInfo(name = "dish_version_id") val dishVersionId: String?,
    @ColumnInfo(name = "weight_g") val weightG: Double,
    val pieces: Double?,
    @ColumnInfo(name = "snapshot_kcal") val snapshotKcal: Double,
    @ColumnInfo(name = "snapshot_protein") val snapshotProtein: Double,
    @ColumnInfo(name = "snapshot_fat") val snapshotFat: Double,
    @ColumnInfo(name = "snapshot_carbs") val snapshotCarbs: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

/** Когда профиль ел продукт или варку — для недавних и частых позиций. */
data class ItemUsage(
    val type: String,
    val productId: String?,
    val dishVersionId: String?,
    val eatenAt: Long,
)
