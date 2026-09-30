package com.glucoplan.foodhealth.ui.measure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.data.measure.WaterNorm
import com.glucoplan.foodhealth.data.measure.WaterRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Вода (ТЗ 15.4): «сегодня 1,2 из 2,5 л», «+ 250 мл» — запись одним нажатием,
 * «Другой объём» — диалог, сегодняшние записи — правка.
 */
@Composable
fun WaterCard(
    today: List<WaterRecord>,
    normMl: Int?,
    lastVolume: Int,
    onQuickAdd: () -> Unit,
    onOther: () -> Unit,
    onEdit: (id: String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.LocalDrink, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Вода", style = MaterialTheme.typography.titleMedium)
                    Text(
                        WaterNorm.today(today.sumOf { it.ml }, normMl).replaceFirstChar { it.uppercase() } +
                            if (normMl == null) " · норма появится после записи веса" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onQuickAdd) { Text("+ $lastVolume мл") }
                TextButton(onClick = onOther) { Text("Другой объём") }
            }
        }
        if (today.isNotEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(vertical = 4.dp)) {
                today.forEach { w ->
                    Text(
                        "${w.ml} мл · ${HM.format(Instant.ofEpochMilli(w.drunkAt).atZone(ZoneId.systemDefault()))}",
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(w.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
