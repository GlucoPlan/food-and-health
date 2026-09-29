package com.glucoplan.foodhealth.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.dish.VersionSummary
import com.glucoplan.foodhealth.data.meal.DraftItem
import com.glucoplan.foodhealth.data.meal.MealCalculator
import com.glucoplan.foodhealth.data.meal.MealDraft
import com.glucoplan.foodhealth.data.meal.MealField
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.data.meal.ResolvedItem
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.ui.picker.PickedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Позиция на экране: сопоставленная позиция и то, что введено в поле веса. */
data class MealRow(val item: ResolvedItem, val input: String, val byPieces: Boolean, val error: String?)

/** Выбрано, ждём ввода веса. */
data class PendingAdd(
    val type: MealItemType,
    val refId: String,
    val title: String,
    val pieceWeightG: Double?,
    val per100: Nutrition,
)

data class MealUi(
    val loaded: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val profileId: String? = null,
    /** null — «сейчас». */
    val eatenAt: Long? = null,
    val rows: List<MealRow> = emptyList(),
    val total: Nutrition = Nutrition.ZERO,
    val errors: Map<String, String> = emptyMap(),
    val pending: PendingAdd? = null,
    /** Позиция, для которой выбирают другую варку, и варки её блюда. */
    val versionChoiceKey: String? = null,
    val versions: List<VersionSummary> = emptyList(),
    /** Выбранный профиль, если у него включён дневник СД1 (раздел 7); иначе null — полей СД1 нет. */
    val sd1Profile: Profile? = null,
    val glucose: String = "",
    val dose: String = "",
)

/** Состояние экрана, которое не входит в черновик. */
data class EditorTransient(
    val errors: Map<String, String> = emptyMap(),
    val pending: PendingAdd? = null,
    val versionChoice: Pair<String, String>? = null, // ключ позиции → id блюда
)

