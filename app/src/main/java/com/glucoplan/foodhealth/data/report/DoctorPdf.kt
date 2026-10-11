package com.glucoplan.foodhealth.data.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Период PDF для врача (ТЗ 17.11): по умолчанию прошлый месяц, не длиннее, чем принимает сервер. */
object DoctorPdfPeriod {
    const val MAX_DAYS = 366

    fun default(today: LocalDate): Pair<LocalDate, LocalDate> {
        val first = today.withDayOfMonth(1).minusMonths(1)
        return first to first.plusMonths(1).minusDays(1)
    }

    /** Ошибка для показа или null, если период подходит. */
    fun error(first: LocalDate, last: LocalDate, today: LocalDate): String? = when {
        last < first -> "Конец периода раньше начала"
        first > today -> "Период ещё не наступил"
        ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS -> "Период — не больше $MAX_DAYS дней"
        else -> null
    }

    /** «Дочь_2026-09-01_2026-09-30.pdf»: имя видно врачу; символы, недопустимые в имени файла, — на «_». */
    fun fileName(profileName: String, first: LocalDate, last: LocalDate): String {
        val name = profileName.trim().replace(Regex("""[\\/:*?"<>|\s]+"""), "_").ifEmpty { "профиль" }
        return "${name}_${first}_$last.pdf"
    }
}

/** Отдаёт готовый PDF в «Поделиться» Android: через FileProvider из cacheDir/pdf, старые файлы удаляются. */
@Singleton
class DoctorPdfShare @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir get() = File(context.cacheDir, "pdf")

    /** false, если поделиться нечем (нет ни одного подходящего приложения). */
    fun share(fileName: String, pdf: ByteArray): Boolean {
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName).apply { writeBytes(pdf) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "PDF для врача").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
