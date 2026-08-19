package com.walnut.beaconfinder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class BeaconFinderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        setupCrashHandler()
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
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications when configured beacons are nearby"
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
