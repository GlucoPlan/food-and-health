package com.glucoplan.foodhealth.data.report

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.sync.FakeServer
import com.glucoplan.foodhealth.data.sync.Phone
import com.glucoplan.foodhealth.data.sync.ServerConfig
import com.glucoplan.foodhealth.data.sync.SyncException
import com.glucoplan.foodhealth.ui.report.ReportDates
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

/** Отчёты с сервера (ТЗ 17.5, 17.10, 17.11): разбор ответа, «Сформировать сейчас», выбор дня, PDF для врача. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReportTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = FakeServer()
    private val phones = mutableListOf<Phone>()

    @After
    fun tearDown() = phones.forEach { it.close() }

    private val sample = """
        {"kind": "day", "date": "2026-09-29", "profile_id": "p", "profile_name": "Я",
         "title": "29 сентября, вторник", "incomplete": true, "empty": false,
         "summary": [
           {"spans": [{"text": "Возможно, данные неполные", "bold": false}], "style": "warn"},
           {"spans": [{"text": "Калории: ", "bold": false}, {"text": "1850", "bold": true},
                      {"text": " из 2050 (90 %)", "bold": false}], "style": "normal"},
           {"spans": [{"text": "Углеводы за день: 44 г", "bold": true}], "style": "big"}
         ],
         "sections": [
           {"title": "Приёмы пищи", "lines": [
             {"spans": [{"text": "08:00 — 120 ккал", "bold": true}], "style": "новый-вид"}
           ]}
         ]}
    """.trimIndent()

    /** Сервер отчётов: запоминает, сколько записей было на сервере синхронизации в момент запроса. */
    private inner class FakeReports(var error: SyncException? = null) : ReportBackend {
        var recordsAtRequest = -1
        var asked: Triple<ReportKind, String, LocalDate>? = null
        override suspend fun report(config: ServerConfig, kind: ReportKind, profileId: String, date: LocalDate): Report {
            error?.let { throw it }
            recordsAtRequest = server.records.size
            asked = Triple(kind, profileId, date)
            return ReportJson.parse(sample)
        }

        var pdfAsked: Triple<String, LocalDate, LocalDate>? = null
        override suspend fun doctorPdf(config: ServerConfig, profileId: String, first: LocalDate, last: LocalDate): ByteArray {
            error?.let { throw it }
            recordsAtRequest = server.records.size
            pdfAsked = Triple(profileId, first, last)
            return "%PDF-1.3".toByteArray()
        }
    }

    private suspend fun phone(connected: Boolean = true) =
        Phone(tmp.root, "a", server).also { phones += it; if (connected) it.connect() }

    @Test
    fun `разбор отчёта — жирные куски, виды строк, неизвестный вид как обычный`() {
        val report = ReportJson.parse(sample)
        assertThat(report.title).isEqualTo("29 сентября, вторник")
        assertThat(report.incomplete).isTrue()
        assertThat(report.empty).isFalse()
        assertThat(report.summary.map { it.style }).containsExactly(LineStyle.WARN, LineStyle.NORMAL, LineStyle.BIG).inOrder()
        val kcal = report.summary[1]
        assertThat(kcal.text).isEqualTo("Калории: 1850 из 2050 (90 %)")
        assertThat(kcal.spans.filter { it.bold }.map { it.text }).containsExactly("1850")
        assertThat(report.sections.single().title).isEqualTo("Приёмы пищи")
        assertThat(report.sections.single().lines.single().style).isEqualTo(LineStyle.NORMAL)
    }

    @Test
    fun `сформировать сейчас — сначала отправляются записи телефона`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        val reports = FakeReports()
        val repo = ReportRepository(reports, a.settings, a.engine)

        val result = repo.report(ReportKind.WEEK, "p", LocalDate.of(2026, 9, 29), syncFirst = true) as ReportResult.Ok
        assertThat(reports.recordsAtRequest).isEqualTo(1)
        assertThat(reports.asked).isEqualTo(Triple(ReportKind.WEEK, "p", LocalDate.of(2026, 9, 29)))
        assertThat(result.syncWarning).isNull()
    }

    @Test
    fun `простое открытие не синхронизирует`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        val reports = FakeReports()
        ReportRepository(reports, a.settings, a.engine).report(ReportKind.DAY, "p", LocalDate.of(2026, 9, 29), syncFirst = false)
        assertThat(reports.recordsAtRequest).isEqualTo(0)
    }

    @Test
    fun `синхронизация не удалась — отчёт всё равно запрашивается, с предупреждением`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        server.failWith = { SyncException.Http(500) }
        val result = ReportRepository(FakeReports(), a.settings, a.engine)
            .report(ReportKind.DAY, "p", LocalDate.of(2026, 9, 29), syncFirst = true) as ReportResult.Ok
        assertThat(result.syncWarning).isNotNull()
    }

    @Test
    fun `нет сети — понятное сообщение`() = runTest {
        val a = phone()
        val result = ReportRepository(FakeReports(SyncException.Network(IOException("timeout"))), a.settings, a.engine)
            .report(ReportKind.DAY, "p", LocalDate.of(2026, 9, 29), syncFirst = false)
        assertThat(result).isEqualTo(ReportResult.Failed("Отчёты доступны только при связи с сервером"))
    }

    @Test
    fun `сервер не настроен`() = runTest {
        val a = phone(connected = false)
        assertThat(ReportRepository(FakeReports(), a.settings, a.engine).report(ReportKind.DAY, "p", LocalDate.of(2026, 9, 29), true))
            .isEqualTo(ReportResult.NotConfigured)
    }

    @Test
    fun `день по умолчанию — вчера, вперёд не дальше сегодня`() {
        val today = LocalDate.of(2026, 9, 30)
        assertThat(ReportDates.default(ReportKind.DAY, today)).isEqualTo(LocalDate.of(2026, 9, 29))
        assertThat(ReportDates.canGoForward(ReportKind.DAY, LocalDate.of(2026, 9, 29), today)).isTrue()
        assertThat(ReportDates.canGoForward(ReportKind.DAY, today, today)).isFalse()
        assertThat(ReportDates.step(ReportKind.DAY, today, forward = false)).isEqualTo(LocalDate.of(2026, 9, 29))
    }

    @Test
    fun `неделя по умолчанию — прошлая полная, вперёд до текущей`() {
        val wednesday = LocalDate.of(2026, 9, 30)
        val lastMonday = LocalDate.of(2026, 9, 21)
        assertThat(ReportDates.default(ReportKind.WEEK, wednesday)).isEqualTo(lastMonday)
        assertThat(ReportDates.default(ReportKind.WEEK, LocalDate.of(2026, 9, 28))).isEqualTo(lastMonday)
        assertThat(ReportDates.step(ReportKind.WEEK, lastMonday, forward = true)).isEqualTo(LocalDate.of(2026, 9, 28))
        assertThat(ReportDates.canGoForward(ReportKind.WEEK, lastMonday, wednesday)).isTrue()
        assertThat(ReportDates.canGoForward(ReportKind.WEEK, LocalDate.of(2026, 9, 28), wednesday)).isFalse()
    }

    @Test
    fun `месяц по умолчанию — прошлый, листание не сбивает число`() {
        val today = LocalDate.of(2026, 9, 30)
        assertThat(ReportDates.default(ReportKind.MONTH, today)).isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(ReportDates.default(ReportKind.MONTH, LocalDate.of(2026, 1, 15))).isEqualTo(LocalDate.of(2025, 12, 1))
        // С 31 января — на 1 февраля и обратно на 1 января, а не на 28 или 31
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = ReportDates.step(ReportKind.MONTH, jan31, forward = true)
        assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(ReportDates.step(ReportKind.MONTH, feb, forward = false)).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(ReportDates.canGoForward(ReportKind.MONTH, LocalDate.of(2026, 8, 1), today)).isTrue()
        assertThat(ReportDates.canGoForward(ReportKind.MONTH, LocalDate.of(2026, 9, 1), today)).isFalse()
    }

    @Test
    fun `картинки отчёта разбираются, у дневного их нет`() {
        assertThat(ReportJson.parse(sample).images).isEmpty()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val body = sample.trimEnd().removeSuffix("}") +
            ""","images": [{"id": "weight", "title": "Вес", "png": "${java.util.Base64.getEncoder().encodeToString(png)}"}]}"""
        val image = ReportJson.parse(body).images.single()
        assertThat(image.id).isEqualTo("weight")
        assertThat(image.title).isEqualTo("Вес")
        assertThat(image.png.toList()).isEqualTo(png.toList())
    }

    @Test
    fun `анализ не листается и не формируется по кнопке`() {
        val today = LocalDate.of(2026, 9, 30)
        assertThat(ReportKind.ANALYSIS.hasPeriod).isFalse()
        assertThat(ReportKind.entries.filter { it.hasPeriod }).containsExactly(ReportKind.DAY, ReportKind.WEEK, ReportKind.MONTH)
        assertThat(ReportKind.ANALYSIS.code).isEqualTo("analysis")
        assertThat(ReportDates.canGoForward(ReportKind.ANALYSIS, today, today)).isFalse()
        assertThat(ReportDates.step(ReportKind.ANALYSIS, today, forward = false)).isEqualTo(today)
    }

    @Test
    fun `анализ с сервера разбирается как отчёт, в том числе пустой`() {
        val analysis = ReportJson.parse(
            """{"kind": "analysis", "date": "2026-09-29", "profile_id": "p", "title": "по данным за 29 сентября, вторник",
               "incomplete": false, "empty": false, "images": [],
               "summary": [{"spans": [{"text": "Белка мало, ", "bold": false}, {"text": "соли много", "bold": true}], "style": "normal"}],
               "sections": [{"title": "Что сделать", "lines": [{"spans": [{"text": "• Творог утром", "bold": false}], "style": "normal"}]}]}"""
        )
        assertThat(analysis.kind).isEqualTo("analysis")
        assertThat(analysis.summary.single().spans.last()).isEqualTo(ReportSpan("соли много", bold = true))
        assertThat(analysis.sections.single().lines.single().text).isEqualTo("• Творог утром")

        val empty = ReportJson.parse(
            """{"kind": "analysis", "date": "", "profile_id": "p", "title": "Анализа пока нет", "incomplete": false,
               "empty": true, "images": [], "sections": [],
               "summary": [{"spans": [{"text": "Claude разбирает данные ночью", "bold": false}], "style": "muted"}]}"""
        )
        assertThat(empty.empty).isTrue()
        assertThat(empty.summary.single().style).isEqualTo(LineStyle.MUTED)
    }

    @Test
    fun `PDF для врача — сначала отправляются записи телефона`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        val reports = FakeReports()
        val first = LocalDate.of(2026, 9, 1)
        val last = LocalDate.of(2026, 9, 30)
        val result = ReportRepository(reports, a.settings, a.engine).doctorPdf("kid", first, last) as PdfResult.Ok
        assertThat(reports.recordsAtRequest).isEqualTo(1)
        assertThat(reports.pdfAsked).isEqualTo(Triple("kid", first, last))
        assertThat(result.pdf.decodeToString()).startsWith("%PDF")
        assertThat(result.syncWarning).isNull()
    }

    @Test
    fun `PDF для врача — нет сети`() = runTest {
        val a = phone()
        val day = LocalDate.of(2026, 9, 1)
        assertThat(ReportRepository(FakeReports(SyncException.Network(IOException("timeout"))), a.settings, a.engine)
            .doctorPdf("kid", day, day)).isEqualTo(PdfResult.Failed("PDF доступен только при связи с сервером"))
    }

    @Test
    fun `PDF для врача — сервер не настроен`() = runTest {
        val a = phone(connected = false)
        val day = LocalDate.of(2026, 9, 1)
        assertThat(ReportRepository(FakeReports(), a.settings, a.engine).doctorPdf("kid", day, day))
            .isEqualTo(PdfResult.NotConfigured)
    }

    @Test
    fun `период PDF — по умолчанию прошлый месяц, проверки`() {
        assertThat(DoctorPdfPeriod.default(LocalDate.of(2026, 10, 11)))
            .isEqualTo(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30))
        assertThat(DoctorPdfPeriod.default(LocalDate.of(2026, 3, 1)))
            .isEqualTo(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28))
        assertThat(DoctorPdfPeriod.default(LocalDate.of(2026, 1, 20)))
            .isEqualTo(LocalDate.of(2025, 12, 1) to LocalDate.of(2025, 12, 31))

        val today = LocalDate.of(2026, 10, 11)
        assertThat(DoctorPdfPeriod.error(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), today)).isNull()
        assertThat(DoctorPdfPeriod.error(today, today, today)).isNull()
        assertThat(DoctorPdfPeriod.error(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), today))
            .isEqualTo("Конец периода раньше начала")
        assertThat(DoctorPdfPeriod.error(today.plusDays(1), today.plusDays(2), today)).isEqualTo("Период ещё не наступил")
        // 366 дней — можно, 367 — нет (как на сервере)
        assertThat(DoctorPdfPeriod.error(LocalDate.of(2025, 10, 11), today, today)).isNull()
        assertThat(DoctorPdfPeriod.error(LocalDate.of(2025, 10, 10), today, today)).isEqualTo("Период — не больше 366 дней")
    }

    @Test
    fun `имя файла PDF — имя профиля и период`() {
        val first = LocalDate.of(2026, 9, 1)
        val last = LocalDate.of(2026, 9, 30)
        assertThat(DoctorPdfPeriod.fileName("Дочь", first, last)).isEqualTo("Дочь_2026-09-01_2026-09-30.pdf")
        assertThat(DoctorPdfPeriod.fileName(" Маша / Петрова ", first, last)).isEqualTo("Маша_Петрова_2026-09-01_2026-09-30.pdf")
        assertThat(DoctorPdfPeriod.fileName("  ", first, last)).isEqualTo("профиль_2026-09-01_2026-09-30.pdf")
    }
}
