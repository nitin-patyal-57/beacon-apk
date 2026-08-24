package com.walnut.beaconfinder.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.service.ScanWatchdogReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothStateObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _btTurnedOn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val btTurnedOn: SharedFlow<Unit> = _btTurnedOn.asSharedFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    private var initialStateReceived = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF
                    )
                    val btEnabled = state == BluetoothAdapter.STATE_ON

                    if (!initialStateReceived) {
                        initialStateReceived = true
                        _isEnabled.value = btEnabled
                        Log.d(TAG, "Initial BT state: enabled=$btEnabled")
                        return
                    }

                    _isEnabled.value = btEnabled
                    if (btEnabled) {
                        _btTurnedOn.tryEmit(Unit)
                        Log.d(TAG, "Bluetooth ON → starting scan service + emitting event")
                        ScanWatchdogReceiver.schedule(this@BluetoothStateObserver.context)
                        try {
                            BackgroundScanService.start(this@BluetoothStateObserver.context)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start service from observer", e)
                        }
                    } else {
                        Log.d(TAG, "Bluetooth OFF → service handles internally")
                    }
                }
            }
        }
    }

    fun start() {
        val btOn = bluetoothAdapter?.isEnabled == true
        _isEnabled.value = btOn
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        if (btOn) {
            Log.d(TAG, "BT already ON at startup → starting scan service")
            ScanWatchdogReceiver.schedule(context)
            try {
                BackgroundScanService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service at startup", e)
            }
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    companion object {
        private const val TAG = "BluetoothStateObserver"
    }
}
