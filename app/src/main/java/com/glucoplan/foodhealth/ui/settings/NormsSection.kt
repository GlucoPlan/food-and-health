package com.glucoplan.foodhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.norms.Activity
import com.glucoplan.foodhealth.data.norms.DailyNorms
import com.glucoplan.foodhealth.data.norms.NormMissing
import kotlin.math.roundToInt

/** Диапазон сахара «от — до» (ТЗ 17.3): только у профиля с «Дневником СД1». */
@Composable
fun GlucoseRangeFields(
    low: String,
    high: String,
    error: String?,
    onLow: (String) -> Unit,
    onHigh: (String) -> Unit,
) {
    Column {
        Text("Целевой сахар, ммоль/л", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(low, onLow, "от", error != null, Modifier.weight(1f))
            NumberField(high, onHigh, "до", error != null, Modifier.weight(1f))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Нормы питания (ТЗ 17.3): активность и цель по весу (у взрослых), ручные нормы.
 * Под каждым полем нормы — расчётное значение: оно действует, пока поле пустое.
 */
@Composable
fun NormsSection(state: ProfileEditState, norms: DailyNorms?, viewModel: ProfileEditViewModel) {
    val form = state.form
    val child = norms?.child == true
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Нормы питания", style = MaterialTheme.typography.bodyLarge)
        if (child) {
            Hint("Для детей нормы берутся из таблиц МР 2.3.1.0253-21 по возрасту и полу")
        } else {
            Hint("Активность")
            // По два чипа в ряд: четыре в одну строку не помещаются на узком экране
            Activity.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { activity ->
                        FilterChip(
                            selected = activity == form.activity,
                            onClick = { viewModel.onActivityChange(activity) },
                            label = { Text(activity.label) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = form.targetWeightKg,
                onValueChange = viewModel::onTargetChange,
                label = { Text("Целевой вес, кг") },
                singleLine = true,
                isError = state.targetError != null,
                supportingText = { Text(state.targetError ?: "Пусто — поддерживать вес") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.targetWeightKg.isNotBlank()) {
                OutlinedTextField(
                    value = form.weightPaceKg,
                    onValueChange = viewModel::onPaceChange,
                    label = { Text("Темп, кг в неделю") },
                    singleLine = true,
                    isError = state.paceError != null,
                    supportingText = state.paceError?.let { msg -> @Composable { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Hint("Нормы в день. Пустое поле — расчёт")
        norms?.let(::missingText)?.let { Hint(it) }
        val auto = norms?.auto
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NormField(form.normKcal, viewModel::onNormKcalChange, "Калории", state.kcalError, auto?.kcal, Modifier.weight(1f))
            NormField(form.normProtein, viewModel::onNormProteinChange, "Белки, г", state.proteinError, auto?.protein, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NormField(form.normFat, viewModel::onNormFatChange, "Жиры, г", state.fatError, auto?.fat, Modifier.weight(1f))
            NormField(form.normCarbs, viewModel::onNormCarbsChange, "Углеводы, г", state.normCarbsError, auto?.carbs, Modifier.weight(1f))
        }
    }
}

/** «Для расчёта не хватает: рост, вес»; null — всего хватает. */
private fun missingText(norms: DailyNorms): String? = when {
    NormMissing.INFANT in norms.missing -> NormMissing.INFANT.label.replaceFirstChar { it.uppercase() }
    norms.missing.isEmpty() -> null
    else -> "Для расчёта не хватает: " + norms.missing.joinToString(", ") { it.label }
}

@Composable
private fun NormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    auto: Double?,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: auto?.let { "расчёт: ${it.roundToInt()}" } ?: "расчёта нет") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
