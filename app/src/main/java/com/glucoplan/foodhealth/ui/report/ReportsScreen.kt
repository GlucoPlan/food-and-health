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
import com.glucoplan.foodhealth.data.report.LineStyle
import com.glucoplan.foodhealth.data.report.Report
import com.glucoplan.foodhealth.data.report.ReportLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))

/** Отчёты и анализ (ТЗ 17.10): отчёты владельца телефона. Пока — итоги дня. */
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий день")
                }
                Text(
                    dayTitle(state.date),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::next, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий день")
                }
            }
            Button(onClick = viewModel::refresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Сформировать сейчас")
                }
            }
            state.warning?.let { Muted("Синхронизация не удалась ($it): записи с этого телефона могли не попасть в отчёт") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.report?.let { ReportContent(it) }
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

@Composable
private fun ReportContent(report: Report) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.summary.forEach { ReportText(it) }
        }
    }
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

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
