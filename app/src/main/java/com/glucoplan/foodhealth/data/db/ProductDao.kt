package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    /** Поиск и сортировка — в ProductSearch: LIKE в SQLite не знает регистра кириллицы. */
    @Query("SELECT * FROM product WHERE deleted = 0")
    fun observeActive(): Flow<List<ProductEntity>>

    /** Вместе с удалёнными: они продолжают считаться в блюдах и прошлых приёмах. */
    @Query("SELECT * FROM product")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM product WHERE barcode = :barcode AND deleted = 0")
    suspend fun findActiveByBarcode(barcode: String): List<ProductEntity>

    @Upsert
    suspend fun upsert(product: ProductEntity)
}
