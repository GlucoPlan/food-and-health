package com.glucoplan.foodhealth.ui.meal

import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.meal.MealDraft
import com.glucoplan.foodhealth.data.meal.MealDraftStore
import com.glucoplan.foodhealth.data.meal.MealField
import com.glucoplan.foodhealth.data.meal.MealRecordResult
import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Экран «Приём пищи» (ТЗ 4.1): черновик общий, хранится в [MealDraftStore]. */
@HiltViewModel
class MealViewModel @Inject constructor(
    private val store: MealDraftStore,
    private val meals: MealRepository,
    products: ProductRepository,
    dishes: DishRepository,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
) : MealEditorViewModel(products, dishes, profiles, devicePrefs) {

    override val draft: StateFlow<MealDraft?> get() = store.draft

    override fun updateDraft(change: (MealDraft) -> MealDraft) = store.update(change)

    init {
        // Сообщение от «Повторить» из истории: что пропущено
        viewModelScope.launch {
            store.notice.filterNotNull().collect {
                showMessage(it)
                store.onNoticeShown()
            }
        }
    }

    /** Сообщение с другого экрана (например, «Записано: 84,2 кг» после замера). */
    fun showNotice(text: String) = showMessage(text)

    /** «Записать»: сохранить и очистить; «кому» и время возвращаются к владельцу и «сейчас». */
    fun record() {
        val d = store.draft.value ?: return
        val profileId = state.value.profileId ?: run {
            transient.update { it.copy(errors = it.errors + (MealField.ITEMS to "Не выбран профиль")) }
            return
        }
        viewModelScope.launch {
            when (val result = meals.record(profileId, d)) {
                is MealRecordResult.Recorded -> {
                    store.replace(MealDraft())
                    transient.value = EditorTransient()
                    showMessage("Записано: ${NumberText.format(result.total.kcal, 0)} ккал")
                }
                is MealRecordResult.Invalid -> transient.update { it.copy(errors = result.errors) }
            }
        }
    }
}
