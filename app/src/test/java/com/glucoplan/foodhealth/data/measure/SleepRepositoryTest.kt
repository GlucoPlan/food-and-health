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

/** Сон (ТЗ 15.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SleepRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: SleepRepository
    private val now = 1_700_000_000_000
    private val min = 60_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        repo = SleepRepository(db.sleepDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `запись с оценкой и без, сообщение, ручной источник`() = runTest {
        assertThat(repo.save(null, "me", now - 455 * min, now, SleepQuality.GOOD, now))
            .isEqualTo(MeasureSave.Saved("Записано: сон 7 ч 35 мин"))
        repo.save(null, "me", now - 2000 * min, now - 1500 * min, null, now)

        val list = repo.observeRecent("me").first()
        assertThat(list.map { it.minutes }).containsExactly(455L, 500L).inOrder()
        assertThat(list[0].quality).isEqualTo(SleepQuality.GOOD)
        assertThat(list[1].quality).isNull()
        assertThat(db.sleepDao().getById(list[0].id)!!.source).isEqualTo(SleepRepository.SOURCE_MANUAL)
    }

    @Test
    fun `слишком короткий и длинный сон, пробуждение в будущем — ошибки`() = runTest {
        val short = repo.save(null, "me", now - 9 * min, now, null, now) as MeasureSave.Invalid
        assertThat(short.errors).containsKey(SleepRepository.FIELD_DURATION)
        val long = repo.save(null, "me", now - 20 * 60 * min - min, now, null, now) as MeasureSave.Invalid
        assertThat(long.errors).containsKey(SleepRepository.FIELD_DURATION)
        val future = repo.save(null, "me", now, now + 480 * min, null, now) as MeasureSave.Invalid
        assertThat(future.errors).containsKey(MeasureSave.TIME)
        assertThat(repo.observeRecent("me").first()).isEmpty()
    }

    @Test
    fun `границы 10 минут и 20 часов проходят`() = runTest {
        assertThat(repo.save(null, "me", now - 10 * min, now, null, now)).isInstanceOf(MeasureSave.Saved::class.java)
        assertThat(repo.save(null, "me", now - 1200 * min, now, null, now)).isInstanceOf(MeasureSave.Saved::class.java)
    }

    @Test
    fun `исправление сохраняет источник, удаление мягкое, профили не смешиваются`() = runTest {
        repo.save(null, "me", now - 455 * min, now, null, now)
        repo.save(null, "wife", now - 400 * min, now, null, now)
        val mine = repo.observeRecent("me").first().single()
        // Как будто запись пришла из Health Connect (2-9)
        db.sleepDao().upsert(db.sleepDao().getById(mine.id)!!.copy(source = SleepRepository.SOURCE_HEALTH_CONNECT, externalId = "hc-1"))

        repo.save(mine.id, "me", now - 470 * min, now, SleepQuality.BAD, now)
        val fixed = db.sleepDao().getById(mine.id)!!
        assertThat(SleepTime.minutes(fixed.asleepAt, fixed.wokeAt)).isEqualTo(470L)
        assertThat(fixed.source).isEqualTo(SleepRepository.SOURCE_HEALTH_CONNECT)
        assertThat(fixed.externalId).isEqualTo("hc-1")

        repo.delete(mine.id)
        assertThat(repo.observeRecent("me").first()).isEmpty()
        assertThat(repo.observeRecent("wife").first()).hasSize(1)
    }
}
