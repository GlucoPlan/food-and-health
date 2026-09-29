package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE deleted = 0 ORDER BY name")
    fun observeActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE deleted = 0")
    suspend fun getActive(): List<ProfileEntity>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}
