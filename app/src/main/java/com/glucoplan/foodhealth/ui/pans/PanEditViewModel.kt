package com.glucoplan.foodhealth.ui.pans

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.pan.PanForm
import com.glucoplan.foodhealth.data.pan.PanPhotos
import com.glucoplan.foodhealth.data.pan.PanRepository
import com.glucoplan.foodhealth.data.pan.PanValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PanEditState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val form: PanForm = PanForm(),
    val nameError: String? = null,
    val weightError: String? = null,
    val photoBusy: Boolean = false,
    val photoError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class PanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pans: PanRepository,
    private val photos: PanPhotos,
) : ViewModel() {

    private val id: String? = savedStateHandle.get<String>(ARG_ID)
    private var originalPhoto: String? = null

    /** Фото, снятые или выбранные в этом редакторе: несохранённые удаляются при выходе. */
    private val sessionPhotos = mutableSetOf<String>()
    private var savedPhoto: String? = null

    private val _state = MutableStateFlow(PanEditState(isNew = id == null, loaded = id == null))
    val state: StateFlow<PanEditState> = _state.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            val pan = pans.get(id)
            originalPhoto = pan?.photo
            _state.update { it.copy(loaded = true, form = pan?.let(PanForm::from) ?: it.form) }
        }
    }

    fun cameraUri(): Uri = photos.cameraUri()

    fun onNameChange(v: String) = _state.update { it.copy(form = it.form.copy(name = v), nameError = null) }
    fun onWeightChange(v: String) = _state.update { it.copy(form = it.form.copy(weight = v), weightError = null) }
    fun removePhoto() = _state.update { it.copy(form = it.form.copy(photo = null)) }

    fun onCameraShot() = importPhoto { photos.importCameraShot() }
    fun onGalleryPicked(uri: Uri) = importPhoto { photos.importFromUri(uri) }

    private fun importPhoto(import: suspend () -> String) {
        _state.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            try {
                val name = import()
                sessionPhotos += name
                _state.update { it.copy(photoBusy = false, form = it.form.copy(photo = name)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(photoBusy = false, photoError = "Не удалось сохранить фото: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            val form = _state.value.form
            when (val result = pans.save(id, form).second) {
                is PanValidation.Valid -> {
                    savedPhoto = form.photo
                    // Старое фото заменили или убрали — файл больше не нужен
                    originalPhoto?.takeIf { it != form.photo }?.let(photos::delete)
                    _state.update { it.copy(done = true) }
                }
                is PanValidation.Invalid -> _state.update {
                    it.copy(nameError = result.nameError, weightError = result.weightError)
                }
            }
        }
    }

    fun delete() {
        val panId = id ?: return
        viewModelScope.launch {
            pans.delete(panId)
            _state.update { it.copy(done = true) }
        }
    }

    override fun onCleared() {
        sessionPhotos.filter { it != savedPhoto }.forEach(photos::delete)
    }

    companion object {
        const val ARG_ID = "id"
    }
}
