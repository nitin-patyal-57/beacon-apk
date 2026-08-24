package com.walnut.beaconfinder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class ScanWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Watchdog fired")

        schedule(context)

        if (!isBluetoothEnabled(context)) {
            Log.d(TAG, "Bluetooth off, skipping scan restart")
            return
        }

        if (!hasPermissions(context)) {
            Log.d(TAG, "Missing permissions, skipping")
            return
        }

        Log.d(TAG, "BT on, restarting service")
        restartService(context)
    }

    private fun restartService(context: Context) {
        try {
            BackgroundScanService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service from watchdog", e)
        }
    }

    private fun isBluetoothEnabled(context: Context): Boolean {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return btManager?.adapter?.isEnabled == true
    }

    private fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val btScan = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
            val btConnect = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            if (btScan != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                btConnect != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "ScanWatchdog"
        const val KEY_SERVICE_ALIVE = "service_alive"
        const val KEY_LAST_ALIVE_TIME = "last_alive_time"
        private const val SERVICE_STALE_TIMEOUT_MS = 120_000L
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun schedule(context: Context) {
            val intent = Intent(context, ScanWatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 9999, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            Log.d(TAG, "Watchdog scheduled (alarmClock) every ${WATCHDOG_INTERVAL_MS / 1000}s")
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ScanWatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 9999, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Watchdog cancelled")
        }
    }
}
