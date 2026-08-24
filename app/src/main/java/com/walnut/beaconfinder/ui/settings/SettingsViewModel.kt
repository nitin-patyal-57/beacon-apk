package com.walnut.beaconfinder.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.walnut.beaconfinder.data.repository.SettingsRepository
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.service.BootReceiver
import com.walnut.beaconfinder.service.ScanWatchdogReceiver
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
    val quietHoursEnabled: StateFlow<Boolean> = settingsRepo.quietHoursEnabled
    val quietHoursStart: StateFlow<Int> = settingsRepo.quietHoursStart
    val quietHoursEnd: StateFlow<Int> = settingsRepo.quietHoursEnd
    val notificationRangeMeters: StateFlow<Double> = settingsRepo.notificationRangeMeters
    val darkModeEnabled: StateFlow<Boolean> = settingsRepo.darkModeEnabled
    val adaptiveScanEnabled: StateFlow<Boolean> = settingsRepo.adaptiveScanEnabled
    val scanHistoryEnabled: StateFlow<Boolean> = settingsRepo.scanHistoryEnabled
    val notificationGroupingEnabled: StateFlow<Boolean> = settingsRepo.notificationGroupingEnabled
    val leaderboardPeriodHours: StateFlow<Int> = settingsRepo.leaderboardPeriodHours

    fun setMonitoringEnabled(enabled: Boolean) {
        settingsRepo.setMonitoringEnabled(enabled)
        val app = getApplication<Application>()
        app.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(BootReceiver.KEY_MONITORING_ENABLED, enabled).apply()

        if (enabled) {
            ScanWatchdogReceiver.schedule(app)
            BackgroundScanService.start(app)
        } else {
            ScanWatchdogReceiver.cancel(app)
            BackgroundScanService.stop(app)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = settingsRepo.setNotificationsEnabled(enabled)
    fun setAutoConnectEnabled(enabled: Boolean) = settingsRepo.setAutoConnectEnabled(enabled)
    fun setPresenceTimeoutMs(timeout: Long) = settingsRepo.setPresenceTimeoutMs(timeout)
    fun setMinRssi(rssi: Int) = settingsRepo.setMinRssi(rssi)
    fun setMaxRetries(retries: Int) = settingsRepo.setMaxRetries(retries)
    fun setQuietHoursEnabled(enabled: Boolean) = settingsRepo.setQuietHoursEnabled(enabled)
    fun setQuietHoursStart(hour: Int) = settingsRepo.setQuietHoursStart(hour)
    fun setQuietHoursEnd(hour: Int) = settingsRepo.setQuietHoursEnd(hour)
    fun setNotificationRangeMeters(meters: Double) = settingsRepo.setNotificationRangeMeters(meters)
    fun setDarkModeEnabled(enabled: Boolean) = settingsRepo.setDarkModeEnabled(enabled)
    fun setAdaptiveScanEnabled(enabled: Boolean) = settingsRepo.setAdaptiveScanEnabled(enabled)
    fun setScanHistoryEnabled(enabled: Boolean) = settingsRepo.setScanHistoryEnabled(enabled)
    fun setNotificationGroupingEnabled(enabled: Boolean) = settingsRepo.setNotificationGroupingEnabled(enabled)
    fun setLeaderboardPeriodHours(hours: Int) = settingsRepo.setLeaderboardPeriodHours(hours)
}
