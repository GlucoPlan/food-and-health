package com.glucoplan.foodhealth.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.product.ProductField
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.product.ProductValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductEditState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val form: ProductForm = ProductForm(),
    /** Ключи — ProductField. */
    val errors: Map<String, String> = emptyMap(),
    val microExpanded: Boolean = false,
    /** Сохранено или удалено — экран закрывается. */
    val done: Boolean = false,
    /** id только что созданного продукта: его сразу выбирает экран выбора продукта. */
    val createdId: String? = null,
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
) : ViewModel() {

    private val id: String? = savedStateHandle.get<String>(ARG_ID)

    private val _state = MutableStateFlow(
        ProductEditState(
            isNew = id == null,
            loaded = id == null,
            // Новый продукт после сканирования неизвестного кода
            form = ProductForm(barcode = savedStateHandle.get<String>(ARG_BARCODE).orEmpty()),
        )
    )
    val state: StateFlow<ProductEditState> = _state.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            val product = products.get(id)
            _state.update {
                it.copy(
                    loaded = true,
                    form = product?.let(ProductForm::from) ?: it.form,
                    // Блок витаминов раскрыт, если в нём что-то заполнено
                    microExpanded = product?.micro?.isNotEmpty() == true,
                )
            }
        }
    }

    /** Изменение любого поля; ошибка этого поля (и суммы БЖУ) сбрасывается. */
    fun edit(field: String, change: (ProductForm) -> ProductForm) = _state.update {
        it.copy(form = change(it.form), errors = it.errors - field - MACROS_KEY)
    }

    fun onScanned(barcode: String) = edit(ProductField.BARCODE) { it.copy(barcode = barcode) }

    fun toggleMicro() = _state.update { it.copy(microExpanded = !it.microExpanded) }

    fun save() {
        viewModelScope.launch {
            when (val result = products.save(id, _state.value.form)) {
                is ProductValidation.Valid -> _state.update {
                    it.copy(done = true, createdId = result.product.id.takeIf { id == null })
                }
                is ProductValidation.Invalid -> _state.update {
                    it.copy(
                        errors = result.errors,
                        microExpanded = it.microExpanded || result.errors.keys.any { k -> k.startsWith("micro:") },
                    )
                }
            }
        }
    }

    fun delete() {
        val productId = id ?: return
        viewModelScope.launch {
            products.delete(productId)
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_BARCODE = "barcode"
        private const val MACROS_KEY = "macros"
    }
}
