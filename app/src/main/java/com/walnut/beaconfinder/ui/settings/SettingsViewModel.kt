package com.walnut.beaconfinder.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.walnut.beaconfinder.data.repository.SettingsRepository
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.service.BootReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepo: SettingsRepository
) : AndroidViewModel(application) {

    val monitoringEnabled: StateFlow<Boolean> = settingsRepo.monitoringEnabled
    val notificationsEnabled: StateFlow<Boolean> = settingsRepo.notificationsEnabled
    val autoConnectEnabled: StateFlow<Boolean> = settingsRepo.autoConnectEnabled
    val presenceTimeoutMs: StateFlow<Long> = settingsRepo.presenceTimeoutMs
    val minRssi: StateFlow<Int> = settingsRepo.minRssi
    val maxRetries: StateFlow<Int> = settingsRepo.maxRetries

    fun setMonitoringEnabled(enabled: Boolean) {
        settingsRepo.setMonitoringEnabled(enabled)
        // Save to prefs for boot receiver
        getApplication<Application>().getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(BootReceiver.KEY_MONITORING_ENABLED, enabled).apply()

        if (enabled) {
            BackgroundScanService.start(getApplication())
        } else {
            BackgroundScanService.stop(getApplication())
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = settingsRepo.setNotificationsEnabled(enabled)
    fun setAutoConnectEnabled(enabled: Boolean) = settingsRepo.setAutoConnectEnabled(enabled)
    fun setPresenceTimeoutMs(timeout: Long) = settingsRepo.setPresenceTimeoutMs(timeout)
    fun setMinRssi(rssi: Int) = settingsRepo.setMinRssi(rssi)
    fun setMaxRetries(retries: Int) = settingsRepo.setMaxRetries(retries)
}
