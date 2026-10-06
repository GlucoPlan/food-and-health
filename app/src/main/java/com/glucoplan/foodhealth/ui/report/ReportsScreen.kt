package com.glucoplan.foodhealth.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.glucoplan.foodhealth.data.report.LineStyle
import com.glucoplan.foodhealth.data.report.ReportImage
import com.glucoplan.foodhealth.data.report.ReportKind
import com.glucoplan.foodhealth.data.report.Report
import com.glucoplan.foodhealth.data.report.ReportLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
// LLLL — название месяца в именительном падеже: «сентябрь»
private val MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))

/** Отчёты и анализ (ТЗ 17.10): отчёты владельца телефона — итоги дня, недели и месяца, ночной анализ Claude. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                // Строку состояния уже учёл внешний Scaffold
                windowInsets = WindowInsets(0),
                title = { Text("Отчёты и анализ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = state.kind.ordinal) {
                ReportKind.entries.forEach { kind ->
                    Tab(
                        selected = kind == state.kind,
                        onClick = { viewModel.selectKind(kind) },
                        text = { Text(kind.label) },
                    )
                }
            }
            if (state.kind.hasPeriod) {
                PeriodControls(state, viewModel)
            } else {
                // Кнопки «Проанализировать сейчас» нет (ТЗ 17.9)
                Muted("Claude разбирает данные каждую ночь: вчерашний день, неделю и месяц. Здесь — последний анализ.")
                state.report?.takeIf { !it.empty }?.let {
                    Text(it.title.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
                }
            }
            state.warning?.let { Muted("Синхронизация не удалась ($it): записи с этого телефона могли не попасть в отчёт") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.kind.hasPeriod && state.loading) CircularProgressIndicator(Modifier.size(24.dp))
            state.report?.let { ReportContent(it) }
        }
    }
}

/** Период стрелками и «Сформировать сейчас». */
@Composable
private fun PeriodControls(state: ReportsState, viewModel: ReportsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previous) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
        }
        Text(
            when (state.kind) {
                ReportKind.DAY -> dayTitle(state.date)
                ReportKind.WEEK -> weekTitle(state.date)
                ReportKind.MONTH -> monthTitle(state.date)
                ReportKind.ANALYSIS -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::next, enabled = state.canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Вперёд")
        }
    }
    Button(onClick = viewModel::refresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Сформировать сейчас")
        }
    }
}

private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "Сегодня, "
        today.minusDays(1) -> "Вчера, "
        else -> ""
    }
    return prefix + DAY.format(date)
}

private fun weekTitle(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val thisWeek = ReportDates.weekStart(LocalDate.now())
    val prefix = when (monday) {
        thisWeek -> "Эта неделя, "
        thisWeek.minusWeeks(1) -> "Прошлая неделя, "
        else -> ""
    }
    val range = if (monday.month == sunday.month) {
        "${monday.dayOfMonth}–${SHORT_DATE.format(sunday)}"
    } else {
        "${SHORT_DATE.format(monday)} – ${SHORT_DATE.format(sunday)}"
    }
    return prefix + range
}

private fun monthTitle(first: LocalDate): String {
    val thisMonth = LocalDate.now().withDayOfMonth(1)
    val name = MONTH.format(first).replaceFirstChar { it.uppercase() }
    return when (first) {
        thisMonth -> "Этот месяц, $name"
        thisMonth.minusMonths(1) -> "Прошлый месяц, $name"
        else -> name
    }
}

@Composable
private fun ReportContent(report: Report) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.summary.forEach { ReportText(it) }
        }
    }
    report.images.forEach { ReportChart(it) }
    report.sections.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            section.lines.forEach { ReportText(it) }
        }
    }
}

/** Строка отчёта: жирные куски, крупные углеводы СД1, замечания цветом. */
@Composable
private fun ReportText(line: ReportLine) {
    val text = buildAnnotatedString {
        line.spans.forEach { span ->
            if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
        }
    }
    when (line.style) {
        LineStyle.BIG -> Text(text, style = MaterialTheme.typography.titleMedium)
        LineStyle.WARN -> Text(text, color = MaterialTheme.colorScheme.error)
        LineStyle.MUTED -> Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LineStyle.NORMAL -> Text(text)
    }
}

/** График с сервера (ТЗ 17.6). */
@Composable
private fun ReportChart(image: ReportImage) {
    val bitmap = remember(image) { BitmapFactory.decodeByteArray(image.png, 0, image.png.size)?.asImageBitmap() }
        ?: return
    Image(
        bitmap = bitmap,
        contentDescription = image.title,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
