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

/** Вес (ТЗ 15.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class WeightRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: WeightRepository
    private val now = 1_700_000_000_000
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        repo = WeightRepository(db.weightDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `запись — округление до 0,1, сообщение, служебные поля`() = runTest {
        val r = repo.save(null, "me", "84,24", null, now)
        assertThat(r).isEqualTo(MeasureSave.Saved("Записано: 84,2 кг"))
        val w = repo.latest("me")!!
        assertThat(w.kg).isEqualTo(84.2)
        assertThat(w.measuredAt).isEqualTo(now)
        val entity = db.weightDao().getById(w.id)!!
        assertThat(entity.deviceId).isEqualTo(prefs.deviceId())
        assertThat(entity.updatedAt).isEqualTo(now)
    }

    @Test
    fun `прошедшее время, последние новыми сверху, профили не смешиваются`() = runTest {
        repo.save(null, "me", "85", now - 48 * hour, now)
        repo.save(null, "me", "84,6", now - 24 * hour, now)
        repo.save(null, "me", "84,9", now - 36 * hour, now)
        repo.save(null, "daughter", "31", now, now)

        assertThat(repo.observeRecent("me").first().map { it.kg }).containsExactly(84.6, 84.9, 85.0).inOrder()
        assertThat(repo.latest("me")!!.kg).isEqualTo(84.6)
        assertThat(repo.latest("daughter")!!.kg).isEqualTo(31.0)
    }

    @Test
    fun `последних не больше пяти`() = runTest {
        repeat(7) { repo.save(null, "me", "${80 + it}", now - it * hour, now) }
        assertThat(repo.observeRecent("me").first()).hasSize(5)
    }

    @Test
    fun `ошибки — ничего не пишется`() = runTest {
        val r = repo.save(null, "me", "1", now + 24 * hour, now) as MeasureSave.Invalid
        assertThat(r.valueError).isNotNull()
        assertThat(r.timeError).isNotNull()
        assertThat(repo.latest("me")).isNull()
    }

    @Test
    fun `исправление и мягкое удаление`() = runTest {
        repo.save(null, "me", "84,2", now - hour, now)
        val w = repo.latest("me")!!
        repo.save(w.id, "me", "82,4", now - 2 * hour, now)
        val fixed = repo.get(w.id)!!
        assertThat(fixed.kg).isEqualTo(82.4)
        assertThat(fixed.measuredAt).isEqualTo(now - 2 * hour)
        assertThat(repo.observeRecent("me").first()).hasSize(1)

        repo.delete(w.id)
        assertThat(repo.latest("me")).isNull()
        assertThat(db.weightDao().getById(w.id)!!.deleted).isTrue()
    }
}
