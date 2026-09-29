package com.glucoplan.foodhealth.ui.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.db.ItemUsage
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.dish.DishSummary
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.meal.RecentItems
import com.glucoplan.foodhealth.data.meal.RecentKind
import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.product.ProductSearch
import com.glucoplan.foodhealth.data.product.ProductSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Что делать экрану выбора после сканирования или создания продукта. */
sealed interface PickerEvent {
    /** Вернуть выбор вызвавшему экрану. */
    data class Picked(val item: PickedItem) : PickerEvent

    /** Код не найден: создать продукт с этим штрихкодом. */
    data class CreateWithBarcode(val barcode: String) : PickerEvent
}

sealed interface PickerRow {
    val name: String

    data class ProductRow(val product: Product) : PickerRow {
        override val name get() = product.name
    }

    data class DishRow(val dish: DishSummary) : PickerRow {
        override val name get() = dish.name
    }
}

data class PickerSection(val title: String?, val rows: List<PickerRow>)

/**
 * Выбор позиции: для состава блюда — только продукты, для приёма пищи — продукты и блюда
 * одним поиском, без текста сверху недавние и частые позиции профиля (ТЗ 4.1, 4.4).
 */
@HiltViewModel
class ProductPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    dishes: DishRepository,
    meals: MealRepository,
) : ViewModel() {

    private val withDishes = savedStateHandle.get<String>(ARG_MODE) == MODE_MEAL
    private val profileId = savedStateHandle.get<String>(ARG_PROFILE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val usages = MutableStateFlow<List<ItemUsage>>(emptyList())

    /** null — ещё загружается. */
    val sections: StateFlow<List<PickerSection>?> = combine(
        products.observeProducts(),
        if (withDishes) dishes.observeSummaries() else flowOf(emptyList()),
        if (withDishes) dishes.observeVersionCatalog() else flowOf(emptyMap()),
        _query,
        usages,
    ) { productList, dishList, catalog, q, used ->
        val productById = productList.associateBy { it.id }
        val dishById = dishList.associateBy { it.id }
        val found: List<PickerRow> = (
            ProductSearch.apply(productList, q, ProductSort.NAME, ascending = true).map { PickerRow.ProductRow(it) } +
                dishList.filter { q.isBlank() || ProductSearch.normalize(it.name).contains(ProductSearch.normalize(q)) }
                    .map { PickerRow.DishRow(it) }
            ).sortedWith { a, b -> ProductSearch.compareNames(a.name, b.name) }

        if (!withDishes || q.isNotBlank()) return@combine listOf(PickerSection(null, found))

        val recent = RecentItems.pick(
            usages = used,
            dishOfVersion = { catalog[it]?.dishId },
            isAvailable = { key ->
                when (key.type) {
                    MealItemType.PRODUCT -> key.id in productById
                    MealItemType.DISH -> key.id in dishById
                }
            },
            now = System.currentTimeMillis(),
        )
        fun rows(kind: RecentKind) = recent.filter { it.kind == kind }.mapNotNull { entry ->
            when (entry.key.type) {
                MealItemType.PRODUCT -> productById[entry.key.id]?.let { PickerRow.ProductRow(it) }
                MealItemType.DISH -> dishById[entry.key.id]?.let { PickerRow.DishRow(it) }
            }
        }
        listOf(
            PickerSection("Недавние", rows(RecentKind.RECENT)),
            PickerSection("Часто", rows(RecentKind.FREQUENT)),
            PickerSection("Всё", found),
        ).filter { it.rows.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _event = MutableStateFlow<PickerEvent?>(null)
    val event: StateFlow<PickerEvent?> = _event.asStateFlow()

    init {
        if (withDishes && profileId != null) viewModelScope.launch { usages.value = meals.usages(profileId) }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    /** Код со сканера: найденный продукт сразу выбирается, неизвестный — создаётся. */
    fun onScanned(barcode: String) {
        viewModelScope.launch {
            val product = products.findByBarcode(barcode)
            _event.value = if (product != null) {
                PickerEvent.Picked(PickedItem(MealItemType.PRODUCT, product.id))
            } else {
                PickerEvent.CreateWithBarcode(barcode)
            }
        }
    }

    /** Продукт, только что созданный из выбора, сразу выбирается. */
    fun onProductCreated(productId: String) {
        _event.value = PickerEvent.Picked(PickedItem(MealItemType.PRODUCT, productId))
    }

    fun onEventHandled() {
        _event.value = null
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_PROFILE = "profile"
        const val MODE_MEAL = "meal"
    }
}
