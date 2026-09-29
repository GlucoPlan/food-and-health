package com.glucoplan.foodhealth.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.profile.ProfileValidation
import com.glucoplan.foodhealth.data.profile.Sex
import com.glucoplan.foodhealth.ui.profile.DateField
import com.glucoplan.foodhealth.ui.profile.SexChips
import java.time.LocalDate

/** Первый запуск (ТЗ 6.1 до этапа 1б): создать профили и выбрать владельца телефона. */
@Composable
fun OwnerSelectScreen(viewModel: OwnerSelectViewModel = hiltViewModel()) {
    val profiles by viewModel.profileList.collectAsStateWithLifecycle()
    val wasReset by viewModel.databaseWasReset.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Кто пользуется этим телефоном?", style = MaterialTheme.typography.headlineSmall)

            if (wasReset) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        "База данных была создана более новой версией приложения, " +
                            "поэтому её пришлось очистить. Создайте профили заново.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Text(
                if (profiles.isEmpty()) "Добавьте профили всех, кто будет вести записи."
                else "Выберите владельца телефона. Его профиль будет выбран по умолчанию при записи еды.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.select(profile) },
                    )
                }
            }

            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить профиль")
            }
        }
    }

    if (adding) {
        AddProfileDialog(viewModel = viewModel, onDone = { adding = false })
    }
}

/** Новый профиль: имя, пол, дата рождения (ТЗ 15.3). */
@Composable
private fun AddProfileDialog(viewModel: OwnerSelectViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf<Sex?>(null) }
    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var errors by remember { mutableStateOf<ProfileValidation.Invalid?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Новый профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errors = errors?.copy(nameError = null) },
                    label = { Text("Имя") },
                    singleLine = true,
                    isError = errors?.nameError != null,
                    supportingText = errors?.nameError?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                SexChips(sex, errors?.sexError) { sex = it; errors = errors?.copy(sexError = null) }
                DateField(
                    label = "Дата рождения",
                    date = birthDate,
                    error = errors?.birthDateError,
                    onPick = { birthDate = it; errors = errors?.copy(birthDateError = null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.add(name, sex, birthDate) { e -> if (e == null) onDone() else errors = e } }) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Отмена") } },
    )
}
