package com.glucoplan.foodhealth.data.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** Кусок строки отчёта; [bold] — выделить. */
data class ReportSpan(val text: String, val bold: Boolean)

/** Вид строки: крупно (углеводы СД1), замечание, второстепенное. Неизвестный — обычная. */
enum class LineStyle(val code: String) {
    NORMAL("normal"), BIG("big"), WARN("warn"), MUTED("muted");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

data class ReportLine(val spans: List<ReportSpan>, val style: LineStyle = LineStyle.NORMAL) {
    val text: String get() = spans.joinToString("") { it.text }
}

data class ReportSection(val title: String, val lines: List<ReportLine>)

/** График (ТЗ 17.6): PNG, пришедший в ответе сервера. */
class ReportImage(val id: String, val title: String, val png: ByteArray)

/** Вид отчёта; [code] — путь на сервере /reports/{code}. */
enum class ReportKind(val code: String, val label: String) { DAY("day", "День"), WEEK("week", "Неделя"), MONTH("month", "Месяц") }

/**
 * Отчёт, собранный сервером (ТЗ 17.5, 17.6): выжимка, подробности, графики.
 * Сервер строит его заново по каждому запросу, телефон его не хранит.
 */
data class Report(
    val kind: String,
    val date: String,
    val title: String,
    val incomplete: Boolean,
    val empty: Boolean,
    val summary: List<ReportLine>,
    val sections: List<ReportSection>,
    val images: List<ReportImage> = emptyList(),
)

object ReportJson {

    fun parse(body: String): Report {
        val root = Json.parseToJsonElement(body).jsonObject
        return Report(
            kind = root.string("kind"),
            date = root.string("date"),
            title = root.string("title"),
            incomplete = root["incomplete"]?.jsonPrimitive?.booleanOrNull ?: false,
            empty = root["empty"]?.jsonPrimitive?.booleanOrNull ?: false,
            summary = root.getValue("summary").jsonArray.map { line(it.jsonObject) },
            sections = root.getValue("sections").jsonArray.map { s ->
                val section = s.jsonObject
                ReportSection(section.string("title"), section.getValue("lines").jsonArray.map { line(it.jsonObject) })
            },
            images = root["images"]?.jsonArray.orEmpty().map { i ->
                val image = i.jsonObject
                ReportImage(image.string("id"), image.string("title"), Base64.getDecoder().decode(image.string("png")))
            },
        )
    }

    private fun line(json: JsonObject) = ReportLine(
        spans = json.getValue("spans").jsonArray.map { s ->
            val span = s.jsonObject
            ReportSpan(span.string("text"), span.getValue("bold").jsonPrimitive.boolean)
        },
        style = LineStyle.fromCode(json["style"]?.jsonPrimitive?.content),
    )

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
