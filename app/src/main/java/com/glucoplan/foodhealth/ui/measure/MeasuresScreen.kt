package com.glucoplan.foodhealth.ui.measure

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.PressureRecord
import com.glucoplan.foodhealth.data.measure.WeightRecord
import com.glucoplan.foodhealth.data.measure.WeightRepository
import com.glucoplan.foodhealth.ui.format.mealTime

/** Экран «Замеры» (ТЗ 15.4). Новый замер записан — [onRecorded] с текстом сообщения. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasuresScreen(
    onBack: () -> Unit,
    onRecorded: (message: String) -> Unit,
    viewModel: MeasuresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()

    LaunchedEffect(recorded) {
        recorded?.let {
            viewModel.onRecordedHandled()
            onRecorded(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Замеры") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Кому
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                state.profiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == state.profileId,
                        onClick = { viewModel.onProfileSelected(profile.id) },
                        label = { Text(profile.name) },
                    )
                }
            }

            // Вес и давление — первыми: вводятся чаще всего (15.4)
            MeasureCard(
                title = "Вес",
                icon = Icons.Filled.MonitorWeight,
                entries = state.weights.map { MeasureEntry(it.id, weightLine(it)) },
                onNew = { viewModel.newMeasure(MeasureKind.WEIGHT) },
                onEdit = { viewModel.edit(MeasureKind.WEIGHT, it) },
            )
            MeasureCard(
                title = "Давление и пульс",
                icon = Icons.Filled.Favorite,
                entries = state.pressures.map { MeasureEntry(it.id, pressureLine(it)) },
                onNew = { viewModel.newMeasure(MeasureKind.PRESSURE) },
                onEdit = { viewModel.edit(MeasureKind.PRESSURE, it) },
            )
        }
    }

    dialog?.let { d ->
        // Новый ключ — новый диалог с чистыми полями
        key(d.kind, d.editingId) {
            when (d.kind) {
                MeasureKind.WEIGHT -> MeasureEntryDialog(
                    title = if (d.editingId == null) "Вес" else "Исправить вес",
                    fields = listOf(
                        MeasureField(
                            WeightRepository.FIELD_KG, "Вес, кг",
                            placeholder = state.weights.firstOrNull()?.let { NumberText.format(it.kg, 1) },
                        ),
                    ),
                    initial = d.initial,
                    initialAt = d.initialAt,
                    editing = d.editingId != null,
                    hint = state.weights.firstOrNull()?.let { "Прошлый раз: ${weightLine(it)}" },
                    errors = d.errors,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onDismiss = viewModel::dismiss,
                )
                MeasureKind.PRESSURE -> MeasureEntryDialog(
                    title = if (d.editingId == null) "Давление" else "Исправить давление",
                    fields = listOf(
                        MeasureField(BloodPressureRepository.FIELD_SYSTOLIC, "Верхнее", integer = true),
                        MeasureField(BloodPressureRepository.FIELD_DIASTOLIC, "Нижнее", integer = true),
                        MeasureField(BloodPressureRepository.FIELD_PULSE, "Пульс", integer = true),
                    ),
                    initial = d.initial,
                    initialAt = d.initialAt,
                    editing = d.editingId != null,
                    hint = state.pressures.firstOrNull()?.let { "Прошлый раз: ${pressureLine(it)}" }
                        ?: "Пульс можно не указывать",
                    errors = d.errors,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onDismiss = viewModel::dismiss,
                )
            }
        }
    }
}

private fun weightLine(w: WeightRecord) = "${NumberText.format(w.kg, 1)} кг · ${mealTime(w.measuredAt)}"

private fun pressureLine(p: PressureRecord) = "${p.text} · ${mealTime(p.measuredAt)}"
