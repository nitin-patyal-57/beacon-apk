package com.walnut.beaconfinder.ui.customformats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.db.CustomFormatEntity
import com.walnut.beaconfinder.data.repository.CustomFormatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomFormatsViewModel @Inject constructor(
    application: Application,
    private val customFormatRepo: CustomFormatRepository
) : AndroidViewModel(application) {

    private val _formats = MutableStateFlow<List<CustomFormatEntity>>(emptyList())
    val formats: StateFlow<List<CustomFormatEntity>> = _formats.asStateFlow()

    private val _editingFormat = MutableStateFlow<CustomFormatEntity?>(null)
    val editingFormat: StateFlow<CustomFormatEntity?> = _editingFormat.asStateFlow()

    init {
        loadFormats()
    }

    fun loadFormats() {
        viewModelScope.launch {
            _formats.value = customFormatRepo.getAll()
        }
    }

    fun addFormat(entity: CustomFormatEntity) {
        viewModelScope.launch {
            customFormatRepo.insert(entity)
            loadFormats()
        }
    }

    fun updateFormat(entity: CustomFormatEntity) {
        viewModelScope.launch {
            customFormatRepo.update(entity)
            loadFormats()
        }
    }

    fun deleteFormat(entity: CustomFormatEntity) {
        viewModelScope.launch {
            customFormatRepo.delete(entity)
            loadFormats()
        }
    }

    fun setEditingFormat(entity: CustomFormatEntity?) {
        _editingFormat.value = entity
    }
}
