package com.glucoplan.foodhealth.data.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.pan.PanForm
import com.glucoplan.foodhealth.data.pan.PanPhotos
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

/** Фото кастрюль между телефонами (ТЗ 4.5, 6). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PhotoSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = FakeServer()
    private val phones = mutableListOf<Phone>()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(100) { it.toByte() }

    private suspend fun phone(name: String) = Phone(tmp.root, name, server).also { phones += it; it.connect() }

    @After
    fun tearDown() = phones.forEach { it.close() }

    /** Кастрюля с фото, снятым на этом телефоне. Возвращает имя файла. */
    private suspend fun panWithPhoto(p: Phone, name: String = "Большая"): String {
        val photo = "${UUID.randomUUID()}.jpg"
        File(p.photosDir, photo).writeBytes(jpeg)
        p.pans.save(null, PanForm(name, "800", photo))
        return photo
    }

    @Test
    fun `фото уходит на сервер и приходит на другой телефон`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val photo = panWithPhoto(a)
        val before = PanPhotos.revision.value

        a.engine.sync()
        assertThat(server.photos.keys).containsExactly(photo.removeSuffix(".jpg"))

        b.engine.sync()
        val got = File(b.photosDir, photo)
        assertThat(got.readBytes()).isEqualTo(jpeg)
        assertThat(b.pans.observePans().first().single().photo).isEqualTo(photo)
        assertThat(PanPhotos.revision.value).isGreaterThan(before)
        assertThat(b.photosDir.listFiles()!!.map { it.name }).containsExactly(photo)
    }

    @Test
    fun `фото отправляется один раз`() = runTest {
        val a = phone("a")
        panWithPhoto(a)
        a.engine.sync()
        a.engine.sync()
        a.pans.observePans().first().single().let { a.pans.save(it.id, PanForm("Большая эмалированная", "800", it.photo)) }
        a.engine.sync()
        assertThat(server.photoUploads).isEqualTo(1)
    }

    @Test
    fun `фото ещё нет на сервере — пропускается, приходит в следующий раз`() = runTest {
        val a = phone("a")
        val b = phone("b")
        server.failPhotoUpload = { SyncException.Http(405) }
        val photo = panWithPhoto(a)

        // Запись кастрюли ушла, фото — нет: синхронизация A с ошибкой, но B получает кастрюлю
        assertThat(a.engine.sync()).isInstanceOf(SyncResult.Failed::class.java)
        assertThat(b.engine.sync()).isInstanceOf(SyncResult.Success::class.java)
        assertThat(b.pans.observePans().first().single().photo).isEqualTo(photo)
        assertThat(File(b.photosDir, photo).exists()).isFalse()

        server.failPhotoUpload = null
        a.engine.sync()
        b.engine.sync()
        assertThat(File(b.photosDir, photo).readBytes()).isEqualTo(jpeg)
    }

    @Test
    fun `кастрюля без фото и с чужим именем файла ничего не ломают`() = runTest {
        val a = phone("a")
        a.pans.save(null, PanForm("Без фото", "500"))
        a.pans.save(null, PanForm("Странная", "500", "../../etc/passwd"))
        assertThat(a.engine.sync()).isInstanceOf(SyncResult.Success::class.java)
        assertThat(server.photos).isEmpty()
    }

    @Test
    fun `имя файла — id на сервере`() {
        assertThat(PhotoSync.idOf("3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b.jpg")).isEqualTo("3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b")
        assertThat(PhotoSync.idOf("3f2a9c1e.jpg")).isNull()
        assertThat(PhotoSync.idOf("../x.jpg")).isNull()
        assertThat(PhotoSync.idOf("3F2A9C1E-7B4D-4E8A-9F10-2C3D4E5F6A7B.jpg")).isNull()
    }
}
