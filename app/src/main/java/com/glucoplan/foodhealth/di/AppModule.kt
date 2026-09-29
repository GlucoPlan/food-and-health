package com.glucoplan.foodhealth.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.pan.PanPhotos
import com.glucoplan.foodhealth.data.sync.PhotoSync
import com.glucoplan.foodhealth.data.sync.SyncApi
import com.glucoplan.foodhealth.data.sync.SyncSettings
import com.glucoplan.foodhealth.data.sync.SyncBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope на всё время жизни приложения: работа, которая не должна прерываться при уходе с экрана. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncBackend(api: SyncApi): SyncBackend
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun providePhotoSync(db: AppDatabase, photos: PanPhotos, settings: SyncSettings, backend: SyncBackend): PhotoSync =
        PhotoSync(db, photos.directory(), settings, backend)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
