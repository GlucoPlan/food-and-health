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
        val result = repo.save(null, filledProfileForm("Иван"))
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
        repo.save(null, filledProfileForm("Дочь"))
        val id = repo.observeProfiles().first().single().id

        repo.save(id, filledProfileForm("Дочь", sd1Enabled = true, showXe = true, carbsPerXe = "12"))

        assertThat(repo.get(id)).isEqualTo(
            Profile(
                id, "Дочь", sd1Enabled = true, showXe = true, carbsPerXe = 12.0,
                sex = Sex.FEMALE, birthDate = java.time.LocalDate.of(2000, 1, 1),
            )
        )
    }

    @Test
    fun `переименование в своё же имя — не дубликат`() = runTest {
        repo.save(null, filledProfileForm("Иван"))
        val id = repo.observeProfiles().first().single().id
        assertThat(repo.save(id, filledProfileForm("иван"))).isInstanceOf(ProfileValidation.Valid::class.java)
    }

    @Test
    fun `дубликат не сохраняется`() = runTest {
        repo.save(null, filledProfileForm("Иван"))
        val result = repo.save(null, filledProfileForm("Иван"))
        assertThat(result).isInstanceOf(ProfileValidation.Invalid::class.java)
        assertThat(db.profileDao().getActive()).hasSize(1)
    }

    @Test
    fun `пол, дата рождения и вода сохраняются`() = runTest {
        repo.save(null, filledProfileForm("Я", sex = Sex.MALE, birthDate = java.time.LocalDate.of(1985, 3, 8))
            .copy(waterEnabled = true, waterMlPerKg = "35"))
        val p = repo.observeProfiles().first().single()
        assertThat(p.sex).isEqualTo(Sex.MALE)
        assertThat(p.birthDate).isEqualTo(java.time.LocalDate.of(1985, 3, 8))
        assertThat(p.waterEnabled).isTrue()
        assertThat(p.waterMlPerKg).isEqualTo(35.0)
        assertThat(p.incomplete).isFalse()
    }

    @Test
    fun `старый профиль без пола и даты читается и помечен неполным`() = runTest {
        db.profileDao().upsert(
            com.glucoplan.foodhealth.data.db.ProfileEntity("old", "Рита", false, false, 10.0, 1L, false, "dev")
        )
        val p = repo.get("old")!!
        assertThat(p.sex).isNull()
        assertThat(p.birthDate).isNull()
        assertThat(p.incomplete).isTrue()
        assertThat(p.waterMlPerKg).isEqualTo(30.0)
        // Без пола и даты сохранить нельзя
        assertThat(repo.save("old", ProfileForm("Рита"))).isInstanceOf(ProfileValidation.Invalid::class.java)
    }

    @Test
    fun `активность, цель, ручные нормы и диапазон сахара сохраняются (17_3)`() = runTest {
        repo.save(
            null,
            filledProfileForm("Я", sd1Enabled = true, sex = Sex.MALE, birthDate = java.time.LocalDate.of(1985, 3, 8)).copy(
                activity = com.glucoplan.foodhealth.data.norms.Activity.LIGHT,
                targetWeightKg = "80", weightPaceKg = "0,3",
                normKcal = "2100", normCarbs = "200",
                glucoseLow = "4", glucoseHigh = "9,5",
            ),
        )
        val p = repo.observeProfiles().first().single()
        assertThat(p.activity).isEqualTo(com.glucoplan.foodhealth.data.norms.Activity.LIGHT)
        assertThat(p.targetWeightKg).isEqualTo(80.0)
        assertThat(p.weightPaceKg).isEqualTo(0.3)
        assertThat(p.manualNorms).isEqualTo(com.glucoplan.foodhealth.data.norms.NormSet(2100.0, null, null, 200.0))
        assertThat(p.glucoseLow).isEqualTo(4.0)
        assertThat(p.glucoseHigh).isEqualTo(9.5)

        // Очистка полей — снова расчёт и поддержание
        repo.save(p.id, filledProfileForm("Я", sd1Enabled = true, sex = Sex.MALE, birthDate = java.time.LocalDate.of(1985, 3, 8)))
        val cleared = repo.get(p.id)!!
        assertThat(cleared.targetWeightKg).isNull()
        assertThat(cleared.manualNorms).isEqualTo(com.glucoplan.foodhealth.data.norms.NormSet())
        assertThat(cleared.glucoseLow).isNull()
    }

    @Test
    fun `выключение СД1 не стирает диапазон сахара`() = runTest {
        val form = filledProfileForm("Дочь", sd1Enabled = true).copy(glucoseLow = "4", glucoseHigh = "10")
        repo.save(null, form)
        val id = repo.observeProfiles().first().single().id
        repo.save(id, form.copy(sd1Enabled = false, glucoseLow = "", glucoseHigh = ""))
        val p = repo.get(id)!!
        assertThat(p.glucoseLow).isEqualTo(4.0)
        assertThat(p.glucoseHigh).isEqualTo(10.0)
    }

    @Test
    fun `старый профиль без норм читается с умолчаниями`() = runTest {
        db.profileDao().upsert(
            com.glucoplan.foodhealth.data.db.ProfileEntity("old", "Рита", false, false, 10.0, 1L, false, "dev")
        )
        val p = repo.get("old")!!
        assertThat(p.activity).isEqualTo(com.glucoplan.foodhealth.data.norms.Activity.MODERATE)
        assertThat(p.weightPaceKg).isEqualTo(0.5)
        assertThat(p.targetWeightKg).isNull()
        assertThat(p.glucoseLow).isNull()
    }
}
