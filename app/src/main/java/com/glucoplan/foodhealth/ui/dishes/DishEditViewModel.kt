package com.glucoplan.foodhealth.ui.dishes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.dish.DishCalculator
import com.glucoplan.foodhealth.data.dish.DishField
import com.glucoplan.foodhealth.data.dish.DishForm
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.dish.DishSaveMode
import com.glucoplan.foodhealth.data.dish.DishValidation
import com.glucoplan.foodhealth.data.dish.IngredientDraft
import com.glucoplan.foodhealth.data.dish.NetWeight
import com.glucoplan.foodhealth.data.dish.VersionSummary
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.pan.Pan
import com.glucoplan.foodhealth.data.pan.PanRepository
import com.glucoplan.foodhealth.data.product.Product
import com.glucoplan.foodhealth.data.product.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class IngredientRow(
    val key: String,
    val name: String,
    val productDeleted: Boolean,
    val weight: String,
    /** Ккал этой порции; null, если вес не введён. */
    val portionKcal: Double?,
    val error: String?,
)

data class DishEditUi(
    val mode: DishSaveMode,
    val loaded: Boolean = false,
    val form: DishForm = DishForm(),
    val errors: Map<String, String> = emptyMap(),
    val rows: List<IngredientRow> = emptyList(),
    /** Кастрюли для выбора, без удалённых. */
    val pans: List<Pan> = emptyList(),
    val selectedPan: Pan? = null,
    /** null — состав пуст, считать нечего. */
    val net: NetWeight? = null,
    val per100: Nutrition? = null,
    val versions: List<VersionSummary> = emptyList(),
    /** Продукт выбран, ждём ввода веса. */
    val pendingProduct: Product? = null,
    val done: Boolean = false,
)

private data class Editor(
    val mode: DishSaveMode,
    val loaded: Boolean,
    val form: DishForm = DishForm(),
    val errors: Map<String, String> = emptyMap(),
    val pendingProductId: String? = null,
    val done: Boolean = false,
)

