package com.glucoplan.foodhealth.ui.pans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.ui.format.grams

/** Список кастрюль (ТЗ 4.5). Открывается из «Блюд» и из настроек. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PansScreen(onOpenPan: (id: String?) -> Unit, onBack: () -> Unit, viewModel: PansViewModel = hiltViewModel()) {
    val pans by viewModel.pans.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кастрюли") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenPan(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Новая кастрюля")
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val list = pans ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "Кастрюль пока нет. Добавьте кнопкой «+», чтобы приложение вычитало их вес из веса блюда.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(list, key = { it.id }) { pan ->
                ListItem(
                    headlineContent = { Text(pan.name) },
                    supportingContent = { Text(grams(pan.weightG)) },
                    leadingContent = { PanThumbnail(pan.photo, size = 56.dp) },
                    modifier = Modifier.clickable { onOpenPan(pan.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
