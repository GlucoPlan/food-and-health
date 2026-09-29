package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.ViewModel
import com.glucoplan.foodhealth.update.UpdateRepository
import com.glucoplan.foodhealth.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updates: UpdateRepository,
) : ViewModel() {

    val update: StateFlow<UpdateState> = updates.state

    /** ТЗ 8.2: проверять обновление при каждом открытии настроек. */
    fun onOpened() = updates.checkNow()

    fun checkNow() = updates.checkNow()

    fun startUpdate() = updates.startUpdate()
}
