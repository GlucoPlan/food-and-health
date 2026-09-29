package com.glucoplan.foodhealth.ui.pans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.pan.Pan
import com.glucoplan.foodhealth.data.pan.PanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PansViewModel @Inject constructor(repository: PanRepository) : ViewModel() {
    /** null — ещё загружается. */
    val pans: StateFlow<List<Pan>?> = repository.observePans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
