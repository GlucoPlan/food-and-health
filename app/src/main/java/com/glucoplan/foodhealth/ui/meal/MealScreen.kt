package com.glucoplan.foodhealth.ui.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.meal.MealField
import com.glucoplan.foodhealth.data.meal.Sd1
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.picker.PickedItem

/** Главный экран (ТЗ 4.1): кому, когда, позиции, итоги, «Записать». */
@Composable
fun MealScreen(
    onAdd: (profileId: String?) -> Unit,
    onScan: () -> Unit,
    onCreateProduct: (barcode: String) -> Unit,
    picked: PickedItem?,
    scannedBarcode: String?,
    createdProductId: String?,
    onResultsHandled: () -> Unit,
    onMeasure: (profileId: String?) -> Unit = {},
    notice: String? = null,
    onNoticeHandled: () -> Unit = {},
    viewModel: MealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    MealEditorEffects(viewModel, picked, scannedBarcode, createdProductId, onResultsHandled, onCreateProduct)
    LaunchedEffect(notice) {
        // В очередь сообщений, а не прямо в snackbar: сброс ключа отменил бы показ
        notice?.let { viewModel.showNotice(it); onNoticeHandled() }
    }
    // Ключ Unit: эффект не перезапускается, начатый показ сообщения ничто не отменяет
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.loaded) {
                TotalsBar(state, viewModel) {
                    Button(onClick = viewModel::record, enabled = state.rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Записать")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        MealEditor(viewModel, state, onAdd, onScan, allowNow = true, modifier = Modifier.padding(padding), onMeasure = onMeasure)
    }
}

/**
 * Итоги приёма и кнопки под ними. Для профиля с дневником СД1 (раздел 7) углеводы крупно,
 * рядом ХЕ (если включены в профиле), и необязательные поля сахара и дозы.
 */
@Composable
fun TotalsBar(state: MealUi, viewModel: MealEditorViewModel, buttons: @Composable () -> Unit) {
    val total = state.total
    val sd1 = state.sd1Profile
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sd1 == null) {
                Text("Итого: " + nutritionLine(total), style = MaterialTheme.typography.titleSmall)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Углеводы", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${NumberText.format(total.carbs, 0)} г",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (sd1.showXe) {
                        Sd1.xe(total.carbs, sd1.carbsPerXe)?.let {
                            Text("${NumberText.format(it, 1)} ХЕ", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Text(
                    "${NumberText.format(total.kcal, 0)} ккал · Б ${NumberText.format(total.protein, 1)} · " +
                        "Ж ${NumberText.format(total.fat, 1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sd1Field("Сахар", "ммоль/л", state.glucose, state.errors[MealField.GLUCOSE], viewModel::onGlucoseChange,
                        Modifier.weight(1f))
                    Sd1Field("Доза", "ед.", state.dose, state.errors[MealField.DOSE], viewModel::onDoseChange,
                        Modifier.weight(1f))
                }
            }
            buttons()
        }
    }
}

@Composable
private fun Sd1Field(
    label: String,
    suffix: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { msg -> @Composable { Text(msg) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
