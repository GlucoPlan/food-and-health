package com.glucoplan.foodhealth.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class DatabaseGuardTest {

    private val name = "guard_test.db"
    private lateinit var context: Context
    private lateinit var guard: DatabaseGuard

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        guard = DatabaseGuard(context)
    }

    private fun createDatabase(version: Int) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.version = version }
    }

    @Test
    fun `needsReset только если база новее`() {
        assertThat(DatabaseGuard.needsReset(fileVersion = 2, appVersion = 1)).isTrue()
        assertThat(DatabaseGuard.needsReset(fileVersion = 1, appVersion = 1)).isFalse()
        assertThat(DatabaseGuard.needsReset(fileVersion = 1, appVersion = 2)).isFalse()
        assertThat(DatabaseGuard.needsReset(fileVersion = null, appVersion = 1)).isFalse()
    }

    @Test
    fun `база новее приложения удаляется и показывается предупреждение`() {
        createDatabase(version = 5)
        assertThat(guard.prepare(name, appVersion = 1)).isTrue()
        assertThat(context.getDatabasePath(name).exists()).isFalse()
        assertThat(guard.wasReset.value).isTrue()

        guard.dismissResetNotice()
        assertThat(guard.wasReset.value).isFalse()
    }

    @Test
    fun `база той же версии не трогается`() {
        createDatabase(version = 1)
        assertThat(guard.prepare(name, appVersion = 1)).isFalse()
        assertThat(context.getDatabasePath(name).exists()).isTrue()
        assertThat(guard.wasReset.value).isFalse()
    }

    @Test
    fun `старая база не трогается — её обновят миграции`() {
        createDatabase(version = 1)
        assertThat(guard.prepare(name, appVersion = 3)).isFalse()
        assertThat(context.getDatabasePath(name).exists()).isTrue()
    }

    @Test
    fun `нет файла — ничего не делаем`() {
        assertThat(guard.prepare(name, appVersion = 1)).isFalse()
        assertThat(guard.wasReset.value).isFalse()
    }
}
