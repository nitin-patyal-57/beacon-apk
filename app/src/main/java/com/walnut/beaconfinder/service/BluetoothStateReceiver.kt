package com.walnut.beaconfinder.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.STATE_OFF
            )
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth ON → starting background scan")
                    ScanWatchdogReceiver.schedule(context)
                    try {
                        BackgroundScanService.start(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service from BT receiver", e)
                    }
                }
                BluetoothAdapter.STATE_OFF -> {
                    Log.d(TAG, "Bluetooth OFF → scan will stop internally in service")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothStateReceiver"
    }
}
