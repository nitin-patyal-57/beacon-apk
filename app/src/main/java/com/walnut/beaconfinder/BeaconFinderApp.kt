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
import com.walnut.beaconfinder.service.PeriodicScanWorker
import com.walnut.beaconfinder.service.ScanWatchdogReceiver
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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
        schedulePeriodicWorker()
        promptBatteryOptimization()
        promptNotificationPermission()
    }

    private fun autoStartMonitoringIfNeeded() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (btManager?.adapter?.isEnabled == true) {
            Log.d(TAG, "BT on at startup → starting background scan service")
            BackgroundScanService.start(this)
        }
        ScanWatchdogReceiver.schedule(this)
    }

    private fun schedulePeriodicWorker() {
        val request = androidx.work.PeriodicWorkRequestBuilder<PeriodicScanWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PeriodicScanWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "PeriodicScanWorker scheduled (every 15 min)")
    }

    private fun promptBatteryOptimization() {
        val prefs = getSharedPreferences("beacon_finder_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_opt_prompted", false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    prefs.edit().putBoolean("battery_opt_prompted", true).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to request battery optimization exemption", e)
                }
            }
        }
    }

    private fun promptNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = getSharedPreferences("beacon_finder_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("notif_perm_prompted", false)) return
            val hasPerm = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    prefs.edit().putBoolean("notif_perm_prompted", true).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open notification settings", e)
                }
            }
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
            NotificationManager.IMPORTANCE_DEFAULT
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
