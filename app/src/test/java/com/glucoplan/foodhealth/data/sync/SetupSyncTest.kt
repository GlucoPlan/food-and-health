package com.glucoplan.foodhealth.data.sync

import com.glucoplan.foodhealth.data.profile.filledProfileForm
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.pan.PanForm
import com.glucoplan.foodhealth.data.profile.ProfileForm
import com.glucoplan.foodhealth.ui.navigation.RootDecision
import com.glucoplan.foodhealth.ui.navigation.RootState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Новый телефон, очистка базы и обновление (ТЗ 6.1, 8.2, 8.4). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SetupSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = FakeServer()
    private val phones = mutableListOf<Phone>()

    private suspend fun phone(name: String, connected: Boolean = true) =
        Phone(tmp.root, name, server).also { phones += it; if (connected) it.connect() }

    @After
    fun tearDown() = phones.forEach { it.close() }

    private suspend fun rootOf(p: Phone): RootState = RootDecision.decide(
        ownerId = p.prefs.ownerId.first(),
        profileIds = p.profiles.observeProfiles().first().map { it.id }.toSet(),
        configured = p.settings.currentConfig() != null,
        setupDone = p.prefs.setupDone.first(),
        syncInitialized = p.db.syncDao().state()?.initialized == true,
        downloadSkipped = false,
    )

    @Test
    fun `загрузка показывает прогресс от общего числа записей на сервере`() = runTest {
        val a = phone("a")
        repeat(5) { a.addProduct("П$it") }
        a.engine.sync()

        server.pageSize = 2
        val fresh = phone("fresh")
        val seen = mutableListOf<SyncProgress?>()
        server.duringSync = { seen += fresh.engine.progress.value }

        assertThat(fresh.engine.sync(withPhotos = false)).isEqualTo(SyncResult.Success(sent = 0, received = 5))
        assertThat(seen).containsExactly(SyncProgress(0, 5), SyncProgress(2, 5), SyncProgress(4, 5)).inOrder()
        assertThat(fresh.engine.progress.value).isNull()
    }

    @Test
    fun `загрузка без фото — фото догружаются следующей синхронизацией`() = runTest {
        val a = phone("a")
        val photo = "${UUID.randomUUID()}.jpg"
        File(a.photosDir, photo).writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3))
        a.pans.save(null, PanForm("Большая", "800", photo))
        a.engine.sync()

        val fresh = phone("fresh")
        fresh.engine.sync(withPhotos = false)
        assertThat(fresh.pans.observePans().first().single().photo).isEqualTo(photo)
        assertThat(File(fresh.photosDir, photo).exists()).isFalse()

        fresh.engine.sync()
        assertThat(File(fresh.photosDir, photo).exists()).isTrue()
    }

    @Test
    fun `оборванная загрузка продолжается с места обрыва`() = runTest {
        val a = phone("a")
        repeat(5) { a.addProduct("П$it") }
        a.engine.sync()

        server.pageSize = 2
        val fresh = phone("fresh")
        var calls = 0
        server.duringSync = { if (++calls == 3) throw SyncException.Network(java.io.IOException("обрыв")) }
        assertThat(fresh.engine.sync(withPhotos = false)).isInstanceOf(SyncResult.Failed::class.java)
        assertThat(fresh.activeNames()).hasSize(4)

        server.duringSync = null
        val before = server.records.size
        assertThat(fresh.engine.sync()).isEqualTo(SyncResult.Success(sent = 0, received = 1))
        assertThat(fresh.activeNames()).hasSize(5)
        assertThat(server.records).hasSize(before)
    }

    @Test
    fun `новый телефон — подключение, загрузка, выбор владельца`() = runTest {
        val a = phone("a")
        a.profiles.save(null, filledProfileForm("Рита"))
        a.engine.sync()

        val fresh = phone("fresh", connected = false)
        assertThat(rootOf(fresh)).isEqualTo(RootState.Connect)

        fresh.connect()
        fresh.prefs.setSetupDone()
        assertThat(rootOf(fresh)).isEqualTo(RootState.Download)

        fresh.engine.sync(withPhotos = false)
        assertThat(rootOf(fresh)).isEqualTo(RootState.NeedsOwner)
        val rita = fresh.profiles.observeProfiles().first().single()
        fresh.prefs.setOwner(rita.id)
        assertThat(rootOf(fresh)).isEqualTo(RootState.Ready)
    }

    @Test
    fun `база очищена (8_4) — всё загружается с сервера, владелец узнаётся сам`() = runTest {
        val a = phone("a")
        a.profiles.save(null, filledProfileForm("Я"))
        val me = a.profiles.observeProfiles().first().single().id
        a.prefs.setOwner(me)
        a.addProduct("Молоко")
        a.engine.sync()
        // Правка, не успевшая уйти на сервер, при очистке пропадёт — это ожидаемо (ТЗ 6.1)
        a.addProduct("Не отправлено")

        // Очистка базы (DatabaseGuard удаляет файл, Room создаёт пустую): настройки телефона те же,
        // таблицы, очередь и состояние синхронизации — как у новой базы
        val sql = a.db.openHelper.writableDatabase
        SyncTables.ALL.forEach { sql.execSQL("DELETE FROM `$it`") }
        sql.execSQL("DELETE FROM sync_outbox")
        sql.execSQL("UPDATE sync_state SET cursor = 0, initialized = 0 WHERE id = 1")
        val cleared = a
        assertThat(rootOf(cleared)).isEqualTo(RootState.Download)

        assertThat(cleared.engine.sync(withPhotos = false)).isInstanceOf(SyncResult.Success::class.java)
        assertThat(cleared.activeNames()).containsExactly("Молоко")
        assertThat(rootOf(cleared)).isEqualTo(RootState.Ready)
    }

    @Test
    fun `перед обновлением — сервер не настроен, обновляемся`() = runTest {
        val a = phone("a", connected = false)
        a.addProduct("Молоко")
        assertThat(PreUpdateSync(a.engine, a.db.syncDao()).run()).isEqualTo(PreUpdateResult.Proceed)
    }

    @Test
    fun `перед обновлением — синхронизация удалась, обновляемся`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        assertThat(PreUpdateSync(a.engine, a.db.syncDao()).run()).isEqualTo(PreUpdateResult.Proceed)
        assertThat(server.records).hasSize(1)
    }

    @Test
    fun `перед обновлением — не удалась, есть неотправленное — предупреждение`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        a.addProduct("Хлеб")
        server.failWith = { SyncException.Network(java.io.IOException("timeout")) }
        val result = PreUpdateSync(a.engine, a.db.syncDao()).run() as PreUpdateResult.Warn
        assertThat(result.pending).isEqualTo(2)
        assertThat(result.reason).contains("Нет связи с сервером")
    }

    @Test
    fun `перед обновлением — не удалась, но отправлять нечего — обновляемся`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        a.engine.sync()
        server.failWith = { SyncException.Network(java.io.IOException("timeout")) }
        assertThat(PreUpdateSync(a.engine, a.db.syncDao()).run()).isEqualTo(PreUpdateResult.Proceed)
    }
}
