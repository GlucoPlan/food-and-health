package com.glucoplan.foodhealth.ui.picker

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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Что делать экрану выбора после сканирования или создания продукта. */
sealed interface PickerEvent {
    /** Вернуть продукт вызвавшему экрану. */
    data class Picked(val productId: String) : PickerEvent

    /** Код не найден: создать продукт с этим штрихкодом. */
    data class CreateWithBarcode(val barcode: String) : PickerEvent
}

/** Выбор продукта для состава блюда, потом — для приёма пищи (ТЗ 4.1, 4.4). */
@HiltViewModel
class ProductPickerViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null — ещё загружается. */
    val products: StateFlow<List<Product>?> =
        combine(repository.observeProducts(), _query) { all, q ->
            ProductSearch.apply(all, q, ProductSort.NAME, ascending = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _event = MutableStateFlow<PickerEvent?>(null)
    val event: StateFlow<PickerEvent?> = _event.asStateFlow()

    fun onQueryChange(text: String) {
        _query.value = text
    }

    /** Код со сканера: найденный продукт сразу выбирается, неизвестный — создаётся. */
    fun onScanned(barcode: String) {
        viewModelScope.launch {
            val product = repository.findByBarcode(barcode)
            _event.value = if (product != null) PickerEvent.Picked(product.id) else PickerEvent.CreateWithBarcode(barcode)
        }
    }

    /** Продукт, только что созданный из выбора, сразу выбирается. */
    fun onProductCreated(productId: String) {
        _event.value = PickerEvent.Picked(productId)
    }

    fun onEventHandled() {
        _event.value = null
    }
}
