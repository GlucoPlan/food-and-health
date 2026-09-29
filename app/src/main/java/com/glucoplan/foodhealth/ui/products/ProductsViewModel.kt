package com.glucoplan.foodhealth.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductListQuery(
    val text: String = "",
    val sort: ProductSort = ProductSort.NAME,
    val ascending: Boolean = ProductSort.NAME.defaultAscending,
)

data class ProductListState(
    val loaded: Boolean = false,
    val products: List<Product> = emptyList(),
    /** Продуктов в базе вообще нет (в отличие от «ничего не найдено»). */
    val baseEmpty: Boolean = false,
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _query = MutableStateFlow(ProductListQuery())
    val query: StateFlow<ProductListQuery> = _query.asStateFlow()

    val state: StateFlow<ProductListState> =
        combine(repository.observeProducts(), _query) { all, q ->
            ProductListState(
                loaded = true,
                products = ProductSearch.apply(all, q.text, q.sort, q.ascending),
                baseEmpty = all.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductListState())

    private val _createWithBarcode = MutableStateFlow<String?>(null)

    /** Отсканирован неизвестный код — экран открывает новый продукт с этим кодом. */
    val createWithBarcode: StateFlow<String?> = _createWithBarcode.asStateFlow()

    /** Код со сканера: известный продукт показывается в списке, неизвестный — создаётся. */
    fun onScanned(barcode: String) {
        viewModelScope.launch {
            if (repository.findByBarcode(barcode) != null) {
                _query.update { it.copy(text = barcode) }
            } else {
                _createWithBarcode.value = barcode
            }
        }
    }

    fun onCreateWithBarcodeHandled() {
        _createWithBarcode.value = null
    }

    fun onQueryChange(text: String) = _query.update { it.copy(text = text) }

    /** Выбор сортировки; повторное нажатие на ту же разворачивает направление. */
    fun onSortClick(sort: ProductSort) = _query.update {
        if (it.sort == sort) it.copy(ascending = !it.ascending)
        else it.copy(sort = sort, ascending = sort.defaultAscending)
    }
}
