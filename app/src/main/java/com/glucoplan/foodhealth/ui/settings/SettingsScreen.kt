package com.glucoplan.foodhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.update.DownloadState
import com.glucoplan.foodhealth.update.UpdateState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val update by viewModel.update.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onOpened() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        UpdateCard(
            state = update,
            onUpdate = viewModel::startUpdate,
            onCheck = viewModel::checkNow,
        )
    }
}

/** Блок «Обновление» (ТЗ 8.2). */
@Composable
private fun UpdateCard(state: UpdateState, onUpdate: () -> Unit, onCheck: () -> Unit) {
    val download = state.download
    val downloading = download is DownloadState.Downloading

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Обновление", style = MaterialTheme.typography.titleMedium)

            Text("Установлена версия ${state.currentVersion}")
            Text(
                when {
                    state.updateAvailable -> "Доступна версия ${state.latest!!.version}"
                    state.latest != null -> "Доступна версия ${state.latest.version}: установлена последняя"
                    state.lastCheckedAt != null -> "Релизов пока нет"
                    else -> "Обновления ещё не проверялись"
                },
                color = if (state.updateAvailable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            state.lastCheckedAt?.let {
                Text(
                    "Проверено ${formatTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.checkError?.let {
                Text(
                    "Не удалось проверить: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.updateAvailable && state.latest!!.notes.isNotBlank()) {
                Text("Что нового", style = MaterialTheme.typography.labelLarge)
                Text(state.latest.notes, style = MaterialTheme.typography.bodyMedium)
            }

            when (download) {
                is DownloadState.Downloading -> {
                    val progress = download.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("Скачано ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Скачивание…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                DownloadState.NeedsPermission -> Text(
                    "Разрешите установку приложений из этого источника и нажмите «Обновить» ещё раз",
                    style = MaterialTheme.typography.bodySmall,
                )
                is DownloadState.Failed -> Text(
                    download.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                DownloadState.Idle -> Unit
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onUpdate, enabled = state.updateAvailable && !downloading) {
                    Text("Обновить")
                }
                OutlinedButton(onClick = onCheck, enabled = !state.checking && !downloading) {
                    Text("Проверить сейчас")
                }
                if (state.checking) {
                    Spacer(Modifier.size(4.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(millis))
