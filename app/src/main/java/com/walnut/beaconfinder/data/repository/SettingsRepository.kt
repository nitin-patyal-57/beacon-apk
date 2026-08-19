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

    companion object {
        const val PREFS_NAME = "beacon_finder_prefs"
        const val KEY_MONITORING_ENABLED = "background_monitoring_enabled"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
        const val KEY_PRESENCE_TIMEOUT_MS = "presence_timeout_ms"
        const val KEY_MIN_RSSI = "min_rssi"
        const val KEY_MAX_RETRIES = "max_retries"
    }
}
