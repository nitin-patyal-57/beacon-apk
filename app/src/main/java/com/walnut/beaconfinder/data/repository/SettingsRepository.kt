package com.walnut.beaconfinder.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _monitoringEnabled = MutableStateFlow(prefs.getBoolean(KEY_MONITORING_ENABLED, false))
    val monitoringEnabled: StateFlow<Boolean> = _monitoringEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONNECT_ENABLED, false))
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _presenceTimeoutMs = MutableStateFlow(prefs.getLong(KEY_PRESENCE_TIMEOUT_MS, 30_000L))
    val presenceTimeoutMs: StateFlow<Long> = _presenceTimeoutMs.asStateFlow()

    private val _minRssi = MutableStateFlow(prefs.getInt(KEY_MIN_RSSI, -80))
    val minRssi: StateFlow<Int> = _minRssi.asStateFlow()

    private val _maxRetries = MutableStateFlow(prefs.getInt(KEY_MAX_RETRIES, 3))
    val maxRetries: StateFlow<Int> = _maxRetries.asStateFlow()

    private val _quietHoursEnabled = MutableStateFlow(prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false))
    val quietHoursEnabled: StateFlow<Boolean> = _quietHoursEnabled.asStateFlow()

    private val _quietHoursStart = MutableStateFlow(prefs.getInt(KEY_QUIET_HOURS_START, 22))
    val quietHoursStart: StateFlow<Int> = _quietHoursStart.asStateFlow()

    private val _quietHoursEnd = MutableStateFlow(prefs.getInt(KEY_QUIET_HOURS_END, 7))
    val quietHoursEnd: StateFlow<Int> = _quietHoursEnd.asStateFlow()

    private val _notificationRangeMeters = MutableStateFlow(prefs.getFloat(KEY_NOTIFICATION_RANGE_METERS, 50f).toDouble())
    val notificationRangeMeters: StateFlow<Double> = _notificationRangeMeters.asStateFlow()

    private val _darkModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE_ENABLED, false))
    val darkModeEnabled: StateFlow<Boolean> = _darkModeEnabled.asStateFlow()

    private val _adaptiveScanEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADAPTIVE_SCAN_ENABLED, true))
    val adaptiveScanEnabled: StateFlow<Boolean> = _adaptiveScanEnabled.asStateFlow()

    private val _scanHistoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCAN_HISTORY_ENABLED, true))
    val scanHistoryEnabled: StateFlow<Boolean> = _scanHistoryEnabled.asStateFlow()

    private val _notificationGroupingEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_GROUPING_ENABLED, true))
    val notificationGroupingEnabled: StateFlow<Boolean> = _notificationGroupingEnabled.asStateFlow()

    private val _leaderboardPeriodHours = MutableStateFlow(prefs.getInt(KEY_LEADERBOARD_PERIOD_HOURS, 24))
    val leaderboardPeriodHours: StateFlow<Int> = _leaderboardPeriodHours.asStateFlow()

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply()
        _darkModeEnabled.value = enabled
    }

    fun setAdaptiveScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADAPTIVE_SCAN_ENABLED, enabled).apply()
        _adaptiveScanEnabled.value = enabled
    }

    fun setScanHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCAN_HISTORY_ENABLED, enabled).apply()
        _scanHistoryEnabled.value = enabled
    }

    fun setNotificationGroupingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_GROUPING_ENABLED, enabled).apply()
        _notificationGroupingEnabled.value = enabled
    }

    fun setLeaderboardPeriodHours(hours: Int) {
        prefs.edit().putInt(KEY_LEADERBOARD_PERIOD_HOURS, hours).apply()
        _leaderboardPeriodHours.value = hours
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
        _monitoringEnabled.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT_ENABLED, enabled).apply()
        _autoConnectEnabled.value = enabled
    }

    fun setPresenceTimeoutMs(timeout: Long) {
        prefs.edit().putLong(KEY_PRESENCE_TIMEOUT_MS, timeout).apply()
        _presenceTimeoutMs.value = timeout
    }

    fun setMinRssi(rssi: Int) {
        prefs.edit().putInt(KEY_MIN_RSSI, rssi).apply()
        _minRssi.value = rssi
    }

    fun setMaxRetries(retries: Int) {
        prefs.edit().putInt(KEY_MAX_RETRIES, retries).apply()
        _maxRetries.value = retries
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, enabled).apply()
        _quietHoursEnabled.value = enabled
    }

    fun setQuietHoursStart(hour: Int) {
        prefs.edit().putInt(KEY_QUIET_HOURS_START, hour).apply()
        _quietHoursStart.value = hour
    }

    fun setQuietHoursEnd(hour: Int) {
        prefs.edit().putInt(KEY_QUIET_HOURS_END, hour).apply()
        _quietHoursEnd.value = hour
    }

    fun setNotificationRangeMeters(meters: Double) {
        prefs.edit().putFloat(KEY_NOTIFICATION_RANGE_METERS, meters.toFloat()).apply()
        _notificationRangeMeters.value = meters
    }

    companion object {
        const val PREFS_NAME = "beacon_finder_prefs"
        const val KEY_MONITORING_ENABLED = "background_monitoring_enabled"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
        const val KEY_PRESENCE_TIMEOUT_MS = "presence_timeout_ms"
        const val KEY_MIN_RSSI = "min_rssi"
        const val KEY_MAX_RETRIES = "max_retries"
        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        const val KEY_NOTIFICATION_RANGE_METERS = "notification_range_meters"
        const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        const val KEY_ADAPTIVE_SCAN_ENABLED = "adaptive_scan_enabled"
        const val KEY_SCAN_HISTORY_ENABLED = "scan_history_enabled"
        const val KEY_NOTIFICATION_GROUPING_ENABLED = "notification_grouping_enabled"
        const val KEY_LEADERBOARD_PERIOD_HOURS = "leaderboard_period_hours"
    }
}
