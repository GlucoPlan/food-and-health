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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glucoplan.foodhealth.ui.dishes.DishEditScreen
import com.glucoplan.foodhealth.ui.dishes.DishEditViewModel
import com.glucoplan.foodhealth.ui.dishes.DishesScreen
import com.glucoplan.foodhealth.ui.history.HistoryScreen
import com.glucoplan.foodhealth.ui.history.MealEditScreen
import com.glucoplan.foodhealth.ui.history.MealEditViewModel
import com.glucoplan.foodhealth.ui.meal.MealScreen
import com.glucoplan.foodhealth.ui.measure.MeasuresScreen
import com.glucoplan.foodhealth.ui.measure.MeasuresViewModel
import com.glucoplan.foodhealth.ui.pans.PanEditScreen
import com.glucoplan.foodhealth.ui.pans.PanEditViewModel
import com.glucoplan.foodhealth.ui.pans.PansScreen
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.ui.picker.PickedItem
import com.glucoplan.foodhealth.ui.picker.ProductPickerScreen
import com.glucoplan.foodhealth.ui.picker.ProductPickerViewModel
import com.glucoplan.foodhealth.ui.products.ProductEditScreen
import com.glucoplan.foodhealth.ui.products.ProductEditViewModel
import com.glucoplan.foodhealth.ui.products.ProductsScreen
import com.glucoplan.foodhealth.ui.scanner.ScannerScreen
import com.glucoplan.foodhealth.ui.report.ReportsScreen
import com.glucoplan.foodhealth.ui.settings.ProfileEditScreen
import com.glucoplan.foodhealth.ui.settings.ProfileEditViewModel
import com.glucoplan.foodhealth.ui.settings.SettingsScreen

private const val PRODUCTS_LIST = "products/list"
private const val PRODUCT_EDIT_BASE = "products/edit"
private const val SCANNER = "scanner"

/** Ключ, под которым сканер кладёт код в savedStateHandle вызвавшего экрана. */
private const val SCANNED_BARCODE = "scanned_barcode"

private const val HISTORY_LIST = "history/list"
private const val HISTORY_MEAL_BASE = "history/meal"

private const val DISHES_LIST = "dishes/list"
private const val DISH_EDIT_BASE = "dishes/edit"

/** Выбор продукта для состава блюда (потом и для приёма пищи). */
private const val PICKER = "picker"

/** Ключ: что выбрано (PickedItem строкой) — кладёт выбор продукта вызвавшему экрану. */
private const val PICKED_ITEM = "picked_item"

/** Ключ: id продукта, только что созданного из выбора продукта. */
private const val CREATED_PRODUCT_ID = "created_product_id"

private const val PANS = "pans"

/** Экран «Замеры» (ТЗ 15.4). */
private const val MEASURES = "measures"

/** Ключ: сообщение для экрана приёма пищи с другого экрана. */
private const val NOTICE = "notice"
private const val PAN_EDIT_BASE = "pans/edit"

