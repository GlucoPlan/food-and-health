package com.glucoplan.foodhealth.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.product.PieceWeight
import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.ui.format.grams
import com.glucoplan.foodhealth.ui.format.nutritionLine

/**
 * Ввод веса сразу после выбора (ТЗ 4.1): цифровая клавиатура открыта сразу.
 * Если задан [pieceWeightG], можно ввести количество штук; тогда в [onConfirm] приходят и штуки.
 * [per100] — КБЖУ на 100 г, для подсказки под полем.
 */
@Composable
fun WeightDialog(
    title: String,
    pieceWeightG: Double?,
    per100: Nutrition,
    onConfirm: (grams: Double, pieces: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var byPieces by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val value = NumberText.parse(text)?.takeIf { it > 0 }
    val pieces = value.takeIf { byPieces && pieceWeightG != null }
    val gramsValue = when {
        value == null -> null
        pieces != null -> PieceWeight.toGrams(pieces, pieceWeightG!!)
        else -> value
    }
    val confirm = { if (gramsValue != null) onConfirm(gramsValue, pieces) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pieceWeightG != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !byPieces, onClick = { byPieces = false }, label = { Text("Граммы") })
                        FilterChip(
                            selected = byPieces,
                            onClick = { byPieces = true },
                            label = { Text("Штуки по ${grams(pieceWeightG)}") },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (byPieces) "Количество, шт" else "Вес, г") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.focusRequester(focus),
                )
                // Внутри диалога: поле уже в композиции, клавиатура откроется сразу
                LaunchedEffect(Unit) { focus.requestFocus() }
                if (gramsValue != null) {
                    Text(
                        (if (pieces != null) "${grams(gramsValue)} · " else "") +
                            nutritionLine(per100.scaled(gramsValue / 100.0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { confirm() }, enabled = gramsValue != null) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Ввод веса продукта: граммы или штуки. */
@Composable
fun WeightDialog(product: Product, onConfirm: (grams: Double, pieces: Double?) -> Unit, onDismiss: () -> Unit) =
    WeightDialog(product.name, product.pieceWeightG, Nutrition.per100(product), onConfirm, onDismiss)
