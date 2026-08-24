package com.walnut.beaconfinder.ui.zones

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.db.BeaconDatabase
import com.walnut.beaconfinder.data.db.ZoneEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ZoneViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val dao = BeaconDatabase.getInstance(application).zoneDao()

    val zones: StateFlow<List<ZoneEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addZone(name: String, beaconKeys: List<String>) {
        viewModelScope.launch {
            dao.insert(ZoneEntity(name = name, beaconKeys = org.json.JSONArray(beaconKeys).toString()))
        }
    }

    fun updateZone(zone: ZoneEntity) {
        viewModelScope.launch { dao.update(zone) }
    }

    fun deleteZone(zone: ZoneEntity) {
        viewModelScope.launch { dao.delete(zone) }
    }
}
