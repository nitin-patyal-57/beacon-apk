package com.walnut.beaconfinder.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.parser.BeaconParserEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val mainHandler = Handler(Looper.getMainLooper())

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
    private val rssiHistory = ConcurrentHashMap<String, MutableList<Pair<Long, Int>>>()
    private val lastInRangeTime = ConcurrentHashMap<String, Long>()
    private val deviceOrder = mutableListOf<String>()

    private var pendingUiUpdate = false
    private var onDeviceOutOfRange: ((String) -> Unit)? = null
    private var lastStaleCleanupAt: Long = 0L

    fun setOnDeviceOutOfRangeListener(listener: (String) -> Unit) {
        onDeviceOutOfRange = listener
    }
    private val uiUpdateRunnable = Runnable {
        emitDeviceMap()
        pendingUiUpdate = false
    }

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

    private var scanRetryCount = 0
    private var scanRetryRunnable: Runnable? = null

    fun startScan() {
        if (_isScanning.value) return
        isAnyScanRunning = false

        scope.launch {
            delay(1500)
            startScanInternal()
        }
    }

    private fun startScanInternal(retryAttempt: Int = 0) {
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

        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(0x004C, byteArrayOf()).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")).build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(REPORT_DELAY_MS)
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
                Log.e(TAG, "Scan failed with error code: $errorCode (attempt ${retryAttempt + 1})")
                _isScanning.value = false
                isAnyScanRunning = false
                scanner = null

                val retryable = errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ||
                        errorCode == SCAN_FAILED_ALREADY_STARTED ||
                        errorCode == SCAN_FAILED_INTERNAL_ERROR

                if (retryable && retryAttempt < MAX_SCAN_RETRIES) {
                    val backoffMs = SCAN_RETRY_BASE_DELAY_MS * (retryAttempt + 1)
                    Log.d(TAG, "Retrying scan in ${backoffMs}ms (attempt ${retryAttempt + 1}/$MAX_SCAN_RETRIES)")
                    _scanError.value = "Scan failed, retrying in ${backoffMs / 1000}s..."
                    scope.launch {
                        delay(backoffMs)
                        startScanInternal(retryAttempt + 1)
                    }
                } else {
                    _scanError.value = when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress by another app"
                        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "BLE scan registration failed. Restart Bluetooth and try again."
                        SCAN_FAILED_INTERNAL_ERROR -> "Internal BLE error. Restart Bluetooth and try again."
                        SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported on this device"
                        else -> "Scan failed (code: $errorCode)"
                    }
                }
            }
        }

        try {
            deviceMap.clear()
            rssiHistory.clear()
            lastInRangeTime.clear()
            synchronized(deviceOrder) { deviceOrder.clear() }
            _devices.value = emptyMap()
            scanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            isAnyScanRunning = true
            _scanError.value = null
            Log.d(TAG, "BLE scan started with reportDelay=${REPORT_DELAY_MS}ms")
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
        isAnyScanRunning = false
        mainHandler.removeCallbacks(uiUpdateRunnable)
        pendingUiUpdate = false
        Log.d(TAG, "BLE scan stopped")
    }

    fun clearDevices() {
        deviceMap.clear()
        rssiHistory.clear()
        lastInRangeTime.clear()
        synchronized(deviceOrder) { deviceOrder.clear() }
        _devices.value = emptyMap()
    }

    private fun processScanResult(result: ScanResult) {
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Skipping scan result - missing BLUETOOTH_CONNECT permission")
            return
        }

        try {
            val beacon = parserEngine.parse(result)
            Log.d(TAG, "Parsed: ${beacon.address} protocol=${beacon.protocol} name=${beacon.name} rssi=${beacon.rssi} mfgData=${beacon.manufacturerData?.let { String.format("%02X%02X", it[0], it[1]) }}")
            if (beacon.protocol != BeaconProtocol.IBEACON &&
                beacon.protocol != BeaconProtocol.EDDYSTONE_UID &&
                beacon.protocol != BeaconProtocol.EDDYSTONE_URL &&
                beacon.protocol != BeaconProtocol.EDDYSTONE_TLM &&
                beacon.protocol != BeaconProtocol.EDDYSTONE_EID) {
                return
            }
            val avgRssi = calculateAverageRssi(beacon.address, beacon.rssi)

            val existing = deviceMap[beacon.address]
            val now = System.currentTimeMillis()
            val wasInRange = existing?.isInRange == true
            if (avgRssi >= IN_RANGE_RSSI) {
                lastInRangeTime[beacon.address] = now
            }
            val isInRange = when {
                avgRssi >= IN_RANGE_RSSI -> true
                else -> (now - (lastInRangeTime[beacon.address] ?: 0L)) <= GRACE_PERIOD_MS
            }

            if (wasInRange && !isInRange) {
                onDeviceOutOfRange?.invoke(beacon.address)
            }

            val updatedDevice = if (existing != null) {
                beacon.copy(
                    rssi = avgRssi,
                    firstSeen = existing.firstSeen,
                    lastSeen = now,
                    connectionState = existing.connectionState,
                    name = beacon.name ?: existing.name,
                    isInRange = isInRange
                )
            } else {
                beacon.copy(rssi = avgRssi, isInRange = isInRange)
            }

            deviceMap[beacon.address] = updatedDevice

            synchronized(deviceOrder) {
                if (beacon.address !in deviceOrder) {
                    deviceOrder.add(beacon.address)
                }
            }

            if (now - lastStaleCleanupAt > 1000L) {
                lastStaleCleanupAt = now
                deviceMap.entries.removeIf { (addr, device) ->
                    val stale = (now - device.lastSeen) > STALE_DEVICE_MS
                    if (stale) {
                        synchronized(deviceOrder) { deviceOrder.remove(addr) }
                        rssiHistory.remove(addr)
                        lastInRangeTime.remove(addr)
                    }
                    stale
                }
            }

            scheduleUiUpdate()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException processing scan result", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan result", e)
        }
    }

    private fun calculateAverageRssi(address: String, newRssi: Int): Int {
        val history = rssiHistory.getOrPut(address) { mutableListOf() }
        val now = System.currentTimeMillis()

        history.add(now to newRssi)
        history.removeAll { now - it.first > WINDOW_SIZE_MS }

        return if (history.isNotEmpty()) {
            history.map { it.second }.average().toInt()
        } else {
            newRssi
        }
    }

    private fun scheduleUiUpdate() {
        if (!pendingUiUpdate) {
            pendingUiUpdate = true
            mainHandler.postDelayed(uiUpdateRunnable, UI_UPDATE_THROTTLE_MS)
        }
    }

    private fun emitDeviceMap() {
        _devices.value = deviceMap.toMap()
    }

    fun getDevice(address: String): BeaconDevice? = deviceMap[address]

    fun getDevicesInOrder(): List<BeaconDevice> {
        synchronized(deviceOrder) {
            return deviceOrder.mapNotNull { deviceMap[it] }
        }
    }

    fun refreshDevices() {
        val now = System.currentTimeMillis()
        deviceMap.entries.removeIf { (_, device) ->
            (now - device.lastSeen) > STALE_DEVICE_MS
        }
        _devices.value = deviceMap.toMap()
    }

    fun updateDeviceConnectionState(address: String, state: com.walnut.beaconfinder.data.model.ConnectionState) {
        val device = deviceMap[address] ?: return
        deviceMap[address] = device.copy(connectionState = state)
        _devices.value = deviceMap.toMap()
    }

    companion object {
        private const val TAG = "BleScannerManager"
        private const val STALE_DEVICE_MS = 15_000L
        private const val REPORT_DELAY_MS = 0L
        private const val WINDOW_SIZE_MS = 2_000L
        private const val UI_UPDATE_THROTTLE_MS = 100L
        private const val IN_RANGE_RSSI = -60
        private const val GRACE_PERIOD_MS = 3_000L
        private const val SCAN_FAILED_ALREADY_STARTED = 1
        private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        private const val SCAN_FAILED_INTERNAL_ERROR = 3
        private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        private const val MAX_SCAN_RETRIES = 5
        private const val SCAN_RETRY_BASE_DELAY_MS = 2000L

        @Volatile
        var isAnyScanRunning = false
            private set

        fun resetIsAnyScanRunning() {
            isAnyScanRunning = false
            Log.d(TAG, "isAnyScanRunning reset to false")
        }

        fun stopAllScans() {
            isAnyScanRunning = false
        }
    }
}
