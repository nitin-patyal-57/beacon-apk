package com.walnut.beaconfinder.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.db.AlertHistoryEntity
import com.walnut.beaconfinder.data.db.BeaconDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertHistoryViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val dao = BeaconDatabase.getInstance(application).alertHistoryDao()

    val alerts: StateFlow<List<AlertHistoryEntity>> = dao.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }
}
