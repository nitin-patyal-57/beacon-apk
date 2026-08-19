package com.walnut.beaconfinder.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.parser.BeaconParserEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScannerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserEngine: BeaconParserEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<Map<String, BeaconDevice>>(emptyMap())
    val devices: StateFlow<Map<String, BeaconDevice>> = _devices.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BeaconDevice>()

    val isBluetoothEnabled: Boolean
        get() = try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun startScan() {
        if (_isScanning.value) return

        if (!hasBluetoothConnectPermission()) {
            _scanError.value = "Bluetooth Connect permission not granted"
            Log.e(TAG, "BLUETOOTH_CONNECT permission missing")
            return
        }

        try {
            scanner = bluetoothAdapter?.bluetoothLeScanner
        } catch (e: SecurityException) {
            _scanError.value = "Bluetooth permission denied. Grant Bluetooth permissions in Settings."
            Log.e(TAG, "SecurityException getting scanner", e)
            return
        }

        if (scanner == null) {
            _scanError.value = "Bluetooth LE scanner unavailable. Is Bluetooth enabled?"
            return
        }

        if (!isBluetoothEnabled) {
            _scanError.value = "Bluetooth is turned off. Please enable it."
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    processScanResult(result)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException in scan callback", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scan callback", e)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    try {
                        processScanResult(result)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException in batch scan callback", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in batch scan callback", e)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
                _scanError.value = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed. Check permissions."
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal scan error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported"
                    else -> "Scan failed (code: $errorCode)"
                }
            }
        }

        try {
            scanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            _scanError.value = null
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            _scanError.value = "Bluetooth permission denied. Grant Bluetooth permissions in Settings."
            Log.e(TAG, "SecurityException starting scan", e)
        } catch (e: Exception) {
            _scanError.value = "Failed to start scan: ${e.message}"
            Log.e(TAG, "Exception starting scan", e)
        }
    }

    fun stopScan() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        scanCallback = null
        _isScanning.value = false
        Log.d(TAG, "BLE scan stopped")
    }

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyMap()
    }

    private fun processScanResult(result: ScanResult) {
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Skipping scan result - missing BLUETOOTH_CONNECT permission")
            return
        }

        try {
            val beacon = parserEngine.parse(result)

            val existing = deviceMap[beacon.address]
            val updatedDevice = if (existing != null) {
                beacon.copy(
                    firstSeen = existing.firstSeen,
                    lastSeen = System.currentTimeMillis(),
                    connectionState = existing.connectionState,
                    name = beacon.name ?: existing.name
                )
            } else {
                beacon
            }

            deviceMap[beacon.address] = updatedDevice
            _devices.value = deviceMap.toMap()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException processing scan result", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan result", e)
        }
    }

    fun getDevice(address: String): BeaconDevice? = deviceMap[address]

    fun updateDeviceConnectionState(address: String, state: com.walnut.beaconfinder.data.model.ConnectionState) {
        val device = deviceMap[address] ?: return
        deviceMap[address] = device.copy(connectionState = state)
        _devices.value = deviceMap.toMap()
    }

    companion object {
        private const val TAG = "BleScannerManager"
        private const val SCAN_FAILED_ALREADY_STARTED = 1
        private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        private const val SCAN_FAILED_INTERNAL_ERROR = 3
        private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
