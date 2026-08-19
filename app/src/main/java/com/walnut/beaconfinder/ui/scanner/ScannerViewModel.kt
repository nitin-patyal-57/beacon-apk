package com.walnut.beaconfinder.ui.scanner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.ble.BleConnectionManager
import com.walnut.beaconfinder.data.ble.BleScannerManager
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
    private val ttsManager: TtsManager
) : AndroidViewModel(application) {

    private val _selectedFilter = MutableStateFlow(BeaconProtocolFilter.ALL)
    val selectedFilter: StateFlow<BeaconProtocolFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RSSI_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(true)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _lastConnectedBeacon = MutableStateFlow<BeaconDevice?>(null)
    val lastConnectedBeacon: StateFlow<BeaconDevice?> = _lastConnectedBeacon.asStateFlow()

    val devices: StateFlow<List<BeaconDevice>> = combine(
        scannerManager.devices,
        _selectedFilter,
        _searchQuery,
        _sortOption,
        connectionManager.connectionState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val deviceMap = values[0] as Map<String, BeaconDevice>
        val filter = values[1] as BeaconProtocolFilter
        val query = values[2] as String
        val sort = values[3] as SortOption
        val connStates = values[4] as Map<String, ConnectionState>

        var list = deviceMap.values.map { device ->
            device.copy(connectionState = connStates[device.address] ?: device.connectionState)
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

        list = when (sort) {
            SortOption.RSSI_DESC -> list.sortedBy { it.rssi }
            SortOption.RSSI_ASC -> list.sortedByDescending { it.rssi }
            SortOption.NAME -> list.sortedBy { it.displayName }
            SortOption.LAST_SEEN -> list.sortedByDescending { it.lastSeen }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = scannerManager.isScanning
    val scanError: StateFlow<String?> = scannerManager.scanError

    private val rssiProcessors = mutableMapOf<String, RssiProcessor>()
    private val packetHistoryStores = mutableMapOf<String, PacketHistoryStore>()
    private var proximityJob: Job? = null

    init {
        ttsManager.init()
        nearestBeaconTracker.setEnabled(true)

        nearestBeaconTracker.setOnNearestBeaconFoundListener { device ->
            _lastConnectedBeacon.value = device
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
        scannerManager.startScan()
        startProximityTracking()
    }

    fun stopScan() {
        scannerManager.stopScan()
        stopProximityTracking()
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
            delay(2000)
            while (true) {
                delay(PROXIMITY_CHECK_INTERVAL_MS)
                val deviceMap = scannerManager.devices.value
                val allDevices = deviceMap.values.toList()
                allDevices.forEach { nearestBeaconTracker.processDevice(it) }
                nearestBeaconTracker.checkAndAnnounce(allDevices)
            }
        }
    }

    private fun stopProximityTracking() {
        proximityJob?.cancel()
        proximityJob = null
    }

    fun clearDevices() = scannerManager.clearDevices()

    fun setFilter(filter: BeaconProtocolFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
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
        val name = device.name ?: when (device.protocol) {
            BeaconProtocol.IBEACON -> "iBeacon ${device.iBeaconUuid?.take(8)}, Major ${device.iBeaconMajor}, Minor ${device.iBeaconMinor}"
            BeaconProtocol.EDDYSTONE_UID -> "Eddystone UID ${device.eddystoneNamespace?.take(10)}"
            BeaconProtocol.EDDYSTONE_URL -> "Eddystone URL ${device.eddystoneUrl}"
            else -> device.address
        }
        ttsManager.speak(name)
    }

    fun getRssiProcessor(address: String): RssiProcessor {
        return rssiProcessors.getOrPut(address) { RssiProcessor() }
    }

    fun getPacketHistory(address: String): PacketHistoryStore {
        return packetHistoryStores.getOrPut(address) { PacketHistoryStore() }
    }

    fun getDeviceCount(): Int = scannerManager.devices.value.size

    fun addRssiSample(address: String, rssi: Int) {
        getRssiProcessor(address).addSample(rssi)
        getPacketHistory(address).addEntry(rssi, scannerManager.getDevice(address)?.rawAdvertisement ?: byteArrayOf())
    }

    override fun onCleared() {
        ttsManager.stop()
        nearestBeaconTracker.setEnabled(false)
        stopProximityTracking()
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
        private const val PROXIMITY_CHECK_INTERVAL_MS = 2000L
    }
}

enum class BeaconProtocolFilter(val protocol: BeaconProtocol?, val label: String) {
    ALL(null, "All"),
    IBEACON(BeaconProtocol.IBEACON, "iBeacon"),
    EDDYSTONE_UID(BeaconProtocol.EDDYSTONE_UID, "Eddystone UID"),
    EDDYSTONE_URL(BeaconProtocol.EDDYSTONE_URL, "Eddystone URL"),
    EDDYSTONE_TLM(BeaconProtocol.EDDYSTONE_TLM, "Eddystone TLM"),
    EDDYSTONE_EID(BeaconProtocol.EDDYSTONE_EID, "Eddystone EID"),
    CUSTOM_BLE(BeaconProtocol.CUSTOM_BLE, "Custom"),
    GENERIC_BLE(BeaconProtocol.GENERIC_BLE, "Generic")
}

enum class SortOption(val label: String) {
    RSSI_DESC("Strongest RSSI"),
    RSSI_ASC("Weakest RSSI"),
    NAME("Name"),
    LAST_SEEN("Last Seen")
}