/** Редактор блюда (ТЗ 4.4): состав, кастрюля, вес, расчёт на 100 г, «Сварил заново». */
@HiltViewModel
class DishEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dishes: DishRepository,
    products: ProductRepository,
    pans: PanRepository,
) : ViewModel() {

    private val dishId: String? = savedStateHandle.get<String>(ARG_ID)

    private val editor = MutableStateFlow(
        Editor(mode = if (dishId == null) DishSaveMode.NEW else DishSaveMode.EDIT, loaded = dishId == null)
    )

    val state: StateFlow<DishEditUi> = combine(
        editor,
        products.observeAllIncludingDeleted(),
        pans.observeAllIncludingDeleted(),
        dishId?.let(dishes::observeVersionSummaries) ?: flowOf(emptyList()),
    ) { e, productList, panList, versions ->
        buildUi(e, productList.associateBy { it.id }, panList, versions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DishEditUi(mode = editor.value.mode))

    init {
        if (dishId != null) viewModelScope.launch {
            val dish = dishes.getDish(dishId)
            val version = dish?.let { dishes.getVersion(it.currentVersionId) }
            editor.update {
                it.copy(
                    loaded = true,
                    form = DishForm(
                        name = dish?.name.orEmpty(),
                        panId = version?.panId,
                        grossWeight = version?.grossWeightG?.let(NumberText::format).orEmpty(),
                        ingredients = version?.ingredients.orEmpty().map {
                            IngredientDraft(it.id, it.productId, NumberText.format(it.weightG))
                        },
                    ),
                )
            }
        }
    }

    private fun edit(vararg fields: String, change: (DishForm) -> DishForm) = editor.update {
        // Вес нетто зависит от всего состава, поэтому ошибка веса сбрасывается при любой правке
        it.copy(form = change(it.form), errors = it.errors - fields.toSet() - DishField.GROSS - DishField.INGREDIENTS)
    }

    fun onNameChange(v: String) = edit(DishField.NAME) { it.copy(name = v) }
    fun onPanSelected(panId: String?) = edit { it.copy(panId = panId) }
    fun onGrossChange(v: String) = edit { it.copy(grossWeight = v) }

    fun onWeightChange(key: String, v: String) = edit(DishField.ingredient(key)) { form ->
        form.copy(ingredients = form.ingredients.map { if (it.key == key) it.copy(weight = v) else it })
    }

    fun onRemoveIngredient(key: String) = edit(DishField.ingredient(key)) { form ->
        form.copy(ingredients = form.ingredients.filterNot { it.key == key })
    }

    /** Продукт выбран на экране выбора — открыть ввод веса. */
    fun onProductPicked(productId: String) = editor.update { it.copy(pendingProductId = productId) }

    fun onWeightConfirmed(grams: Double) {
        val productId = editor.value.pendingProductId ?: return
        editor.update { it.copy(pendingProductId = null) }
        edit { form ->
            form.copy(
                ingredients = form.ingredients +
                    IngredientDraft(UUID.randomUUID().toString(), productId, NumberText.format(grams, 1))
            )
        }
    }

    fun onWeightDismissed() = editor.update { it.copy(pendingProductId = null) }

    /**
     * «Сварил заново» (ТЗ 4.4, 5.4): состав и кастрюля копируются, вес с кастрюлей пустой.
     * Удалённая кастрюля не копируется (ТЗ 5.5).
     */
    fun recook() {
        val panDeleted = state.value.selectedPan?.deleted == true
        editor.update { e ->
            e.copy(
                mode = DishSaveMode.RECOOK,
                errors = emptyMap(),
                form = e.form.copy(
                    panId = e.form.panId.takeUnless { panDeleted },
                    grossWeight = "",
                    ingredients = e.form.ingredients.map { it.copy(key = UUID.randomUUID().toString()) },
                ),
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val e = editor.value
            when (val result = dishes.save(dishId, e.mode, e.form)) {
                is DishValidation.Valid -> editor.update { it.copy(done = true) }
                is DishValidation.Invalid -> editor.update { it.copy(errors = result.errors) }
            }
        }
    }

    fun delete() {
        val id = dishId ?: return
        viewModelScope.launch {
            dishes.delete(id)
            editor.update { it.copy(done = true) }
        }
    }

    private fun buildUi(
        e: Editor,
        products: Map<String, Product>,
        allPans: List<Pan>,
        versions: List<VersionSummary>,
    ): DishEditUi {
        val rows = e.form.ingredients.map { d ->
            val product = products[d.productId]
            val weight = NumberText.parse(d.weight)
            IngredientRow(
                key = d.key,
                name = product?.name ?: "Неизвестный продукт",
                productDeleted = product?.deleted == true,
                weight = d.weight,
                portionKcal = if (product != null && weight != null) Nutrition.portion(product, weight).kcal else null,
                error = e.errors[DishField.ingredient(d.key)],
            )
        }
        val selectedPan = e.form.panId?.let { id -> allPans.firstOrNull { it.id == id } }
        val weighed = e.form.ingredients.mapNotNull { d ->
            val product = products[d.productId]
            val weight = NumberText.parse(d.weight)?.takeIf { it > 0 }
            if (product != null && weight != null) product to weight else null
        }
        val gross = if (e.form.grossWeight.isBlank()) null else NumberText.parse(e.form.grossWeight)
        val net = if (e.form.ingredients.isEmpty()) null
        else DishCalculator.netWeight(gross, selectedPan?.weightG, weighed.sumOf { it.second })

        return DishEditUi(
            mode = e.mode,
            loaded = e.loaded,
            form = e.form,
            errors = e.errors,
            rows = rows,
            pans = allPans.filter { !it.deleted }.sortedBy { it.name },
            selectedPan = selectedPan,
            net = net,
            per100 = (net as? NetWeight.Ok)?.let { DishCalculator.per100(weighed, it.grams) },
            versions = versions,
            pendingProduct = e.pendingProductId?.let { products[it] },
            done = e.done,
        )
    }

    companion object {
        const val ARG_ID = "id"
    }
}
