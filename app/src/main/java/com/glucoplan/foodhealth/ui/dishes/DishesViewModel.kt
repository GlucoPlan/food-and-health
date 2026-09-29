package com.glucoplan.foodhealth.ui.dishes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.dish.DishSummary
import com.glucoplan.foodhealth.data.product.ProductSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DishListState(
    val loaded: Boolean = false,
    val dishes: List<DishSummary> = emptyList(),
    val baseEmpty: Boolean = false,
)

@HiltViewModel
class DishesViewModel @Inject constructor(repository: DishRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<DishListState> =
        combine(repository.observeSummaries(), _query) { all, q ->
            val needle = ProductSearch.normalize(q)
            DishListState(
                loaded = true,
                dishes = all
                    .filter { needle.isEmpty() || ProductSearch.normalize(it.name).contains(needle) }
                    .sortedWith { a, b -> ProductSearch.compareNames(a.name, b.name) },
                baseEmpty = all.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishListState())

    fun onQueryChange(text: String) {
        _query.value = text
    }
}
