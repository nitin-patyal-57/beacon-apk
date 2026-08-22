package com.walnut.beaconfinder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.walnut.beaconfinder.data.ble.BluetoothStateObserver
import com.walnut.beaconfinder.data.repository.SettingsRepository
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.service.BootReceiver
import com.walnut.beaconfinder.service.ScanWatchdogReceiver
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class BeaconFinderApp : Application() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var bluetoothStateObserver: BluetoothStateObserver

    override fun onCreate() {
        super.onCreate()
        ErrorLogManager.init(this)
        createNotificationChannels()
        setupCrashHandler()
        bluetoothStateObserver.start()
        autoStartMonitoringIfNeeded()
    }

    private fun autoStartMonitoringIfNeeded() {
        val prefs = getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(BootReceiver.KEY_MONITORING_ENABLED, false)) {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (btManager?.adapter?.isEnabled == true) {
                Log.d(TAG, "Monitoring enabled + BT on → starting background scan service")
                BackgroundScanService.start(this)
            }
            ScanWatchdogReceiver.schedule(this)
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val crashText = "[$timestamp] CRASH on ${thread.name}: ${throwable.message}\n$sw\n\n"
                val file = File(filesDir, CRASH_LOG_FILE)
                file.appendText(crashText)
                Log.e(TAG, crashText)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val nearbyChannel = NotificationChannel(
            CHANNEL_NEARBY,
            "Nearby Beacons",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when configured beacons are nearby"
            enableVibration(true)
        }

        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            "Background Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background BLE monitoring status"
        }

        manager.createNotificationChannel(nearbyChannel)
        manager.createNotificationChannel(monitoringChannel)
    }

    companion object {
        const val CHANNEL_NEARBY = "nearby_beacons"
        const val CHANNEL_MONITORING = "background_monitoring"
        const val CRASH_LOG_FILE = "crash_log.txt"
        private const val TAG = "BeaconFinderApp"
    }
}
