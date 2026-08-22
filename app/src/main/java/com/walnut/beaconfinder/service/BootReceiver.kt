package com.walnut.beaconfinder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking background monitoring")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MONITORING_ENABLED, false)) {
                ScanWatchdogReceiver.schedule(context)
                BackgroundScanService.start(context)
                Log.d(TAG, "Background scan service started after boot")
            }
        }
    }

    companion object {
        const val TAG = "BootReceiver"
        const val PREFS_NAME = "beacon_finder_prefs"
        const val KEY_MONITORING_ENABLED = "background_monitoring_enabled"
    }
}
