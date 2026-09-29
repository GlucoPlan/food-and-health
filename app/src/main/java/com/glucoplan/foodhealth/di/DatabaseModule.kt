package com.glucoplan.foodhealth.di

import android.content.Context
import androidx.room.Room
import com.glucoplan.foodhealth.data.db.ALL_MIGRATIONS
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.DatabaseGuard
import com.glucoplan.foodhealth.data.db.DishDao
import com.glucoplan.foodhealth.data.db.HeightDao
import com.glucoplan.foodhealth.data.db.MealDao
import com.glucoplan.foodhealth.data.db.SyncDao
import com.glucoplan.foodhealth.data.db.SyncTriggers
import com.glucoplan.foodhealth.data.db.PanDao
import com.glucoplan.foodhealth.data.db.ProductDao
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
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(SyncTriggers.callback)
            // Страховка, если DatabaseGuard не смог прочитать версию файла
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun providePanDao(db: AppDatabase): PanDao = db.panDao()

    @Provides
    fun provideDishDao(db: AppDatabase): DishDao = db.dishDao()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()

    @Provides
    fun provideHeightDao(db: AppDatabase): HeightDao = db.heightDao()
}
