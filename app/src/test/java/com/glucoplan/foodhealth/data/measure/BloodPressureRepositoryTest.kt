package com.glucoplan.foodhealth.data.measure

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository.Companion.FIELD_DIASTOLIC
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository.Companion.FIELD_PULSE
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository.Companion.FIELD_SYSTOLIC
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

/** Давление и пульс (ТЗ 15.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BloodPressureRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: BloodPressureRepository
    private val now = 1_700_000_000_000
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        repo = BloodPressureRepository(db.bloodPressureDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun errors(sys: String, dia: String, pulse: String, at: Long? = null) =
        (repo.save(null, "me", sys, dia, pulse, at, now) as MeasureSave.Invalid).errors

    @Test
    fun `запись с пульсом и без`() = runTest {
        assertThat(repo.save(null, "me", "120", "80", "72", null, now)).isEqualTo(MeasureSave.Saved("Записано: 120/80, пульс 72"))
        assertThat(repo.save(null, "me", "118", "76", " ", now - hour, now)).isEqualTo(MeasureSave.Saved("Записано: 118/76"))
        val list = repo.observeRecent("me").first()
        assertThat(list.map { it.text }).containsExactly("120/80, пульс 72", "118/76").inOrder()
        assertThat(list[1].pulse).isNull()
    }

    @Test
    fun `границы и целые числа`() = runTest {
        assertThat(errors("49", "80", "")).containsKey(FIELD_SYSTOLIC)
        assertThat(errors("261", "80", "")).containsKey(FIELD_SYSTOLIC)
        assertThat(errors("120", "29", "")).containsKey(FIELD_DIASTOLIC)
        assertThat(errors("120", "80", "29")).containsKey(FIELD_PULSE)
        assertThat(errors("120", "80", "221")).containsKey(FIELD_PULSE)
        assertThat(errors("120,5", "80", "")).containsKey(FIELD_SYSTOLIC)
        assertThat(errors("", "", "")).containsAtLeast(FIELD_SYSTOLIC, "От 50 до 260", FIELD_DIASTOLIC, "От 30 до 160")
        assertThat(repo.observeRecent("me").first()).isEmpty()
    }

    @Test
    fun `верхнее не больше нижнего — перепутали поля`() = runTest {
        assertThat(errors("80", "120", "")[FIELD_DIASTOLIC]).isEqualTo("Нижнее должно быть меньше верхнего")
        assertThat(errors("100", "100", "")).containsKey(FIELD_DIASTOLIC)
    }

    @Test
    fun `время в будущем`() = runTest {
        assertThat(errors("120", "80", "", now + 24 * hour)).containsKey(MeasureSave.TIME)
    }

    @Test
    fun `исправление, мягкое удаление, профили не смешиваются`() = runTest {
        repo.save(null, "me", "120", "80", "72", null, now)
        repo.save(null, "wife", "110", "70", "", null, now)
        val mine = repo.observeRecent("me").first().single()

        repo.save(mine.id, "me", "125", "82", "", mine.measuredAt, now)
        assertThat(repo.get(mine.id)!!.text).isEqualTo("125/82")

        repo.delete(mine.id)
        assertThat(repo.observeRecent("me").first()).isEmpty()
        assertThat(db.bloodPressureDao().getById(mine.id)!!.deleted).isTrue()
        assertThat(repo.observeRecent("wife").first()).hasSize(1)
    }
}
