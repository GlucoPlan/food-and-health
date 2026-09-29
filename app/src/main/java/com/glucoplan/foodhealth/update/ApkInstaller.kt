package com.glucoplan.foodhealth.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Скачивание APK из релиза и передача его системному установщику. */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val dir get() = File(context.cacheDir, "updates")

    /** Уже скачанный целиком APK этой версии, если есть. */
    fun downloaded(version: String): File? = apkFile(version).takeIf { it.exists() }

    /**
     * Скачивает APK в cacheDir/updates. [onProgress] получает долю от 0 до 1
     * или null, если размер неизвестен. Файлы прошлых версий удаляются.
     */
    suspend fun download(
        url: String,
        version: String,
        expectedSize: Long,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = apkFile(version)
        val part = File(dir, target.name + ".part")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize.takeIf { it > 0 }
            var done = 0L
            var lastReported = -1
            onProgress(if (total != null) 0f else null)
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            if (total != null && done != total) {
                part.delete()
                throw IOException("файл скачался не полностью")
            }
        }
        if (!part.renameTo(target)) throw IOException("не удалось сохранить файл")
        target
    }

    /** Можно ли приложению ставить APK (Android 8+ спрашивает разрешение отдельно). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Открывает системный экран «Установка из этого источника» для нашего приложения. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Запускает системную установку. false, если установщик не открылся. */
    fun install(apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun apkFile(version: String) = File(dir, "FoodHealth_$version.apk")
}
