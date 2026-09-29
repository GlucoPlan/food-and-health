package com.glucoplan.foodhealth.data.pan

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PanRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: PanRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) { File(tmp.root, "t.preferences_pb") })
        repo = PanRepository(db.panDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    @Test
    fun `новая кастрюля получает UUID и служебные поля`() = runTest {
        val before = System.currentTimeMillis()
        val (id, result) = repo.save(null, PanForm("Сотейник", "650", "x.jpg"))
        assertThat(result).isInstanceOf(PanValidation.Valid::class.java)
        val entity = db.panDao().getById(id!!)!!
        assertThat(entity.id).matches("[0-9a-f-]{36}")
        assertThat(entity.weightG).isEqualTo(650.0)
        assertThat(entity.photo).isEqualTo("x.jpg")
        assertThat(entity.updatedAt).isAtLeast(before)
        assertThat(entity.deviceId).isEqualTo(prefs.deviceId())
    }

    @Test
    fun `ошибки — ничего не пишется`() = runTest {
        val (id, result) = repo.save(null, PanForm("", "0"))
        assertThat(id).isNull()
        assertThat(result).isInstanceOf(PanValidation.Invalid::class.java)
        assertThat(repo.observePans().first()).isEmpty()
    }

    @Test
    fun `удалённая пропадает из выбора, но остаётся среди всех`() = runTest {
        val (id, _) = repo.save(null, PanForm("Сотейник", "650"))
        repo.delete(id!!)
        assertThat(repo.observePans().first()).isEmpty()
        assertThat(repo.observeAllIncludingDeleted().first().single().deleted).isTrue()
    }
}
