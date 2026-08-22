package com.walnut.beaconfinder.data.model

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid

data class BeaconDevice(
    val bluetoothDevice: BluetoothDevice? = null,
    val address: String = "",
    val name: String? = null,
    val protocol: BeaconProtocol = BeaconProtocol.GENERIC_BLE,
    val rssi: Int = 0,
    val txPower: Int? = null,
    val iBeaconUuid: String? = null,
    val iBeaconMajor: Int? = null,
    val iBeaconMinor: Int? = null,
    val eddystoneNamespace: String? = null,
    val eddystoneInstance: String? = null,
    val eddystoneUrl: String? = null,
    val eddystoneTlmVersion: Int? = null,
    val eddystoneBatteryVoltage: Int? = null,
    val eddystoneTemperature: Double? = null,
    val eddystoneAdvCount: Long? = null,
    val eddystoneTimeSinceBoot: Long? = null,
    val eddystoneEid: String? = null,
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val serviceData: Map<ParcelUuid, ByteArray> = emptyMap(),
    val serviceUuids: List<ParcelUuid> = emptyList(),
    val rawAdvertisement: ByteArray = byteArrayOf(),
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val connectable: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isCustomFormat: Boolean = false,
    val customFormatName: String? = null,
    val advertiseFlags: Int? = null,
    val txPowerLevel: Int? = null,
    val isInRange: Boolean = false
) {
    val displayName: String
        get() = name ?: address

    val identityKey: String
        get() = when (protocol) {
            BeaconProtocol.IBEACON -> "iBeacon:${iBeaconUuid ?: ""}:${iBeaconMajor ?: 0}:${iBeaconMinor ?: 0}"
            BeaconProtocol.EDDYSTONE_UID -> "EddystoneUID:${eddystoneNamespace ?: ""}:${eddystoneInstance ?: ""}"
            BeaconProtocol.EDDYSTONE_URL -> "EddystoneURL:${eddystoneUrl ?: ""}"
            BeaconProtocol.EDDYSTONE_TLM -> "EddystoneTLM:$address"
            BeaconProtocol.EDDYSTONE_EID -> "EddystoneEID:${eddystoneEid ?: address}"
            BeaconProtocol.CUSTOM_BLE -> "Custom:${manufacturerId ?: 0}:${address}"
            BeaconProtocol.GENERIC_BLE -> "Generic:$address"
        }

    val distance: Double?
        get() {
            if (txPower == null) return null
            val ratio = (txPower - rssi).toDouble() / (10 * 2.0)
            return if (ratio < 1.0) Math.pow(10.0, ratio) else Math.pow(10.0, ratio) * 0.89976
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BeaconDevice) return false
        return address == other.address && protocol == other.protocol &&
                rssi == other.rssi && isInRange == other.isInRange &&
                lastSeen == other.lastSeen && connectionState == other.connectionState
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + rssi
        result = 31 * result + isInRange.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + connectionState.hashCode()
        return result
    }
}
