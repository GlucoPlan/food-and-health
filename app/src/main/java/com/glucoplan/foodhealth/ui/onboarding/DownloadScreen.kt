package com.glucoplan.foodhealth.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Загрузка данных с сервера (ТЗ 6.1, п. 3): прогресс, при ошибке — повторить или продолжить. */
@Composable
fun DownloadScreen(viewModel: DownloadViewModel = hiltViewModel()) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val wasReset by viewModel.databaseWasReset.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Загрузка данных с сервера", style = MaterialTheme.typography.headlineSmall)

            if (wasReset) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        "База данных была создана более новой версией приложения, поэтому её пришлось очистить. " +
                            "Данные загружаются с сервера; пропадёт только то, что не успело синхронизироваться.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            val e = error
            if (e != null) {
                Text("Не удалось загрузить: $e", color = MaterialTheme.colorScheme.error)
                Text(
                    "Уже полученное сохранено. Можно повторить или продолжить — остальное загрузится в фоне, " +
                        "когда появится связь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::start) { Text("Повторить") }
                    OutlinedButton(onClick = viewModel::skip) { Text("Продолжить без загрузки") }
                }
            } else {
                val p = progress
                val total = p?.total
                if (p != null && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (p.received.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Получено ${p.received} из $total записей")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Соединение с сервером…")
                }
                Text(
                    "Фото кастрюль загрузятся в фоне после этого.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
