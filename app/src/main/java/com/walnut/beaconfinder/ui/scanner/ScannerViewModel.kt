package com.walnut.beaconfinder.ui.scanner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.ble.BleConnectionManager
import com.walnut.beaconfinder.data.ble.BleScannerManager
import com.walnut.beaconfinder.data.ble.BluetoothStateObserver
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.ConnectionState
import com.walnut.beaconfinder.data.parser.BeaconParserEngine
import com.walnut.beaconfinder.data.parser.CustomBeaconParser
import com.walnut.beaconfinder.data.processing.BeaconPresenceTracker
import com.walnut.beaconfinder.data.processing.CooldownTracker
import com.walnut.beaconfinder.data.processing.NearestBeaconTracker
import com.walnut.beaconfinder.data.processing.PacketHistoryStore
import com.walnut.beaconfinder.data.processing.RssiProcessor
import com.walnut.beaconfinder.data.processing.TtsManager
import com.walnut.beaconfinder.data.repository.CustomFormatRepository
import com.walnut.beaconfinder.data.repository.KnownBeaconRepository
import com.walnut.beaconfinder.data.repository.SettingsRepository
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.service.ScanWatchdogReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    application: Application,
    private val scannerManager: BleScannerManager,
    private val connectionManager: BleConnectionManager,
    private val parserEngine: BeaconParserEngine,
    private val knownBeaconRepo: KnownBeaconRepository,
    private val customFormatRepo: CustomFormatRepository,
    private val settingsRepo: SettingsRepository,
    private val presenceTracker: BeaconPresenceTracker,
    private val cooldownTracker: CooldownTracker,
    private val nearestBeaconTracker: NearestBeaconTracker,
    private val ttsManager: TtsManager,
    private val bluetoothStateObserver: BluetoothStateObserver
) : AndroidViewModel(application) {

    private val _selectedFilter = MutableStateFlow(BeaconProtocolFilter.ALL)
    val selectedFilter: StateFlow<BeaconProtocolFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.LAST_SEEN)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(true)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _lastConnectedBeacon = MutableStateFlow<BeaconDevice?>(null)
    val lastConnectedBeacon: StateFlow<BeaconDevice?> = _lastConnectedBeacon.asStateFlow()

    private val _excludedAddresses = MutableStateFlow<Set<String>>(emptySet())

    private val _devices = MutableStateFlow(DeviceListWrapper(emptyList()))
    val devices: StateFlow<List<BeaconDevice>> = _devices.map { it.devices }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val isScanning: StateFlow<Boolean> = scannerManager.isScanning
    val scanError: StateFlow<String?> = scannerManager.scanError

    private val rssiProcessors = mutableMapOf<String, RssiProcessor>()
    private val packetHistoryStores = mutableMapOf<String, PacketHistoryStore>()
    private var proximityJob: Job? = null
    private var refreshJob: Job? = null

    init {
        ttsManager.init()
        nearestBeaconTracker.setEnabled(true)

        nearestBeaconTracker.setOnOutOfRangeConfirmedListener { address ->
            _excludedAddresses.value = _excludedAddresses.value + address
        }

        nearestBeaconTracker.setOnNearestBeaconFoundListener { device ->
            _lastConnectedBeacon.value = device
            _excludedAddresses.value = _excludedAddresses.value - device.address
        }

        viewModelScope.launch {
            customFormatRepo.getAll().forEach { entity ->
                CustomBeaconParser.CustomFormat(
                    name = entity.name,
                    manufacturerId = entity.manufacturerId,
                    frameSignature = entity.frameSignatureHex?.let { hexToBytes(it) },
                    identifierOffset = entity.identifierOffset,
                    identifierLength = entity.identifierLength
                )
            }
        }
    }

    fun startScan() {
        nearestBeaconTracker.reset()
        rssiProcessors.clear()
        packetHistoryStores.clear()
        _excludedAddresses.value = emptySet()
        scannerManager.startScan()
        startProximityTracking()
        startAutoRefresh()

        val app = getApplication<Application>()
        app.getSharedPreferences("beacon_finder_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("background_monitoring_enabled", true).apply()
        settingsRepo.setMonitoringEnabled(true)
        ScanWatchdogReceiver.schedule(app)
        BackgroundScanService.start(app)
    }

    fun stopScan() {
        scannerManager.stopScan()
        stopProximityTracking()
        stopAutoRefresh()
    }

    fun toggleAutoConnect() {
        _autoConnectEnabled.value = !_autoConnectEnabled.value
        nearestBeaconTracker.setEnabled(_autoConnectEnabled.value)
        if (_autoConnectEnabled.value && scannerManager.isScanning.value) {
            startProximityTracking()
        } else {
            stopProximityTracking()
        }
    }

    private fun startProximityTracking() {
        proximityJob?.cancel()
        proximityJob = viewModelScope.launch {
            while (true) {
                delay(PROXIMITY_CHECK_INTERVAL_MS)
                val deviceMap = scannerManager.devices.value
                val allDevices = deviceMap.values.toList()
                nearestBeaconTracker.checkAndAnnounce(allDevices)
            }
        }
    }

    private fun stopProximityTracking() {
        proximityJob?.cancel()
        proximityJob = null
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                emitDeviceList()
            }
        }
    }

    private fun emitDeviceList() {
        val filter = _selectedFilter.value
        val query = _searchQuery.value
        val sort = _sortOption.value
        val connStates = connectionManager.connectionState.value

        var list = scannerManager.getDevicesInOrder()
            .filter { it.address !in _excludedAddresses.value }
            .map { device ->
                val connState = connStates[device.address] ?: device.connectionState
                if (connState != device.connectionState) device.copy(connectionState = connState) else device
            }

        if (filter != BeaconProtocolFilter.ALL) {
            list = list.filter { it.protocol == filter.protocol }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                        it.address.contains(query, ignoreCase = true)
            }
        }

        if (sort != SortOption.LAST_SEEN) {
            list = when (sort) {
                SortOption.RSSI_DESC -> list.sortedBy { it.rssi }
                SortOption.RSSI_ASC -> list.sortedByDescending { it.rssi }
                SortOption.NAME -> list.sortedBy { it.displayName }
                else -> list
            }
        }

        val current = _devices.value.devices
        val currentMap = current.associateBy { it.address }
        val changed = list.size != current.size || list.any { device ->
            val old = currentMap[device.address]
            old == null || old.rssi != device.rssi || old.connectionState != device.connectionState || old.isInRange != device.isInRange
        }
        if (changed) {
            _devices.value = DeviceListWrapper(list)
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun clearDevices() = scannerManager.clearDevices()

    fun setFilter(filter: BeaconProtocolFilter) {
        _selectedFilter.value = filter
        emitDeviceList()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        emitDeviceList()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        emitDeviceList()
    }

    fun getDevice(address: String): BeaconDevice? = scannerManager.getDevice(address)

    fun connectToDevice(address: String) {
        val device = scannerManager.getDevice(address) ?: return
        val btDevice = device.bluetoothDevice ?: return
        connectionManager.connectGatt(btDevice)
    }

    fun disconnectFromDevice(address: String) {
        connectionManager.disconnect(address)
    }

    fun speakBeaconName(device: BeaconDevice) {
        val name = device.name?.takeIf { it.isNotBlank() } ?: when (device.protocol) {
            BeaconProtocol.IBEACON -> "Unknown iBeacon"
            BeaconProtocol.EDDYSTONE_UID -> "Unknown Eddystone"
            BeaconProtocol.EDDYSTONE_URL -> "Unknown Eddystone"
            else -> "Unknown beacon"
        }
        ttsManager.speak(name)
    }

    fun getRssiProcessor(address: String): RssiProcessor {
        if (rssiProcessors.size >= MAX_DEVICE_HISTORY && !rssiProcessors.containsKey(address)) {
            rssiProcessors.entries.iterator().let { iter ->
                if (iter.hasNext()) { iter.next(); iter.remove() }
            }
        }
        return rssiProcessors.getOrPut(address) { RssiProcessor() }
    }

    fun getPacketHistory(address: String): PacketHistoryStore {
        if (packetHistoryStores.size >= MAX_DEVICE_HISTORY && !packetHistoryStores.containsKey(address)) {
            packetHistoryStores.entries.iterator().let { iter ->
                if (iter.hasNext()) { iter.next(); iter.remove() }
            }
        }
        return packetHistoryStores.getOrPut(address) { PacketHistoryStore() }
    }

    fun getDeviceCount(): Int = scannerManager.devices.value.size

    fun addRssiSample(address: String, rssi: Int) {
        getRssiProcessor(address).addSample(rssi)
        getPacketHistory(address).addEntry(rssi, scannerManager.getDevice(address)?.rawAdvertisement ?: byteArrayOf())
    }

    override fun onCleared() {
        nearestBeaconTracker.setEnabled(false)
        stopProximityTracking()
        stopAutoRefresh()
        ttsManager.stop()
        super.onCleared()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val TAG = "ScannerViewModel"
        private const val PROXIMITY_CHECK_INTERVAL_MS = 1000L
        private const val AUTO_REFRESH_INTERVAL_MS = 200L
        private const val MAX_DEVICE_HISTORY = 50
    }
}

enum class BeaconProtocolFilter(val protocol: BeaconProtocol?, val label: String) {
    ALL(null, "All"),
    IBEACON(BeaconProtocol.IBEACON, "iBeacon"),
    EDDYSTONE_UID(BeaconProtocol.EDDYSTONE_UID, "Eddystone UID"),
    EDDYSTONE_URL(BeaconProtocol.EDDYSTONE_URL, "Eddystone URL"),
    EDDYSTONE_TLM(BeaconProtocol.EDDYSTONE_TLM, "Eddystone TLM"),
    EDDYSTONE_EID(BeaconProtocol.EDDYSTONE_EID, "Eddystone EID")
}

enum class SortOption(val label: String) {
    RSSI_DESC("Strongest RSSI"),
    RSSI_ASC("Weakest RSSI"),
    NAME("Name"),
    LAST_SEEN("Last Seen")
}

class DeviceListWrapper(val devices: List<BeaconDevice>)