private const val SETTINGS_MAIN = "settings/main"
private const val PROFILE_EDIT_BASE = "settings/profile"
private const val REPORTS = "settings/reports"

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

    // Сканер — на весь экран, без нижней панели
    val fullScreen = destination?.route == SCANNER

    Scaffold(
        bottomBar = {
            if (!fullScreen) NavigationBar {
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
            composable(Tab.Meal.route) { entry ->
                val results = entry.mealResults()
                val notice by entry.savedStateHandle.getStateFlow<String?>(NOTICE, null).collectAsStateWithLifecycle()
                MealScreen(
                    onMeasure = { profileId ->
                        navController.navigate(
                            MEASURES + (profileId?.let { "?${MeasuresViewModel.ARG_PROFILE}=$it" } ?: "")
                        )
                    },
                    notice = notice,
                    onNoticeHandled = { entry.savedStateHandle[NOTICE] = null },
                    onAdd = { profileId -> navController.navigate(mealPickerRoute(profileId)) },
                    onScan = { navController.navigate(SCANNER) },
                    onCreateProduct = { code ->
                        navController.navigate("$PRODUCT_EDIT_BASE?${ProductEditViewModel.ARG_BARCODE}=$code")
                    },
                    picked = results.picked,
                    scannedBarcode = results.scanned,
                    createdProductId = results.created,
                    onResultsHandled = results.clear,
                )
            }
            navigation(startDestination = HISTORY_LIST, route = Tab.History.route) {
                composable(HISTORY_LIST) {
                    HistoryScreen(
                        onOpenMeal = { id -> navController.navigate("$HISTORY_MEAL_BASE?id=$id") },
                        onOpenMeasure = { kind, id, profileId ->
                            navController.navigate(
                                "$MEASURES?${MeasuresViewModel.ARG_EDIT}=${MeasuresViewModel.editArg(kind, id)}" +
                                    (profileId?.let { "&${MeasuresViewModel.ARG_PROFILE}=$it" } ?: "")
                            )
                        },
                    )
                }
                composable(
                    route = "$HISTORY_MEAL_BASE?${MealEditViewModel.ARG_ID}={${MealEditViewModel.ARG_ID}}",
                    arguments = listOf(optionalIdArgument(MealEditViewModel.ARG_ID)),
                ) { entry ->
                    val results = entry.mealResults()
                    MealEditScreen(
                        onClose = { navController.popBackStack() },
                        onRepeated = {
                            // Закрыть приём, чтобы при возврате в историю был список, и открыть «Приём пищи»
                            navController.popBackStack()
                            navController.navigate(Tab.Meal.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onAdd = { profileId -> navController.navigate(mealPickerRoute(profileId)) },
                        onScan = { navController.navigate(SCANNER) },
                        onCreateProduct = { code ->
                            navController.navigate("$PRODUCT_EDIT_BASE?${ProductEditViewModel.ARG_BARCODE}=$code")
                        },
                        picked = results.picked,
                        scannedBarcode = results.scanned,
                        createdProductId = results.created,
                        onResultsHandled = results.clear,
                    )
                }
            }
            navigation(startDestination = PRODUCTS_LIST, route = Tab.Products.route) {
                composable(PRODUCTS_LIST) { entry ->
                    val scanned by entry.scannedBarcode()
                    ProductsScreen(
                        onOpenProduct = { id ->
                            navController.navigate(if (id == null) PRODUCT_EDIT_BASE else "$PRODUCT_EDIT_BASE?id=$id")
                        },
                        onNewProductWithBarcode = { code ->
                            navController.navigate("$PRODUCT_EDIT_BASE?${ProductEditViewModel.ARG_BARCODE}=$code")
                        },
                        onScan = { navController.navigate(SCANNER) },
                        scannedBarcode = scanned,
                        onScannedHandled = { entry.savedStateHandle[SCANNED_BARCODE] = null },
                    )
                }
                composable(
                    route = "$PRODUCT_EDIT_BASE?" +
                        "${ProductEditViewModel.ARG_ID}={${ProductEditViewModel.ARG_ID}}&" +
                        "${ProductEditViewModel.ARG_BARCODE}={${ProductEditViewModel.ARG_BARCODE}}",
                    arguments = listOf(
                        optionalIdArgument(ProductEditViewModel.ARG_ID),
                        optionalIdArgument(ProductEditViewModel.ARG_BARCODE),
                    ),
                ) { entry ->
                    val scanned by entry.scannedBarcode()
                    ProductEditScreen(
                        onDone = { createdId ->
                            // Новый продукт, созданный из выбора продукта, сразу выбирается там
                            if (createdId != null) {
                                navController.previousBackStackEntry?.savedStateHandle?.set(CREATED_PRODUCT_ID, createdId)
                            }
                            navController.popBackStack()
                        },
                        onScan = { navController.navigate(SCANNER) },
                        scannedBarcode = scanned,
                        onScannedHandled = { entry.savedStateHandle[SCANNED_BARCODE] = null },
                    )
                }
            }
            navigation(startDestination = DISHES_LIST, route = Tab.Dishes.route) {
                composable(DISHES_LIST) {
                    DishesScreen(
                        onOpenDish = { id ->
                            navController.navigate(if (id == null) DISH_EDIT_BASE else "$DISH_EDIT_BASE?id=$id")
                        },
                        onOpenPans = { navController.navigate(PANS) },
                    )
                }
                composable(
                    route = "$DISH_EDIT_BASE?${DishEditViewModel.ARG_ID}={${DishEditViewModel.ARG_ID}}",
                    arguments = listOf(optionalIdArgument(DishEditViewModel.ARG_ID)),
                ) { entry ->
                    val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_ITEM, null)
                        .collectAsStateWithLifecycle()
                    DishEditScreen(
                        onDone = { navController.popBackStack() },
                        onAddProduct = { navController.navigate(PICKER) },
                        // В состав блюда выбираются только продукты
                        pickedProductId = picked?.let(PickedItem::decode)
                            ?.takeIf { it.type == MealItemType.PRODUCT }?.id,
                        onPickedHandled = { entry.savedStateHandle[PICKED_ITEM] = null },
                    )
                }
            }
            composable(
                route = "$PICKER?${ProductPickerViewModel.ARG_MODE}={${ProductPickerViewModel.ARG_MODE}}&" +
                    "${ProductPickerViewModel.ARG_PROFILE}={${ProductPickerViewModel.ARG_PROFILE}}",
                arguments = listOf(
                    optionalIdArgument(ProductPickerViewModel.ARG_MODE),
                    optionalIdArgument(ProductPickerViewModel.ARG_PROFILE),
                ),
            ) { entry ->
                val scanned by entry.scannedBarcode()
                val created by entry.savedStateHandle.getStateFlow<String?>(CREATED_PRODUCT_ID, null)
                    .collectAsStateWithLifecycle()
                val forMeal = entry.arguments?.getString(ProductPickerViewModel.ARG_MODE) == ProductPickerViewModel.MODE_MEAL
                ProductPickerScreen(
                    title = if (forMeal) "Добавить в приём" else "Выбор продукта",
                    onPicked = { item ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_ITEM, item.encode())
                        navController.popBackStack()
                    },
                    onCreateProduct = { barcode ->
                        navController.navigate(
                            if (barcode == null) PRODUCT_EDIT_BASE
                            else "$PRODUCT_EDIT_BASE?${ProductEditViewModel.ARG_BARCODE}=$barcode"
                        )
                    },
                    onScan = { navController.navigate(SCANNER) },
                    onBack = { navController.popBackStack() },
                    scannedBarcode = scanned,
                    onScannedHandled = { entry.savedStateHandle[SCANNED_BARCODE] = null },
                    createdProductId = created,
                    onCreatedHandled = { entry.savedStateHandle[CREATED_PRODUCT_ID] = null },
                )
            }
            composable(
                route = "$MEASURES?${MeasuresViewModel.ARG_PROFILE}={${MeasuresViewModel.ARG_PROFILE}}&" +
                    "${MeasuresViewModel.ARG_EDIT}={${MeasuresViewModel.ARG_EDIT}}",
                arguments = listOf(
                    optionalIdArgument(MeasuresViewModel.ARG_PROFILE),
                    optionalIdArgument(MeasuresViewModel.ARG_EDIT),
                ),
            ) {
                MeasuresScreen(
                    onBack = { navController.popBackStack() },
                    onRecorded = { message ->
                        // ТЗ 15.4: после записи — сразу к еде, сообщение показывает экран приёма пищи
                        navController.previousBackStackEntry?.savedStateHandle?.set(NOTICE, message)
                        navController.popBackStack()
                    },
                )
            }
            composable(PANS) {
                PansScreen(
                    onOpenPan = { id -> navController.navigate(if (id == null) PAN_EDIT_BASE else "$PAN_EDIT_BASE?id=$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "$PAN_EDIT_BASE?${PanEditViewModel.ARG_ID}={${PanEditViewModel.ARG_ID}}",
                arguments = listOf(optionalIdArgument(PanEditViewModel.ARG_ID)),
            ) {
                PanEditScreen(onDone = { navController.popBackStack() })
            }
            composable(SCANNER) {
                ScannerScreen(
                    onResult = { code ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_BARCODE, code)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            navigation(startDestination = SETTINGS_MAIN, route = Tab.Settings.route) {
                composable(SETTINGS_MAIN) {
                    SettingsScreen(
                        onOpenProfile = { id ->
                            navController.navigate(if (id == null) PROFILE_EDIT_BASE else "$PROFILE_EDIT_BASE?id=$id")
                        },
                        onOpenPans = { navController.navigate(PANS) },
                        onOpenReports = { navController.navigate(REPORTS) },
                    )
                }
                composable(REPORTS) {
                    ReportsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = "$PROFILE_EDIT_BASE?${ProfileEditViewModel.ARG_ID}={${ProfileEditViewModel.ARG_ID}}",
                    arguments = listOf(optionalIdArgument(ProfileEditViewModel.ARG_ID)),
                ) {
                    ProfileEditScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Результаты выбора, сканера и создания продукта для экрана приёма. */
private class MealResults(val picked: PickedItem?, val scanned: String?, val created: String?, val clear: () -> Unit)

@Composable
private fun NavBackStackEntry.mealResults(): MealResults {
    val picked by savedStateHandle.getStateFlow<String?>(PICKED_ITEM, null).collectAsStateWithLifecycle()
    val scanned by scannedBarcode()
    val created by savedStateHandle.getStateFlow<String?>(CREATED_PRODUCT_ID, null).collectAsStateWithLifecycle()
    return MealResults(picked?.let(PickedItem::decode), scanned, created) {
        savedStateHandle[PICKED_ITEM] = null
        savedStateHandle[SCANNED_BARCODE] = null
        savedStateHandle[CREATED_PRODUCT_ID] = null
    }
}

/** Выбор для приёма пищи: продукты и блюда, недавние этого профиля. */
private fun mealPickerRoute(profileId: String?) =
    "$PICKER?${ProductPickerViewModel.ARG_MODE}=${ProductPickerViewModel.MODE_MEAL}" +
        (profileId?.let { "&${ProductPickerViewModel.ARG_PROFILE}=$it" } ?: "")

/** Код, который вернул сканер этому экрану; после обработки сбрасывается в null. */
@Composable
private fun NavBackStackEntry.scannedBarcode(): State<String?> =
    savedStateHandle.getStateFlow<String?>(SCANNED_BARCODE, null).collectAsStateWithLifecycle()

/** Необязательный id в маршруте: нет id — создание новой записи. */
private fun optionalIdArgument(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
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
