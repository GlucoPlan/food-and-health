package com.glucoplan.foodhealth.data.pan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Фото кастрюль в files/pan_photos. Снимки уменьшаются до [MAX_SIDE] px по длинной
 * стороне и поворачиваются по EXIF: так они меньше весят на телефоне и при синхронизации (1б).
 */
@Singleton
class PanPhotos @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cameraDir get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun file(name: String): File = file(context, name)

    /** Uri для системной камеры: снимок пишется во временный файл. */
    fun cameraUri(): Uri {
        val target = File(cameraDir, "capture.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    /** Обработать снимок камеры; возвращает имя файла фото. */
    suspend fun importCameraShot(): String {
        val shot = File(cameraDir, "capture.jpg")
        try {
            return import { shot.inputStream() }
        } finally {
            shot.delete()
        }
    }

    /** Обработать картинку из галереи; возвращает имя файла фото. */
    suspend fun importFromUri(uri: Uri): String = import {
        context.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
    }

    fun delete(name: String) {
        file(name).delete()
    }

    private suspend fun import(open: () -> java.io.InputStream): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("Файл не похож на картинку")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Не удалось прочитать картинку")

        val orientation = open().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val name = "${UUID.randomUUID()}.jpg"
        directory(context).mkdirs()
        file(name).outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (result !== decoded) result.recycle()
        decoded.recycle()
        name
    }

    companion object {
        private const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 85

        private fun directory(context: Context) = File(context.filesDir, "pan_photos")

        /** Файл фото по имени — для показа миниатюр. */
        fun file(context: Context, name: String): File = File(directory(context), name)
    }
}
