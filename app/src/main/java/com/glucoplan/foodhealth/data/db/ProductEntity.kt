package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Продукт (ТЗ 5.2). Штрихкод уникален только среди неудалённых продуктов,
 * это проверяет ProductRepository; в базе индекс не уникальный (удалённые
 * продукты сохраняют свой код, а на этапе 1б коды приходят с разных телефонов).
 */
@Entity(tableName = "product", indices = [Index("barcode")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double?,
    val sugar: Double?,
    val salt: Double?,
    val gi: Int?,
    @ColumnInfo(name = "piece_weight_g") val pieceWeightG: Double?,
    val source: String,
    val notes: String?,
    val micro: Map<String, Double>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)
