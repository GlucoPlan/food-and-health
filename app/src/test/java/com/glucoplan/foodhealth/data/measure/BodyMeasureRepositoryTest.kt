package com.glucoplan.foodhealth.data.measure

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

/** Обхваты тела (ТЗ 15.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BodyMeasureRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: BodyMeasureRepository
    private val now = 1_700_000_000_000

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        repo = BodyMeasureRepository(db.bodyMeasureDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** Поля в порядке ТЗ: шея, грудь, талия, живот, бёдра, бедро, голень, плечо, запястье. */
    private fun texts(vararg pairs: Pair<BodyPart, String>) = BodyPart.entries.map { p -> pairs.toMap()[p].orEmpty() }

    @Test
    fun `все девять — сообщение с числом заполненных`() = runTest {
        val all = listOf("40", "105", "92", "98", "104", "60", "40", "35", "18")
        assertThat(repo.save(null, "me", all, null, now)).isEqualTo(MeasureSave.Saved("Записано: обхваты (9)"))
        val r = repo.observeRecent("me").first().single()
        assertThat(r.values).hasSize(9)
        assertThat(r.text).startsWith("шея 40, грудь 105, талия 92")
    }

    @Test
    fun `пропуски допустимы, округление до 0,1`() = runTest {
        val r = repo.save(null, "me", texts(BodyPart.WAIST to "92,34", BodyPart.BELLY to "98.25"), null, now)
        assertThat(r).isEqualTo(MeasureSave.Saved("Записано: обхваты (2)"))
        val rec = repo.observeRecent("me").first().single()
        assertThat(rec.values).containsExactly(BodyPart.WAIST, 92.3, BodyPart.BELLY, 98.3)
        assertThat(rec.text).isEqualTo("талия 92,3, живот 98,3")
    }

    @Test
    fun `пустой замер целиком и значения вне границ — ошибки`() = runTest {
        val empty = repo.save(null, "me", texts(), null, now) as MeasureSave.Invalid
        assertThat(empty.errors).containsKey(BodyMeasureRepository.FIELD_ALL)
        val bad = repo.save(null, "me", texts(BodyPart.WAIST to "4", BodyPart.HIPS to "251", BodyPart.ARM to "много"), null, now)
            as MeasureSave.Invalid
        assertThat(bad.errors.keys).containsExactly("waist", "hips", "arm")
        assertThat(bad.errors).doesNotContainKey(BodyMeasureRepository.FIELD_ALL)
        val future = repo.save(null, "me", texts(BodyPart.WAIST to "92"), now + 3_600_000, now) as MeasureSave.Invalid
        assertThat(future.errors).containsKey(MeasureSave.TIME)
        assertThat(repo.observeRecent("me").first()).isEmpty()
    }

    @Test
    fun `исправление — очищенное поле очищается, удаление мягкое`() = runTest {
        repo.save(null, "me", texts(BodyPart.WAIST to "92", BodyPart.BELLY to "98"), null, now)
        val r = repo.observeRecent("me").first().single()
        repo.save(r.id, "me", texts(BodyPart.WAIST to "91,5"), r.measuredAt, now)
        assertThat(repo.get(r.id)!!.values).containsExactly(BodyPart.WAIST, 91.5)

        repo.delete(r.id)
        assertThat(repo.observeRecent("me").first()).isEmpty()
        assertThat(db.bodyMeasureDao().getById(r.id)!!.deleted).isTrue()
    }

    @Test
    fun `профили не смешиваются`() = runTest {
        repo.save(null, "me", texts(BodyPart.WAIST to "92"), null, now)
        repo.save(null, "wife", texts(BodyPart.WAIST to "70"), null, now)
        assertThat(repo.observeAll("me").first().single().values[BodyPart.WAIST]).isEqualTo(92.0)
    }
}
