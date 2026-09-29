package com.glucoplan.foodhealth.ui.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.nutrition.Nutrition
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
    viewModel: MealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    MealEditorEffects(viewModel, picked, scannedBarcode, createdProductId, onResultsHandled, onCreateProduct)
    // Ключ Unit: эффект не перезапускается, начатый показ сообщения ничто не отменяет
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.loaded) {
                TotalsBar(state.total) {
                    Button(onClick = viewModel::record, enabled = state.rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Записать")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        MealEditor(viewModel, state, onAdd, onScan, allowNow = true, modifier = Modifier.padding(padding))
    }
}

/** Итоги приёма и кнопки под ними. */
@Composable
fun TotalsBar(total: Nutrition, buttons: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Итого: " + nutritionLine(total), style = MaterialTheme.typography.titleSmall)
            buttons()
        }
    }
}
