package com.glucoplan.foodhealth.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProfileDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProfileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.profileDao()
    }

    @After
    fun tearDown() = db.close()

    private fun profile(id: String, name: String, deleted: Boolean = false) = ProfileEntity(
        id = id, name = name, sd1Enabled = false, showXe = false, carbsPerXe = 10.0,
        updatedAt = 1L, deleted = deleted, deviceId = "dev",
    )

    @Test
    fun `вставка и чтение`() = runTest {
        dao.upsert(profile("1", "Иван"))
        assertThat(dao.getById("1")?.name).isEqualTo("Иван")
    }

    @Test
    fun `upsert обновляет существующую запись`() = runTest {
        dao.upsert(profile("1", "Иван"))
        dao.upsert(profile("1", "Ваня").copy(sd1Enabled = true))
        val all = dao.getActive()
        assertThat(all).hasSize(1)
        assertThat(all.single().name).isEqualTo("Ваня")
        assertThat(all.single().sd1Enabled).isTrue()
    }

    @Test
    fun `мягко удалённые не попадают в список, но остаются в базе`() = runTest {
        dao.upsert(profile("1", "Иван"))
        dao.upsert(profile("2", "Мария", deleted = true))
        assertThat(dao.observeActive().first().map { it.id }).containsExactly("1")
        assertThat(dao.getActive().map { it.id }).containsExactly("1")
        assertThat(dao.getById("2")?.deleted).isTrue()
    }

    @Test
    fun `список отсортирован по имени`() = runTest {
        dao.upsert(profile("1", "Мария"))
        dao.upsert(profile("2", "Анна"))
        dao.upsert(profile("3", "Иван"))
        assertThat(dao.observeActive().first().map { it.name })
            .containsExactly("Анна", "Иван", "Мария").inOrder()
    }
}
