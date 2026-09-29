package com.glucoplan.foodhealth.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.SyncTriggers
import com.glucoplan.foodhealth.data.meal.MealDraftStore
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File

/** «Телефон» для тестов синхронизации: своя база в памяти, свои настройки и device_id. */
class Phone(dir: File, name: String, server: SyncBackend) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(SyncTriggers.callback)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "$name.preferences_pb") }
    val prefs = DevicePrefs(store)
    val settings = SyncSettings(store)
    val draft = MealDraftStore(store, scope)
    val products = ProductRepository(db.productDao(), prefs)
    val profiles = ProfileRepository(db.profileDao(), prefs)
    val engine = SyncEngine(db, settings, server, prefs, draft)

    suspend fun connect() = settings.saveConfig(ServerConfig("https://example.org", "k".repeat(20)))

    suspend fun addProduct(name: String, kcal: String = "100"): String {
        products.save(null, ProductForm(name, kcal = kcal, protein = "1", fat = "1", carbs = "1"))
        return products.observeProducts().first().single { it.name == name }.id
    }

    suspend fun rename(id: String, name: String) {
        products.save(id, ProductForm(name, kcal = "100", protein = "1", fat = "1", carbs = "1"))
    }

    suspend fun product(id: String): Product? = products.get(id)

    suspend fun activeNames(): List<String> = products.observeProducts().first().map { it.name }.sorted()

    suspend fun outbox() = db.syncDao().outbox()

    fun close() {
        db.close()
        scope.cancel()
    }
}
