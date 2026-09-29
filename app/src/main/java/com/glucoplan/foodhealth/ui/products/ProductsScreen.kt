package com.glucoplan.foodhealth.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.data.product.ProductSort

@Composable
fun ProductsScreen(
    onOpenProduct: (id: String?) -> Unit,
    onNewProductWithBarcode: (barcode: String) -> Unit,
    onScan: () -> Unit,
    scannedBarcode: String?,
    onScannedHandled: () -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createWithBarcode by viewModel.createWithBarcode.collectAsStateWithLifecycle()

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.onScanned(scannedBarcode)
            onScannedHandled()
        }
    }
    LaunchedEffect(createWithBarcode) {
        createWithBarcode?.let {
            viewModel.onCreateWithBarcodeHandled()
            onNewProductWithBarcode(it)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenProduct(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Новый продукт")
            }
        },
        // Отступы системных панелей учтены внешним Scaffold с нижней навигацией
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query.text,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Название или штрихкод") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (query.text.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                            }
                        }
                        IconButton(onClick = onScan) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать штрихкод")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ProductSort.entries.forEach { sort ->
                    val selected = query.sort == sort
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onSortClick(sort) },
                        label = { Text(sort.label) },
                        trailingIcon = if (selected) {
                            @Composable {
                                Icon(
                                    if (query.ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                    contentDescription = if (query.ascending) "по возрастанию" else "по убыванию",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else null,
                    )
                }
            }

            when {
                !state.loaded -> Unit
                state.products.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(
                        if (state.baseEmpty) "Продуктов пока нет. Добавьте первый кнопкой «+»."
                        else "Ничего не найдено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.products, key = { it.id }) { product ->
                        ProductRow(product, onClick = { onOpenProduct(product.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(product.name) },
        overlineContent = product.brand?.let { brand -> @Composable { Text(brand) } },
        supportingContent = { Text(nutritionLine(product)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** «250 ккал · Б 10 · Ж 5 · У 40 · ГИ 55» на 100 г. */
internal fun nutritionLine(p: Product): String = buildString {
    append(NumberText.format(p.kcal, maxFraction = 0)).append(" ккал")
    append(" · Б ").append(NumberText.format(p.protein, maxFraction = 1))
    append(" · Ж ").append(NumberText.format(p.fat, maxFraction = 1))
    append(" · У ").append(NumberText.format(p.carbs, maxFraction = 1))
    p.gi?.let { append(" · ГИ ").append(it) }
}
