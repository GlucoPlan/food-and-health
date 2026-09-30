package com.glucoplan.foodhealth.ui.measure

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.health.SleepImporter
import com.glucoplan.foodhealth.data.health.SleepSession
import com.glucoplan.foodhealth.data.health.SleepSource
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.BodyMeasureRepository
import com.glucoplan.foodhealth.data.measure.SleepRepository
import com.glucoplan.foodhealth.data.measure.WaterRepository
import com.glucoplan.foodhealth.data.measure.WeightRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Экран «Замеры», открытый из «Истории» на старой записи (ТЗ 15.4). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MeasuresFromHistoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var profiles: ProfileRepository
    private lateinit var weights: WeightRepository
    private lateinit var sleeps: SleepRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        profiles = ProfileRepository(db.profileDao(), prefs)
        weights = WeightRepository(db.weightDao(), prefs)
        sleeps = SleepRepository(db.sleepDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun vm(edit: String) = MeasuresViewModel(
        SavedStateHandle(mapOf(MeasuresViewModel.ARG_EDIT to edit, MeasuresViewModel.ARG_PROFILE to "me")),
        profiles, prefs, weights,
        BloodPressureRepository(db.bloodPressureDao(), prefs),
        sleeps,
        WaterRepository(db.waterDao(), weights, profiles, prefs),
        BodyMeasureRepository(db.bodyMeasureDao(), prefs),
        SleepImporter(NoHealthConnect, db.sleepDao(), sleeps, prefs),
    )

    /** Health Connect на «телефоне» нет. */
    private object NoHealthConnect : SleepSource {
        override fun available() = false
        override suspend fun hasPermission() = false
        override suspend fun sessions(from: Long, to: Long) = emptyList<SleepSession>()
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }

    @Test
    fun `старый вес (не из последних пяти) открывается и после правки экран закрывается`() = runTest {
        val day = 24 * 3_600_000L
        val now = System.currentTimeMillis()
        weights.save(null, "me", "90", null, now - 30 * day)
        val old = weights.observeRecent("me", 10).first().single()
        repeat(6) { weights.save(null, "me", "${84 + it}", null, now - it * day) }

        val vm = vm(MeasuresViewModel.editArg(MeasuresViewModel.KIND_WEIGHT, old.id))
        val dialog = vm.dialog.await { it != null }!!
        assertThat(dialog.editingId).isEqualTo(old.id)
        assertThat(dialog.initial).containsExactly("90")
        assertThat(vm.closed.value).isFalse()

        vm.save(listOf("89,5"), dialog.initialAt)
        vm.closed.await { it }
        assertThat(weights.get(old.id)!!.kg).isEqualTo(89.5)
        // Правка, а не новая запись: сообщения «Записано» и возврата к еде нет
        assertThat(vm.recorded.value).isNull()
    }

    @Test
    fun `отмена правки тоже возвращает в историю`() = runTest {
        weights.save(null, "me", "84", null)
        val w = weights.observeRecent("me").first().single()
        val vm = vm(MeasuresViewModel.editArg(MeasuresViewModel.KIND_WEIGHT, w.id))
        vm.dialog.await { it != null }
        vm.dismiss()
        assertThat(vm.closed.value).isTrue()
    }

    @Test
    fun `сон открывается по id, удаление закрывает экран`() = runTest {
        val now = System.currentTimeMillis()
        sleeps.save(null, "me", now - 480 * 60_000L, now - 60_000L, null)
        val s = sleeps.observeRecent("me").first().single()
        val vm = vm(MeasuresViewModel.editArg(MeasuresViewModel.KIND_SLEEP, s.id))
        assertThat(vm.sleepDialog.await { it != null }!!.editingId).isEqualTo(s.id)
        vm.deleteSleep()
        vm.closed.await { it }
        assertThat(sleeps.get(s.id)).isNull()
    }

    @Test
    fun `несуществующая запись — сразу назад`() = runTest {
        val vm = vm(MeasuresViewModel.editArg(MeasuresViewModel.KIND_WEIGHT, "нет"))
        vm.closed.await { it }
    }

    @Test
    fun `обхваты открываются по id — все девять полей, пустые пустыми`() = runTest {
        val body = BodyMeasureRepository(db.bodyMeasureDao(), prefs)
        body.save(null, "me", listOf("", "", "92", "98,5", "", "", "", "", ""), null)
        val r = body.observeRecent("me").first().single()
        val vm = vm(MeasuresViewModel.editArg(MeasuresViewModel.KIND_BODY, r.id))
        val d = vm.dialog.await { it != null }!!
        assertThat(d.kind).isEqualTo(MeasureKind.BODY)
        assertThat(d.initial).containsExactly("", "", "92", "98,5", "", "", "", "", "").inOrder()
    }
}
