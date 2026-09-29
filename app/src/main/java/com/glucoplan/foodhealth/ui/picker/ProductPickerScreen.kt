package com.glucoplan.foodhealth.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.ui.format.nutritionLine
import com.glucoplan.foodhealth.ui.format.shortDate
import com.glucoplan.foodhealth.ui.products.nutritionLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerScreen(
    title: String,
    onPicked: (PickedItem) -> Unit,
    onCreateProduct: (barcode: String?) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    scannedBarcode: String?,
    onScannedHandled: () -> Unit,
    createdProductId: String?,
    onCreatedHandled: () -> Unit,
    viewModel: ProductPickerViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.onScanned(scannedBarcode)
            onScannedHandled()
        }
    }
    LaunchedEffect(createdProductId) {
        if (createdProductId != null) {
            viewModel.onProductCreated(createdProductId)
            onCreatedHandled()
        }
    }
    LaunchedEffect(event) {
        when (val e = event) {
            is PickerEvent.Picked -> { viewModel.onEventHandled(); onPicked(e.item) }
            is PickerEvent.CreateWithBarcode -> { viewModel.onEventHandled(); onCreateProduct(e.barcode) }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateProduct(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Новый продукт") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Название или штрихкод") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) {
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            val list = sections ?: return@Column
            if (list.all { it.rows.isEmpty() }) {
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    list.forEachIndexed { index, section ->
                        section.title?.let { title ->
                            item(key = "header$index") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(section.rows, key = { row -> "$index:" + rowKey(row) }) { row ->
                            PickerRowItem(row, onClick = {
                                onPicked(
                                    when (row) {
                                        is PickerRow.ProductRow -> PickedItem(MealItemType.PRODUCT, row.product.id)
                                        is PickerRow.DishRow -> PickedItem(MealItemType.DISH, row.dish.currentVersionId)
                                    }
                                )
                            })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun rowKey(row: PickerRow) = when (row) {
    is PickerRow.ProductRow -> "p" + row.product.id
    is PickerRow.DishRow -> "d" + row.dish.id
}

@Composable
private fun PickerRowItem(row: PickerRow, onClick: () -> Unit) {
    when (row) {
        is PickerRow.ProductRow -> ListItem(
            headlineContent = { Text(row.product.name) },
            overlineContent = row.product.brand?.let { b -> @Composable { Text(b) } },
            supportingContent = { Text(nutritionLine(row.product)) },
            modifier = Modifier.clickable(onClick = onClick),
        )
        is PickerRow.DishRow -> ListItem(
            headlineContent = { Text(row.dish.name) },
            overlineContent = { Text("Блюдо · сварено ${shortDate(row.dish.lastCookedAt)}") },
            supportingContent = { Text(nutritionLine(row.dish.per100)) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
