package com.walnut.beaconfinder.ui.knownbeacons

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.db.KnownBeaconEntity
import com.walnut.beaconfinder.data.repository.KnownBeaconRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnownBeaconsViewModel @Inject constructor(
    application: Application,
    private val knownBeaconRepo: KnownBeaconRepository
) : AndroidViewModel(application) {

    private val _beacons = MutableStateFlow<List<KnownBeaconEntity>>(emptyList())
    val beacons: StateFlow<List<KnownBeaconEntity>> = _beacons.asStateFlow()

    private val _editingBeacon = MutableStateFlow<KnownBeaconEntity?>(null)
    val editingBeacon: StateFlow<KnownBeaconEntity?> = _editingBeacon.asStateFlow()

    init {
        loadBeacons()
    }

    fun loadBeacons() {
        viewModelScope.launch {
            _beacons.value = knownBeaconRepo.getAll()
        }
    }

    fun addBeacon(entity: KnownBeaconEntity) {
        viewModelScope.launch {
            knownBeaconRepo.insert(entity)
            loadBeacons()
        }
    }

    fun updateBeacon(entity: KnownBeaconEntity) {
        viewModelScope.launch {
            knownBeaconRepo.update(entity)
            loadBeacons()
        }
    }

    fun deleteBeacon(entity: KnownBeaconEntity) {
        viewModelScope.launch {
            knownBeaconRepo.delete(entity)
            loadBeacons()
        }
    }

    fun setEditingBeacon(entity: KnownBeaconEntity?) {
        _editingBeacon.value = entity
    }
}
