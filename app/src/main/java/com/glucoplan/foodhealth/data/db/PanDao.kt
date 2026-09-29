package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PanDao {

    @Query("SELECT * FROM pan WHERE deleted = 0 ORDER BY name")
    fun observeActive(): Flow<List<PanEntity>>

    /** Вместе с удалёнными: на них ссылаются сохранённые варки. */
    @Query("SELECT * FROM pan")
    fun observeAll(): Flow<List<PanEntity>>

    @Query("SELECT * FROM pan WHERE id = :id")
    suspend fun getById(id: String): PanEntity?

    @Upsert
    suspend fun upsert(pan: PanEntity)
}
