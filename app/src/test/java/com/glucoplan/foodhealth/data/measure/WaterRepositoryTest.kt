package com.glucoplan.foodhealth.data.measure

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.profile.Sex
import com.glucoplan.foodhealth.data.profile.filledProfileForm
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

/** Вода (ТЗ 15.2–15.4). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class WaterRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var profiles: ProfileRepository
    private lateinit var weights: WeightRepository
    private lateinit var repo: WaterRepository
    private lateinit var me: String

    // Полдень сегодня: записи «сегодня +N минут» не окажутся в будущем, даже если тест идёт сразу после полуночи
    private val now = java.time.LocalDate.now().atTime(12, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val min = 60_000L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        profiles = ProfileRepository(db.profileDao(), prefs)
        weights = WeightRepository(db.weightDao(), prefs)
        repo = WaterRepository(db.waterDao(), weights, profiles, prefs)
        profiles.save(null, filledProfileForm("Я", sex = Sex.MALE).copy(waterEnabled = true, waterMlPerKg = "30"))
        me = profiles.observeProfiles().first().single().id
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `последний объём — из последней записи, без записей 250`() = runTest {
        assertThat(repo.observeLastVolume(me).first()).isEqualTo(250)
        repo.save(null, me, "330", now - 10 * min, now)
        assertThat(repo.observeLastVolume(me).first()).isEqualTo(330)
    }

    @Test
    fun `сообщение с итогом дня и нормой по последнему весу`() = runTest {
        assertThat(repo.save(null, me, "250", null, now)).isEqualTo(MeasureSave.Saved("Записано: вода 250 мл · сегодня 0,25 л"))
        weights.save(null, me, "84", null, now)
        assertThat(repo.save(null, me, "500", null, now)).isEqualTo(MeasureSave.Saved("Записано: вода 500 мл · сегодня 0,75 из 2,52 л"))
    }

    @Test
    fun `сегодня — без вчерашних, удалённых и чужих`() = runTest {
        val (from, _) = WaterNorm.dayBounds(now)
        repo.save(null, me, "200", from + min, now)
        repo.save(null, me, "300", from - min, now)          // вчера
        repo.save(null, "other", "400", from + 2 * min, now)  // чужое
        repo.save(null, me, "100", from + 3 * min, now)
        val hundred = repo.observeToday(me, now).first().first { it.ml == 100 }
        repo.delete(hundred.id)

        assertThat(repo.observeToday(me, now).first().map { it.ml }).containsExactly(200)
        assertThat(repo.todaySummary(me, now)).isEqualTo("сегодня 0,2 л")
    }

    @Test
    fun `проверка ввода и время в будущем`() = runTest {
        listOf("0", "3001", "250,5", "", "стакан").forEach {
            val r = repo.save(null, me, it, null, now) as MeasureSave.Invalid
            assertThat(r.errors).containsKey(WaterRepository.FIELD_ML)
        }
        val future = repo.save(null, me, "250", now + 60 * min, now) as MeasureSave.Invalid
        assertThat(future.errors).containsKey(MeasureSave.TIME)
        assertThat(repo.observeToday(me, now).first()).isEmpty()
    }

    @Test
    fun `исправление объёма`() = runTest {
        repo.save(null, me, "250", now - min, now)
        val w = repo.observeToday(me, now).first().single()
        repo.save(w.id, me, "400", w.drunkAt, now)
        assertThat(repo.get(w.id)!!.ml).isEqualTo(400)
        assertThat(repo.observeToday(me, now).first()).hasSize(1)
    }
}
