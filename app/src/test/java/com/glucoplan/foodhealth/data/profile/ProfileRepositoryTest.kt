package com.glucoplan.foodhealth.data.profile

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
class ProfileRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var devicePrefs: DevicePrefs
    private lateinit var repo: ProfileRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = prefsScope) {
            File(tmp.root, "test.preferences_pb")
        }
        devicePrefs = DevicePrefs(store)
        repo = ProfileRepository(db.profileDao(), devicePrefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    @Test
    fun `новый профиль получает UUID, updated_at и device_id`() = runTest {
        val before = System.currentTimeMillis()
        val result = repo.save(null, ProfileForm("Иван"))
        val after = System.currentTimeMillis()

        assertThat(result).isInstanceOf(ProfileValidation.Valid::class.java)
        val saved = db.profileDao().getActive().single()
        assertThat(saved.id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertThat(saved.updatedAt).isAtLeast(before)
        assertThat(saved.updatedAt).isAtMost(after)
        assertThat(saved.deviceId).isEqualTo(devicePrefs.deviceId())
        assertThat(saved.deleted).isFalse()
        assertThat(saved.carbsPerXe).isEqualTo(10.0)
    }

    @Test
    fun `device_id постоянный`() = runTest {
        assertThat(devicePrefs.deviceId()).isEqualTo(devicePrefs.deviceId())
    }

    @Test
    fun `изменение сохраняет id и обновляет поля`() = runTest {
        repo.save(null, ProfileForm("Дочь"))
        val id = repo.observeProfiles().first().single().id

        repo.save(id, ProfileForm("Дочь", sd1Enabled = true, showXe = true, carbsPerXe = "12"))

        assertThat(repo.get(id)).isEqualTo(Profile(id, "Дочь", sd1Enabled = true, showXe = true, carbsPerXe = 12.0))
    }

    @Test
    fun `переименование в своё же имя — не дубликат`() = runTest {
        repo.save(null, ProfileForm("Иван"))
        val id = repo.observeProfiles().first().single().id
        assertThat(repo.save(id, ProfileForm("иван"))).isInstanceOf(ProfileValidation.Valid::class.java)
    }

    @Test
    fun `дубликат не сохраняется`() = runTest {
        repo.save(null, ProfileForm("Иван"))
        val result = repo.save(null, ProfileForm("Иван"))
        assertThat(result).isInstanceOf(ProfileValidation.Invalid::class.java)
        assertThat(db.profileDao().getActive()).hasSize(1)
    }
}
