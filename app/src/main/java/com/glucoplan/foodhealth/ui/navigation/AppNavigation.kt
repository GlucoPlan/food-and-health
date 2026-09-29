package com.glucoplan.foodhealth.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.glucoplan.foodhealth.ui.dishes.DishesScreen
import com.glucoplan.foodhealth.ui.history.HistoryScreen
import com.glucoplan.foodhealth.ui.meal.MealScreen
import com.glucoplan.foodhealth.ui.products.ProductsScreen
import com.glucoplan.foodhealth.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Meal("meal", "Приём пищи", Icons.Filled.Restaurant),
    History("history", "История", Icons.Filled.History),
    Products("products", "Продукты", Icons.Filled.ShoppingBasket),
    Dishes("dishes", "Блюда", Icons.Filled.RamenDining),
    Settings("settings", "Настройки", Icons.Filled.Settings),
}

/** Нижняя навигация по пяти вкладкам (ТЗ, раздел 4). [settingsBadge] — точка «есть обновление». */
@Composable
fun AppNavigation(settingsBadge: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BadgedBox(badge = { if (tab == Tab.Settings && settingsBadge) Badge() }) {
                                Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Meal.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Meal.route) { MealScreen() }
            composable(Tab.History.route) { HistoryScreen() }
            composable(Tab.Products.route) { ProductsScreen() }
            composable(Tab.Dishes.route) { DishesScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
        }
    }
}

/** Заглушка вкладки, которая появится в следующих подзадачах. */
@Composable
internal fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            "Скоро здесь будет экран",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
