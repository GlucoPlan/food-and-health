package com.glucoplan.foodhealth.data.pan

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Сохранение фото кастрюли: с камеры и из галереи, на телефоне без папки pan_photos. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanPhotosTest {

    private lateinit var context: Context
    private lateinit var photos: PanPhotos

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "pan_photos").deleteRecursively()
        File(context.cacheDir, "camera").deleteRecursively()
        photos = PanPhotos(context)
    }

    private fun writeJpeg(file: File, width: Int, height: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun size(file: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(file.path, it) }
        .let { it.outWidth to it.outHeight }

    @Test
    fun `фото из галереи сохраняется, даже если папки ещё нет`() = runTest {
        val source = File(context.cacheDir, "gallery.jpg")
        writeJpeg(source, 400, 300)

        val name = photos.importFromUri(Uri.fromFile(source))

        val saved = photos.file(name)
        assertThat(saved.exists()).isTrue()
        assertThat(size(saved)).isEqualTo(400 to 300)
    }

    @Test
    fun `снимок камеры сохраняется, временный файл удаляется`() = runTest {
        photos.cameraUri()
        val shot = File(context.cacheDir, "camera/capture.jpg")
        writeJpeg(shot, 200, 100)

        val name = photos.importCameraShot()

        assertThat(photos.file(name).exists()).isTrue()
        assertThat(shot.exists()).isFalse()
    }

    @Test
    fun `большое фото уменьшается до 1600 по длинной стороне`() = runTest {
        val source = File(context.cacheDir, "big.jpg")
        writeJpeg(source, 4000, 3000)

        val (w, h) = size(photos.file(photos.importFromUri(Uri.fromFile(source))))

        assertThat(maxOf(w, h)).isAtMost(1600)
        assertThat(maxOf(w, h)).isAtLeast(1590)
    }

    @Test
    fun `не картинка — понятная ошибка`() = runTest {
        val source = File(context.cacheDir, "text.jpg").apply { writeText("не картинка") }
        val error = runCatching { photos.importFromUri(Uri.fromFile(source)) }.exceptionOrNull()
        assertThat(error).isNotNull()
    }
}
