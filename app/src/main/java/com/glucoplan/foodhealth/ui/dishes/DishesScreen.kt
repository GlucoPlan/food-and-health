package com.glucoplan.foodhealth.ui.dishes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.dish.DishSummary
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate

/** Вкладка «Блюда» (ТЗ 4.4). */
@Composable
fun DishesScreen(
    onOpenDish: (id: String?) -> Unit,
    onOpenPans: () -> Unit,
    viewModel: DishesViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenDish(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Новое блюдо")
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Поиск блюда") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenPans) {
                    Icon(Icons.Filled.SoupKitchen, contentDescription = "Кастрюли")
                }
            }

            when {
                !state.loaded -> Unit
                state.dishes.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        if (state.baseEmpty) "Блюд пока нет. Добавьте первое кнопкой «+»." else "Ничего не найдено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.dishes, key = { it.id }) { dish ->
                        DishRow(dish, onClick = { onOpenDish(dish.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DishRow(dish: DishSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(dish.name) },
        supportingContent = {
            Column {
                Text(nutritionLine(dish.per100) + " на 100 г")
                Text(
                    "Сварено ${shortDate(dish.lastCookedAt)}" + if (dish.byRawIngredients) " · по сырым ингредиентам" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
