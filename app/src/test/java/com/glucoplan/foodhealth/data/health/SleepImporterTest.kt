package com.glucoplan.foodhealth.data.health

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.measure.SleepQuality
import com.glucoplan.foodhealth.data.measure.SleepRepository
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

/** Импорт сна владельцу телефона (ТЗ 15.5). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SleepImporterTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeSource : SleepSource {
        var available = true
        var permission = true
        var sessions = listOf<SleepSession>()
        var requestedFrom = 0L
        override fun available() = available
        override suspend fun hasPermission() = permission
        override suspend fun sessions(from: Long, to: Long): List<SleepSession> {
            requestedFrom = from
            return sessions.filter { it.end > from && it.start < to }
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var sleeps: SleepRepository
    private val source = FakeSource()
    private lateinit var importer: SleepImporter

    private val h = 3_600_000L
    private val now = 1_700_000_000_000

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        sleeps = SleepRepository(db.sleepDao(), prefs)
        importer = SleepImporter(source, db.sleepDao(), sleeps, prefs)
        prefs.setOwner("me")
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `ночи импортируются владельцу с пометкой источника, без оценки`() = runTest {
        source.sessions = listOf(
            SleepSession("a", now - 30 * h, now - 27 * h),
            SleepSession("b", now - (26.5 * h).toLong(), now - 23 * h),  // перерыв полчаса — склеится с «a»
            SleepSession("c", now - 8 * h, now - h),
        )
        assertThat(importer.run(now)).isEqualTo(2)

        val list = sleeps.observeRecent("me").first()
        assertThat(list).hasSize(2)
        assertThat(list.all { it.source == SleepRepository.SOURCE_HEALTH_CONNECT }).isTrue()
        assertThat(list.all { it.quality == null }).isTrue()
        assertThat(db.sleepDao().getById(list.last().id)!!.externalId).isEqualTo("a")
        assertThat(sleeps.observeRecent("other").first()).isEmpty()
        // Окно — последние 14 дней
        assertThat(source.requestedFrom).isEqualTo(now - 14 * 24 * h)
    }

    @Test
    fun `повторный импорт не создаёт дублей, исправленное не затирается`() = runTest {
        source.sessions = listOf(SleepSession("c", now - 8 * h, now - h))
        importer.run(now)
        val r = sleeps.observeRecent("me").first().single()
        sleeps.save(r.id, "me", r.asleepAt + h, r.wokeAt, SleepQuality.GOOD, now)

        assertThat(importer.run(now + h)).isEqualTo(0)
        val after = sleeps.observeRecent("me").first().single()
        assertThat(after.quality).isEqualTo(SleepQuality.GOOD)
        assertThat(after.asleepAt).isEqualTo(r.asleepAt + h)
    }

    @Test
    fun `удалённая импортированная ночь не возвращается`() = runTest {
        source.sessions = listOf(SleepSession("c", now - 8 * h, now - h))
        importer.run(now)
        sleeps.delete(sleeps.observeRecent("me").first().single().id)
        assertThat(importer.run(now + h)).isEqualTo(0)
        assertThat(sleeps.observeRecent("me").first()).isEmpty()
    }

    @Test
    fun `ночь уже записана руками — не дублируется`() = runTest {
        sleeps.save(null, "me", now - 8 * h, now - 30 * 60_000L, null, now)
        source.sessions = listOf(SleepSession("c", now - 8 * h + 600_000, now - h))
        assertThat(importer.run(now)).isEqualTo(0)
        assertThat(sleeps.observeRecent("me").first().single().source).isEqualTo(SleepRepository.SOURCE_MANUAL)
    }

    @Test
    fun `нет Health Connect, разрешения или владельца — ничего`() = runTest {
        source.sessions = listOf(SleepSession("c", now - 8 * h, now - h))
        source.permission = false
        assertThat(importer.run(now)).isNull()
        source.permission = true
        source.available = false
        assertThat(importer.run(now)).isNull()
        assertThat(sleeps.observeRecent("me").first()).isEmpty()
    }

    @Test
    fun `при запуске — не чаще раза в час`() = runTest {
        source.sessions = listOf(SleepSession("c", now - 8 * h, now - h))
        assertThat(importer.runIfDue(now)).isEqualTo(1)
        source.sessions = source.sessions + SleepSession("d", now - 32 * h, now - 25 * h)
        assertThat(importer.runIfDue(now + 30 * 60_000L)).isNull()
        assertThat(importer.runIfDue(now + h)).isEqualTo(1)
    }
}
