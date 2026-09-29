package com.glucoplan.foodhealth.data.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Очередь отправки заполняется сама, триггерами. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SyncTriggersTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var phone: Phone

    @After
    fun tearDown() = phone.close()

    @Test
    fun `новая запись и правка попадают в очередь, версия растёт`() = runTest {
        phone = Phone(tmp.root, "a", FakeServer())
        val id = phone.addProduct("Молоко")
        val first = phone.outbox().single()
        assertThat(first.tbl).isEqualTo("product")
        assertThat(first.id).isEqualTo(id)

        phone.rename(id, "Молоко 3,2%")
        val second = phone.outbox().single()
        assertThat(second.version).isGreaterThan(first.version)
    }

    @Test
    fun `пока применяются изменения с сервера, очередь не пополняется`() = runTest {
        phone = Phone(tmp.root, "a", FakeServer())
        val sql = phone.db.openHelper.writableDatabase
        sql.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
        phone.addProduct("Молоко")
        sql.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
        assertThat(phone.outbox()).isEmpty()
    }

    @Test
    fun `все синхронизируемые таблицы под триггерами`() = runTest {
        phone = Phone(tmp.root, "a", FakeServer())
        val sql = phone.db.openHelper.writableDatabase
        val triggers = sql.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        SyncTables.ALL.forEach { table ->
            assertThat(triggers).contains("sync_${table}_insert")
            assertThat(triggers).contains("sync_${table}_update")
        }
    }
}
