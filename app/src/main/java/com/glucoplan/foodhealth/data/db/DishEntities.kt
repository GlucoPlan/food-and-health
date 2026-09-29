package com.glucoplan.foodhealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Блюда с версиями (ТЗ 4.4, 5.2, 5.4). Внешних ключей нет: на этапе 1б записи
 * приходят с сервера в произвольном порядке, связи проверяет код.
 */

@Entity(tableName = "dish")
data class DishEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "current_version_id") val currentVersionId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

/** Варка. [grossWeightG] null — вес не указан, [netWeightG] = сумма сырых ингредиентов. */
@Entity(tableName = "dish_version", indices = [Index("dish_id")])
data class DishVersionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "dish_id") val dishId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "pan_id") val panId: String?,
    @ColumnInfo(name = "gross_weight_g") val grossWeightG: Double?,
    @ColumnInfo(name = "net_weight_g") val netWeightG: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)

@Entity(tableName = "dish_ingredient", indices = [Index("dish_version_id")])
data class DishIngredientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "dish_version_id") val dishVersionId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "weight_g") val weightG: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "device_id") val deviceId: String,
)
