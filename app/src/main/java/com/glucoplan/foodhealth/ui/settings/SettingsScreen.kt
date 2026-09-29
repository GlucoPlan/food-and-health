package com.glucoplan.foodhealth.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileValidator
import com.glucoplan.foodhealth.update.DownloadState
import com.glucoplan.foodhealth.update.UpdateState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.glucoplan.foodhealth.data.sync.FirstSyncChoice
import com.glucoplan.foodhealth.ui.format.mealTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenProfile: (id: String?) -> Unit,
    onOpenPans: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    val profiles by viewModel.profileList.collectAsStateWithLifecycle()
    val ownerId by viewModel.ownerId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onOpened() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        OwnerCard(profiles, ownerId, onSelect = viewModel::setOwner)
        ProfilesCard(profiles, onOpenProfile)
        ServerCard(viewModel)
        SyncCard(viewModel)
        Card(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Кастрюли") },
                supportingContent = { Text("Название, вес и фото кастрюль для блюд") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onOpenPans),
            )
        }
        UpdateCard(
            state = update,
            onUpdate = viewModel::startUpdate,
            onCheck = viewModel::checkNow,
        )
    }
}

/** Сервер (ТЗ 4.6): адрес и ключ семьи, «Проверить соединение». */
@Composable
private fun ServerCard(viewModel: SettingsViewModel) {
    val server by viewModel.server.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сервер", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = server.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("Адрес") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = server.key,
                onValueChange = viewModel::onKeyChange,
                label = { Text("Ключ семьи") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Скрыть ключ" else "Показать ключ",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::saveAndCheck, enabled = !server.checking) {
                    Text("Проверить соединение")
                }
                if (server.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            server.checkMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (server.checkOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Синхронизация (ТЗ 4.6): последняя успешная, «Синхронизировать сейчас», ошибка. */
@Composable
private fun SyncCard(viewModel: SettingsViewModel) {
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val status by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    var confirmReplace by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Синхронизация", style = MaterialTheme.typography.titleMedium)
            if (!configured) {
                Text(
                    "Сервер не подключён: данные хранятся только на этом телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(status.lastSuccessAt?.let { "Последняя: ${mealTime(it)}" } ?: "Ещё не синхронизировалось")
            if (pending > 0) {
                Text(
                    "Не отправлено изменений: $pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            status.lastError?.let { error ->
                Text(
                    "Ошибка" + (status.lastErrorAt?.let { " (${mealTime(it)})" } ?: "") + ": $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.syncNow() }, enabled = !syncing) { Text("Синхронизировать сейчас") }
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }

    choice?.let { serverRecords ->
        AlertDialog(
            onDismissRequest = viewModel::dismissChoice,
            title = { Text("На сервере уже есть данные") },
            text = {
                Text(
                    "Записей на сервере: $serverRecords. На этом телефоне тоже есть данные.\n\n" +
                        "«Отправить мои» — объединить: записи телефона добавятся к серверным. Профили и продукты, " +
                        "заведённые на разных телефонах отдельно, окажутся дважды.\n\n" +
                        "«Заменить» — удалить данные этого телефона и загрузить с сервера."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.syncNow(FirstSyncChoice.MERGE) }) { Text("Отправить мои") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissChoice(); confirmReplace = true }) { Text("Заменить") }
            },
        )
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Удалить данные телефона?") },
            text = {
                Text(
                    "Всё, что записано на этом телефоне, будет удалено и заменено данными сервера. " +
                        "Владельца телефона нужно будет выбрать заново."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; viewModel.syncNow(FirstSyncChoice.REPLACE) }) {
                    Text("Заменить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Отмена") } },
        )
    }
}

/** Владелец этого телефона (ТЗ 2, 4.6). */
@Composable
private fun OwnerCard(profiles: List<Profile>, ownerId: String?, onSelect: (Profile) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val owner = profiles.firstOrNull { it.id == ownerId }

    Card(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Владелец этого телефона") },
            supportingContent = { Text(owner?.name ?: "Не выбран") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { choosing = true },
        )
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Владелец телефона") },
            text = {
                Column {
                    profiles.forEach { profile ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(profile); choosing = false },
                        ) {
                            RadioButton(selected = profile.id == ownerId, onClick = null)
                            Text(profile.name, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Закрыть") } },
        )
    }
}

/** Список профилей семьи (ТЗ 4.6). */
@Composable
private fun ProfilesCard(profiles: List<Profile>, onOpenProfile: (String?) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Профили",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            profiles.forEach { profile ->
                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = profileSummary(profile)?.let { msg -> @Composable { Text(msg) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenProfile(profile.id) },
                )
            }
            TextButton(
                onClick = { onOpenProfile(null) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Добавить профиль")
            }
        }
    }
}

private fun profileSummary(profile: Profile): String? = listOfNotNull(
    "Дневник СД1".takeIf { profile.sd1Enabled },
    "ХЕ по ${ProfileValidator.formatCarbs(profile.carbsPerXe)} г".takeIf { profile.showXe },
).joinToString(" · ").ifEmpty { null }

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
