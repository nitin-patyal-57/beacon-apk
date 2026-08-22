package com.walnut.beaconfinder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESTART_SCAN) {
            Log.d(TAG, "AlarmManager triggered → starting background scan service")
            BackgroundScanService.start(context)
        }
    }

    companion object {
        const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART_SCAN = "com.walnut.beaconfinder.RESTART_SCAN"
    }
}
