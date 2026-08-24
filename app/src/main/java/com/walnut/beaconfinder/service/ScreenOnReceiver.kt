package com.walnut.beaconfinder.service

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            Log.d(TAG, "Screen ON — checking service")
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (btManager?.adapter?.isEnabled == true) {
                if (!BackgroundScanService.isServiceScanning) {
                    Log.w(TAG, "Service not scanning after screen ON, restarting")
                    BackgroundScanService.forceRestart(context)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScreenOnReceiver"
    }
}
