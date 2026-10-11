package com.glucoplan.foodhealth.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.ui.profile.DateField

/** Период и, если профилей с СД1 несколько, чей PDF (ТЗ 17.11). Готовый файл уходит в «Поделиться». */
@Composable
fun DoctorPdfDialogView(state: DoctorPdfDialog, profiles: List<Profile>, viewModel: DoctorPdfViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text("PDF для врача") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profiles.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { p ->
                            FilterChip(
                                selected = p.id == state.profileId,
                                onClick = { viewModel.selectProfile(p.id) },
                                label = { Text(p.name) },
                            )
                        }
                    }
                } else {
                    profiles.firstOrNull()?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
                }
                DateField("С", state.first, error = null, onPick = viewModel::setFirst)
                DateField("По", state.last, error = null, onPick = viewModel::setLast)
                Text(
                    "Приёмы пищи по дням с углеводами, сахаром и дозами; за период — углеводы по времени суток, " +
                        "сахар, вес и рост.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.warning?.let {
                    Text(
                        "Синхронизация не удалась ($it): записи с этого телефона могли не попасть в PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::create, enabled = !state.loading) {
                if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Сформировать")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss, enabled = !state.loading) { Text("Закрыть") }
        },
    )
}