/**
 * Общая часть экрана приёма (ТЗ 4.1) и открытого приёма из истории (ТЗ 4.2):
 * кому, когда, позиции, добавление поиском и сканером, смена варки.
 * Где хранится черновик, решает наследник.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MealEditorViewModel(
    private val products: ProductRepository,
    private val dishes: DishRepository,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
) : ViewModel() {

    /** Черновик; null — ещё загружается. */
    protected abstract val draft: StateFlow<MealDraft?>

    protected abstract fun updateDraft(change: (MealDraft) -> MealDraft)

    protected val transient = MutableStateFlow(EditorTransient())

    private val _createWithBarcode = MutableStateFlow<String?>(null)

    /** Отсканирован неизвестный код — экран открывает создание продукта. */
    val createWithBarcode: StateFlow<String?> = _createWithBarcode.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /**
     * Сообщения для snackbar. Очередь, а не состояние: сообщение ждёт, пока экран его покажет,
     * и не теряется, если пришло, когда экран был на другой вкладке.
     */
    val messages: Flow<String> = _messages.receiveAsFlow()

    protected fun showMessage(text: String) {
        _messages.trySend(text)
    }

    private val versionCatalog = dishes.observeVersionCatalog()

    val state: StateFlow<MealUi> by lazy {
        combine(
            draft.filterNotNull(),
            transient,
            combine(profiles.observeProfiles(), devicePrefs.ownerId) { list, ownerId -> list to ownerId },
            combine(products.observeAllIncludingDeleted(), versionCatalog) { p, v -> p.associateBy { it.id } to v },
            transient.flatMapLatest { t ->
                t.versionChoice?.let { dishes.observeVersionSummaries(it.second) } ?: flowOf(emptyList())
            },
        ) { d, t, (profileList, ownerId), (productById, catalog), versions ->
            val resolved = MealCalculator.resolve(d.items, productById, catalog)
            val profileId = d.profileId?.takeIf { id -> profileList.any { it.id == id } } ?: ownerId
            MealUi(
                loaded = true,
                profiles = profileList,
                profileId = profileId,
                sd1Profile = profileList.firstOrNull { it.id == profileId }?.takeIf { it.sd1Enabled },
                glucose = d.glucose,
                dose = d.dose,
                eatenAt = d.eatenAt,
                rows = resolved.zip(d.items) { r, item ->
                    MealRow(r, item.pieces ?: item.weight, item.pieces != null, t.errors[MealField.item(item.key)])
                },
                total = MealCalculator.total(resolved),
                errors = t.errors,
                pending = t.pending,
                versionChoiceKey = t.versionChoice?.first,
                versions = versions,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MealUi())
    }

    private fun change(clearError: String? = null, f: (MealDraft) -> MealDraft) {
        updateDraft(f)
        transient.update { t -> t.copy(errors = t.errors - MealField.ITEMS - listOfNotNull(clearError).toSet()) }
    }

    fun onProfileSelected(id: String) = change { it.copy(profileId = id) }

    /** null — «сейчас». */
    fun onTimeSelected(millis: Long?) = change(MealField.TIME) { it.copy(eatenAt = millis) }

    fun onInputChange(key: String, text: String) = change(MealField.item(key)) { d ->
        d.copy(items = d.items.map { item ->
            when {
                item.key != key -> item
                item.pieces != null -> item.copy(pieces = text)
                else -> item.copy(weight = text)
            }
        })
    }

    fun onGlucoseChange(text: String) = change(MealField.GLUCOSE) { it.copy(glucose = text) }

    fun onDoseChange(text: String) = change(MealField.DOSE) { it.copy(dose = text) }

    fun onRemove(key: String) = change(MealField.item(key)) { d -> d.copy(items = d.items.filterNot { it.key == key }) }

    /** Выбор с экрана выбора: продукт или варка блюда. */
    fun onPicked(item: PickedItem) {
        viewModelScope.launch {
            when (item.type) {
                MealItemType.PRODUCT -> askProductWeight(item.id)
                MealItemType.DISH -> {
                    val version = versionCatalog.first()[item.id] ?: return@launch
                    transient.update {
                        it.copy(pending = PendingAdd(MealItemType.DISH, item.id, version.dishName, null, version.per100))
                    }
                }
            }
        }
    }

    /** Код со сканера: найденный продукт — сразу ввод веса (ТЗ 4.1). */
    fun onScanned(barcode: String) {
        viewModelScope.launch {
            val product = products.findByBarcode(barcode)
            if (product != null) askProductWeight(product.id) else _createWithBarcode.value = barcode
        }
    }

    fun onCreateWithBarcodeHandled() {
        _createWithBarcode.value = null
    }

    /** Продукт создан после сканирования неизвестного кода — сразу ввод веса. */
    fun onProductCreated(productId: String) {
        viewModelScope.launch { askProductWeight(productId) }
    }

    private suspend fun askProductWeight(productId: String) {
        val product = products.get(productId) ?: return
        transient.update {
            it.copy(
                pending = PendingAdd(
                    MealItemType.PRODUCT, product.id, product.name, product.pieceWeightG, Nutrition.per100(product)
                )
            )
        }
    }

    fun onWeightConfirmed(grams: Double, pieces: Double?) {
        val pending = transient.value.pending ?: return
        transient.update { it.copy(pending = null) }
        change { d ->
            d.copy(
                items = d.items + DraftItem(
                    key = UUID.randomUUID().toString(),
                    type = pending.type,
                    refId = pending.refId,
                    weight = NumberText.format(grams, 1),
                    pieces = pieces?.let { NumberText.format(it, 2) },
                )
            )
        }
    }

    fun onWeightDismissed() = transient.update { it.copy(pending = null) }

    /** ТЗ 5.4: другая варка блюда выбирается через список его варок. */
    fun onChooseVersion(key: String) {
        val dishId = state.value.rows.firstOrNull { it.item.key == key }?.item?.dishId ?: return
        transient.update { it.copy(versionChoice = key to dishId) }
    }

    fun onVersionChosen(versionId: String) {
        val key = transient.value.versionChoice?.first ?: return
        transient.update { it.copy(versionChoice = null) }
        change { d -> d.copy(items = d.items.map { if (it.key == key) it.copy(refId = versionId) else it }) }
    }

    fun onVersionDismissed() = transient.update { it.copy(versionChoice = null) }

}
