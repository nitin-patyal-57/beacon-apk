package com.walnut.beaconfinder.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.walnut.beaconfinder.data.model.ConnectionState
import com.walnut.beaconfinder.data.model.GattCharacteristicInfo
import com.walnut.beaconfinder.data.model.GattServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val handler = Handler(Looper.getMainLooper())

    private val connections = ConcurrentHashMap<String, BleGattConnection>()

    data class BleGattConnection(
        val address: String,
        val bluetoothGatt: BluetoothGatt,
        var connectionState: ConnectionState = ConnectionState.CONNECTING,
        var services: List<GattServiceInfo> = emptyList(),
        var connectAttemptTime: Long = System.currentTimeMillis(),
        var retryCount: Int = 0
    )

    private val _connectionState = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionState: StateFlow<Map<String, ConnectionState>> = _connectionState.asStateFlow()

    private val _discoveredServices = MutableStateFlow<Map<String, List<GattServiceInfo>>>(emptyMap())
    val discoveredServices: StateFlow<Map<String, List<GattServiceInfo>>> = _discoveredServices.asStateFlow()

    private val _characteristicValue = MutableStateFlow<Pair<String, ByteArray>?>(null)
    val characteristicValue: StateFlow<Pair<String, ByteArray>?> = _characteristicValue.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothGattCallback,
        autoConnect: Boolean = false,
        address: String
    ): Boolean {
        // Prevent duplicate connections
        val existing = connections[address]
        if (existing != null) {
            when (existing.connectionState) {
                ConnectionState.CONNECTING, ConnectionState.CONNECTED,
                ConnectionState.DISCOVERING_SERVICES, ConnectionState.READY -> {
                    Log.d(TAG, "Already connected/connecting to $address")
                    return false
                }
                else -> {}
            }
        }

        return true
    }

    @SuppressLint("MissingPermission")
    fun connectGatt(
        device: BluetoothDevice,
        autoConnect: Boolean = false,
        timeoutMs: Long = 10_000L
    ): BluetoothGatt? {
        val address = device.address

        if (Build.VERSION.SDK_INT >= 31) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted, cannot connect to $address")
                return null
            }
        }

        val existing = connections[address]
        if (existing != null && (existing.connectionState == ConnectionState.CONNECTING ||
                    existing.connectionState == ConnectionState.CONNECTED ||
                    existing.connectionState == ConnectionState.DISCOVERING_SERVICES ||
                    existing.connectionState == ConnectionState.READY)) {
            Log.d(TAG, "Already connected/connecting to $address")
            return existing.bluetoothGatt
        }

        updateState(address, ConnectionState.CONNECTING)

        return try {
            val gatt = device.connectGatt(context, autoConnect, gattCallback)
            if (gatt != null) {
                connections[address] = BleGattConnection(
                    address = address,
                    bluetoothGatt = gatt
                )

                // Connection timeout
                handler.postDelayed({
                    val conn = connections[address]
                    if (conn != null && conn.connectionState == ConnectionState.CONNECTING) {
                        Log.w(TAG, "Connection timeout for $address")
                        updateState(address, ConnectionState.FAILED)
                        disconnect(address)
                    }
                }, timeoutMs)
            }
            gatt
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to $address", e)
            updateState(address, ConnectionState.FAILED)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to $address", e)
            updateState(address, ConnectionState.FAILED)
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        val conn = connections[address] ?: return
        updateState(address, ConnectionState.DISCONNECTING)
        try {
            conn.bluetoothGatt.disconnect()
            handler.postDelayed({
                conn.bluetoothGatt.close()
                connections.remove(address)
                updateState(address, ConnectionState.DISCONNECTED)
            }, 500)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from $address", e)
            connections.remove(address)
            updateState(address, ConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(address: String, characteristic: BluetoothGattCharacteristic): Boolean {
        val conn = connections[address] ?: return false
        return try {
            conn.bluetoothGatt.readCharacteristic(characteristic)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading characteristic", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        address: String,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        val conn = connections[address] ?: return false
        return try {
            characteristic.value = value
            characteristic.writeType = writeType
            conn.bluetoothGatt.writeCharacteristic(characteristic)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing characteristic", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun setCharacteristicNotification(
        address: String,
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean
    ): Boolean {
        val conn = connections[address] ?: return false
        return try {
            conn.bluetoothGatt.setCharacteristicNotification(characteristic, enabled)
            // Write CCCD descriptor for notifications/indications
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = if (enabled) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                conn.bluetoothGatt.writeDescriptor(descriptor)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting notification", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun requestMtu(address: String, mtu: Int = 512): Boolean {
        val conn = connections[address] ?: return false
        return try {
            conn.bluetoothGatt.requestMtu(mtu)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun readRemoteRssi(address: String): Boolean {
        val conn = connections[address] ?: return false
        return try {
            conn.bluetoothGatt.readRemoteRssi()
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun setConnectionPriority(address: String, priority: Int): Boolean {
        val conn = connections[address] ?: return false
        return try {
            val connPriority = when (priority) {
                0 -> android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH
                1 -> android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                2 -> android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
                else -> android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED
            }
            conn.bluetoothGatt.requestConnectionPriority(connPriority)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting connection priority", e)
            false
        }
    }

    fun isConnected(address: String): Boolean {
        val conn = connections[address] ?: return false
        return conn.connectionState == ConnectionState.CONNECTED ||
                conn.connectionState == ConnectionState.DISCOVERING_SERVICES ||
                conn.connectionState == ConnectionState.READY
    }

    fun isConnecting(address: String): Boolean {
        val conn = connections[address] ?: return false
        return conn.connectionState == ConnectionState.CONNECTING
    }

    fun getConnection(address: String): BleGattConnection? = connections[address]

    private fun updateState(address: String, state: ConnectionState) {
        val conn = connections[address]
        if (conn != null) {
            connections[address] = conn.copy(connectionState = state)
        }
        _connectionState.value = connections.mapValues { it.value.connectionState }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            Log.d(TAG, "onConnectionStateChange: $address status=$status newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT error status=$status for $address")
                connections.remove(address)
                updateState(address, ConnectionState.FAILED)
                gatt.close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateState(address, ConnectionState.CONNECTED)
                    handler.postDelayed({
                        gatt.discoverServices()
                        updateState(address, ConnectionState.DISCOVERING_SERVICES)
                    }, 200)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connections.remove(address)
                    updateState(address, ConnectionState.DISCONNECTED)
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            Log.d(TAG, "onServicesDiscovered: $address status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services.map { service ->
                    GattServiceInfo(
                        uuid = android.os.ParcelUuid(service.uuid),
                        characteristics = service.characteristics.map { char ->
                            GattCharacteristicInfo(
                                uuid = android.os.ParcelUuid(char.uuid),
                                properties = char.properties,
                                descriptors = char.descriptors.map { android.os.ParcelUuid(it.uuid) }
                            )
                        }
                    )
                }
                val conn = connections[address]
                if (conn != null) {
                    connections[address] = conn.copy(services = services, connectionState = ConnectionState.READY)
                }
                updateState(address, ConnectionState.READY)
                _discoveredServices.value = _discoveredServices.value.toMutableMap().apply {
                    this[address] = services
                }
            } else {
                updateState(address, ConnectionState.CONNECTED)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            Log.d(TAG, "onCharacteristicRead: $address uuid=${characteristic.uuid} status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                _characteristicValue.value = address to (characteristic.value ?: byteArrayOf())
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            Log.d(TAG, "onCharacteristicWrite: $address uuid=${characteristic.uuid} status=$status")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val address = gatt.device.address
            Log.d(TAG, "onCharacteristicChanged: $address uuid=${characteristic.uuid}")
            _characteristicValue.value = address to (characteristic.value ?: byteArrayOf())
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val address = gatt.device.address
            Log.d(TAG, "onDescriptorWrite: $address status=$status")
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val address = gatt.device.address
            Log.d(TAG, "onMtuChanged: $address mtu=$mtu status=$status")
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val address = gatt.device.address
            Log.d(TAG, "onReadRemoteRssi: $address rssi=$rssi status=$status")
        }
    }

    companion object {
        private const val TAG = "BleConnectionManager"
        private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
