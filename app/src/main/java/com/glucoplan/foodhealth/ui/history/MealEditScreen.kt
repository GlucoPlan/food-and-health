package com.glucoplan.foodhealth.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.ui.meal.MealEditor
import com.glucoplan.foodhealth.ui.meal.MealEditorEffects
import com.glucoplan.foodhealth.ui.meal.TotalsBar
import com.glucoplan.foodhealth.ui.picker.PickedItem

/** Открытый приём из истории (ТЗ 4.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(
    onClose: () -> Unit,
    onRepeated: () -> Unit,
    onAdd: (profileId: String?) -> Unit,
    onScan: () -> Unit,
    onCreateProduct: (barcode: String) -> Unit,
    picked: PickedItem?,
    scannedBarcode: String?,
    createdProductId: String?,
    onResultsHandled: () -> Unit,
    viewModel: MealEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val repeatPending by viewModel.repeatPending.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    MealEditorEffects(viewModel, picked, scannedBarcode, createdProductId, onResultsHandled, onCreateProduct)
    LaunchedEffect(result) {
        when (result) {
            MealEditResult.CLOSED, MealEditResult.MISSING -> onClose()
            MealEditResult.REPEATED -> onRepeated()
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приём пищи") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        bottomBar = {
            if (state.loaded) {
                TotalsBar(state.total) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::repeat, modifier = Modifier.weight(1f)) { Text("Повторить") }
                        Button(
                            onClick = viewModel::save,
                            enabled = state.rows.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Сохранить") }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) return@Scaffold
        MealEditor(viewModel, state, onAdd, onScan, allowNow = false, modifier = Modifier.padding(padding))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить приём?") },
            text = { Text("Он пропадёт из истории и итогов дня.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    if (repeatPending != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRepeat,
            title = { Text("Заменить набранный приём?") },
            text = { Text("На экране «Приём пищи» уже что-то набрано. Оно будет заменено составом этого приёма.") },
            confirmButton = { TextButton(onClick = viewModel::confirmRepeat) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRepeat) { Text("Отмена") } },
        )
    }
}
