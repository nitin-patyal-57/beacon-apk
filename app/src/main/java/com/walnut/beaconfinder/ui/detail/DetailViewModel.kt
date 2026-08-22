package com.walnut.beaconfinder.ui.detail

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.walnut.beaconfinder.data.ble.BleConnectionManager
import com.walnut.beaconfinder.data.ble.BleScannerManager
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.ConnectionState
import com.walnut.beaconfinder.data.model.GattServiceInfo
import com.walnut.beaconfinder.data.processing.DistanceCalculator
import com.walnut.beaconfinder.data.processing.PacketHistoryStore
import com.walnut.beaconfinder.data.processing.RssiProcessor
import com.walnut.beaconfinder.data.repository.KnownBeaconRepository
import com.walnut.beaconfinder.data.db.KnownBeaconEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val scannerManager: BleScannerManager,
    private val connectionManager: BleConnectionManager,
    private val knownBeaconRepo: KnownBeaconRepository
) : AndroidViewModel(application) {

    private val address: String = savedStateHandle["address"] ?: ""

    private val _device = MutableStateFlow(DeviceWrapper(null))
    val device: StateFlow<BeaconDevice?> = _device.map { it.device }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    private val _knownBeacon = MutableStateFlow<KnownBeaconEntity?>(null)
    val knownBeacon: StateFlow<KnownBeaconEntity?> = _knownBeacon.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
        .map { it[address] ?: ConnectionState.DISCONNECTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val discoveredServices: StateFlow<List<GattServiceInfo>> = connectionManager.discoveredServices
        .map { it[address] ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characteristicValue: StateFlow<Pair<String, ByteArray>?> = connectionManager.characteristicValue
        .map { if (it?.first == address) it else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rssiProcessor = RssiProcessor()
    val packetHistory = PacketHistoryStore()

    private val _refreshTick = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            val dev = scannerManager.getDevice(address)
            _device.value = DeviceWrapper(dev)
            _knownBeacon.value = knownBeaconRepo.getByIdentityKey(dev?.identityKey ?: "")
        }

        viewModelScope.launch {
            scannerManager.devices.collect { deviceMap ->
                val updated = deviceMap[address]
                if (updated != null) {
                    _device.value = DeviceWrapper(updated)
                }
            }
        }

        viewModelScope.launch {
            _refreshTick.collect {
                val dev = scannerManager.getDevice(address)
                if (dev != null) {
                    _device.value = DeviceWrapper(dev)
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500L)
                _refreshTick.value++
            }
        }
    }

    fun refreshDevice() {
        _device.value = DeviceWrapper(scannerManager.getDevice(address))
    }

    fun connect() {
        val dev = _device.value.device ?: return
        val btDevice = dev.bluetoothDevice ?: return
        connectionManager.connectGatt(btDevice)
    }

    fun disconnect() {
        connectionManager.disconnect(address)
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        connectionManager.readCharacteristic(address, characteristic)
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        connectionManager.writeCharacteristic(address, characteristic, value)
    }

    fun toggleNotification(characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        connectionManager.setCharacteristicNotification(address, characteristic, enabled)
    }

    fun addRssiSample(rssi: Int) {
        rssiProcessor.addSample(rssi)
        val dev = _device.value.device
        if (dev != null) {
            packetHistory.addEntry(rssi, dev.rawAdvertisement)
        }
    }

    fun getDistance(): String? {
        val dev = _device.value.device ?: return null
        val txPower = dev.txPower ?: return null
        val distance = DistanceCalculator.estimateDistance(dev.rssi, txPower)
        return DistanceCalculator.formatDistance(distance)
    }
}

class DeviceWrapper(val device: BeaconDevice?)
