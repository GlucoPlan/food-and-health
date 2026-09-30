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

/** Отчёты с сервера (ТЗ 17.5, 17.10): разбор ответа, «Сформировать сейчас», выбор дня. */
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
        var asked: Pair<String, LocalDate>? = null
        override suspend fun day(config: ServerConfig, profileId: String, date: LocalDate): Report {
            error?.let { throw it }
            recordsAtRequest = server.records.size
            asked = profileId to date
            return ReportJson.parse(sample)
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

        val result = repo.day("p", LocalDate.of(2026, 9, 29), syncFirst = true) as ReportResult.Ok
        assertThat(reports.recordsAtRequest).isEqualTo(1)
        assertThat(reports.asked).isEqualTo("p" to LocalDate.of(2026, 9, 29))
        assertThat(result.syncWarning).isNull()
    }

    @Test
    fun `простое открытие не синхронизирует`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        val reports = FakeReports()
        ReportRepository(reports, a.settings, a.engine).day("p", LocalDate.of(2026, 9, 29), syncFirst = false)
        assertThat(reports.recordsAtRequest).isEqualTo(0)
    }

    @Test
    fun `синхронизация не удалась — отчёт всё равно запрашивается, с предупреждением`() = runTest {
        val a = phone()
        a.addProduct("Молоко")
        server.failWith = { SyncException.Http(500) }
        val result = ReportRepository(FakeReports(), a.settings, a.engine)
            .day("p", LocalDate.of(2026, 9, 29), syncFirst = true) as ReportResult.Ok
        assertThat(result.syncWarning).isNotNull()
    }

    @Test
    fun `нет сети — понятное сообщение`() = runTest {
        val a = phone()
        val result = ReportRepository(FakeReports(SyncException.Network(IOException("timeout"))), a.settings, a.engine)
            .day("p", LocalDate.of(2026, 9, 29), syncFirst = false)
        assertThat(result).isEqualTo(ReportResult.Failed("Отчёты доступны только при связи с сервером"))
    }

    @Test
    fun `сервер не настроен`() = runTest {
        val a = phone(connected = false)
        assertThat(ReportRepository(FakeReports(), a.settings, a.engine).day("p", LocalDate.of(2026, 9, 29), true))
            .isEqualTo(ReportResult.NotConfigured)
    }

    @Test
    fun `день по умолчанию — вчера, вперёд не дальше сегодня`() {
        val today = LocalDate.of(2026, 9, 30)
        assertThat(ReportDates.default(today)).isEqualTo(LocalDate.of(2026, 9, 29))
        assertThat(ReportDates.canGoForward(LocalDate.of(2026, 9, 29), today)).isTrue()
        assertThat(ReportDates.canGoForward(today, today)).isFalse()
    }
}
