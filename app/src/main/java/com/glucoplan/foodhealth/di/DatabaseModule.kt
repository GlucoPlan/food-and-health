package com.glucoplan.foodhealth.di

import android.content.Context
import androidx.room.Room
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.DatabaseGuard
import com.glucoplan.foodhealth.data.db.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, guard: DatabaseGuard): AppDatabase {
        guard.prepare(AppDatabase.NAME, AppDatabase.VERSION)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Страховка, если DatabaseGuard не смог прочитать версию файла
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
}
