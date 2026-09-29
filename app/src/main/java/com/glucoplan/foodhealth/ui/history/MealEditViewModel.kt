package com.glucoplan.foodhealth.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.meal.MealDraft
import com.glucoplan.foodhealth.data.meal.MealDraftStore
import com.glucoplan.foodhealth.data.meal.MealRecordResult
import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.meal.RepeatResult
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.ui.meal.MealEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MealEditResult {
    /** Сохранено или удалено — вернуться в историю. */
    CLOSED,

    /** «Повторить» — открыть экран «Приём пищи». */
    REPEATED,

    /** Приём не найден (удалён на другом экране). */
    MISSING,
}

/** Открытый приём из истории (ТЗ 4.2): правка, удаление, «Повторить». */
@HiltViewModel
class MealEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meals: MealRepository,
    private val store: MealDraftStore,
    products: ProductRepository,
    dishes: DishRepository,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
) : MealEditorViewModel(products, dishes, profiles, devicePrefs) {

    private val mealId: String = checkNotNull(savedStateHandle.get<String>(ARG_ID))

    private val local = MutableStateFlow<MealDraft?>(null)
    override val draft: StateFlow<MealDraft?> get() = local

    override fun updateDraft(change: (MealDraft) -> MealDraft) = local.update { it?.let(change) }

    private val _result = MutableStateFlow<MealEditResult?>(null)
    val result: StateFlow<MealEditResult?> = _result.asStateFlow()

    /** «Повторить» при непустом черновике: ждём подтверждения замены. */
    private val _repeatPending = MutableStateFlow<RepeatResult?>(null)
    val repeatPending: StateFlow<RepeatResult?> = _repeatPending.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = meals.loadForEdit(mealId)
            if (loaded == null) _result.value = MealEditResult.MISSING else local.value = loaded
        }
    }

    fun save() {
        val d = local.value ?: return
        val profileId = state.value.profileId ?: return
        viewModelScope.launch {
            when (val r = meals.update(mealId, profileId, d)) {
                is MealRecordResult.Recorded -> _result.value = MealEditResult.CLOSED
                is MealRecordResult.Invalid -> transient.update { it.copy(errors = r.errors) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            meals.delete(mealId)
            _result.value = MealEditResult.CLOSED
        }
    }

    /** «Повторить»: состав — новым приёмом на главный экран; набранное там заменяется после вопроса. */
    fun repeat() {
        viewModelScope.launch {
            val repeat = meals.repeat(mealId) ?: return@launch
            if (store.draft.value?.items.isNullOrEmpty()) applyRepeat(repeat) else _repeatPending.value = repeat
        }
    }

    fun confirmRepeat() {
        _repeatPending.value?.let(::applyRepeat)
        _repeatPending.value = null
    }

    fun cancelRepeat() {
        _repeatPending.value = null
    }

    private fun applyRepeat(repeat: RepeatResult) {
        store.replace(repeat.draft, notice = repeat.message)
        _result.value = MealEditResult.REPEATED
    }

    companion object {
        const val ARG_ID = "id"
    }
}
