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
import java.time.LocalDate

/** История роста (ТЗ 15.3). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class HeightRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var repo: HeightRepository
    private val today = LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") })
        repo = HeightRepository(db.heightDao(), prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `история по датам, новые сверху, текущий — последний по дате`() = runTest {
        assertThat(repo.add("d", "128,5", LocalDate.of(2026, 3, 1), today)).isNull()
        assertThat(repo.add("d", "131", LocalDate.of(2026, 9, 1), today)).isNull()
        assertThat(repo.add("d", "125", LocalDate.of(2025, 9, 1), today)).isNull()
        assertThat(repo.add("other", "180", today, today)).isNull()

        val history = repo.observeHistory("d").first()
        assertThat(history.map { it.heightCm }).containsExactly(131.0, 128.5, 125.0).inOrder()
        assertThat(history.first().date).isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `рост вне границ и дата в будущем — ошибка, ничего не пишется`() = runTest {
        assertThat(repo.add("d", "29", today, today)).isNotNull()
        assertThat(repo.add("d", "251", today, today)).isNotNull()
        assertThat(repo.add("d", "много", today, today)).isNotNull()
        assertThat(repo.add("d", "130", today.plusDays(1), today)).isNotNull()
        assertThat(repo.observeHistory("d").first()).isEmpty()
    }

    @Test
    fun `удаление мягкое, служебные поля заполнены`() = runTest {
        repo.add("d", "130", today, today)
        val record = repo.observeHistory("d").first().single()
        val entity = db.heightDao().getById(record.id)!!
        assertThat(entity.deviceId).isEqualTo(prefs.deviceId())
        assertThat(entity.deleted).isFalse()

        repo.delete(record.id)
        assertThat(repo.observeHistory("d").first()).isEmpty()
        assertThat(db.heightDao().getById(record.id)!!.deleted).isTrue()
    }
}
