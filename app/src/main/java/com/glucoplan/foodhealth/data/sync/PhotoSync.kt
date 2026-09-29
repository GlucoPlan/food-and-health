package com.glucoplan.foodhealth.data.sync

import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.pan.PanPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Фото кастрюль (ТЗ 6): загружаются отдельным запросом, а ссылка — поле photo записи кастрюли —
 * синхронизируется вместе с записью. Имя файла `<uuid>.jpg` одинаково на всех телефонах,
 * UUID — это id фото на сервере.
 */
class PhotoSync(
    private val db: AppDatabase,
    private val dir: File,
    private val settings: SyncSettings,
    private val backend: SyncBackend,
) {
    data class Result(val uploaded: Int, val downloaded: Int)

    suspend fun run(config: ServerConfig): Result {
        val names = withContext(Dispatchers.IO) { referencedPhotos() }
        val uploadedBefore = settings.uploadedPhotos()
        var uploaded = 0
        var downloaded = 0
        for (name in names) {
            val id = idOf(name) ?: continue
            val file = File(dir, name)
            if (file.isFile) {
                if (name !in uploadedBefore) {
                    backend.uploadPhoto(config, id, withContext(Dispatchers.IO) { file.readBytes() })
                    settings.markPhotoUploaded(name)
                    uploaded++
                }
            } else {
                // 404 — другой телефон ещё не отправил фото, попробуем в следующий раз
                val bytes = backend.downloadPhoto(config, id) ?: continue
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val part = File(dir, "$name.part")
                    part.writeBytes(bytes)
                    part.renameTo(file)
                }
                settings.markPhotoUploaded(name)
                downloaded++
            }
        }
        if (downloaded > 0) PanPhotos.onPhotosDownloaded()
        return Result(uploaded, downloaded)
    }

    /** Фото всех кастрюль, в том числе удалённых: их показывают сохранённые варки. */
    private fun referencedPhotos(): List<String> =
        db.openHelper.writableDatabase.query("SELECT DISTINCT photo FROM pan WHERE photo IS NOT NULL").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    companion object {
        private val NAME = Regex("^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.jpg$")

        /** id фото на сервере из имени файла; null — имя не наше, такое фото не передаётся. */
        fun idOf(name: String): String? = NAME.matchEntire(name)?.groupValues?.get(1)
    }
}
