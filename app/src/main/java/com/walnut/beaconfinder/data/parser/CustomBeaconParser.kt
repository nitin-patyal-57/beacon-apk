package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomBeaconParser @Inject constructor() {

    data class CustomFormat(
        val name: String,
        val manufacturerId: Int,
        val frameSignature: ByteArray? = null,
        val identifierOffset: Int = 0,
        val identifierLength: Int = 4,
        val serviceUuid: ParcelUuid? = null,
        val serviceDataPrefix: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CustomFormat) return false
            return name == other.name
        }

        override fun hashCode(): Int = name.hashCode()
    }

    fun parse(scanResult: ScanResult, formats: List<CustomFormat>): BeaconDevice? {
        val record = scanResult.scanRecord ?: return null

        for (format in formats) {
            val result = tryMatchManufacturerData(scanResult, record, format)
            if (result != null) return result

            val resultService = tryMatchServiceData(scanResult, record, format)
            if (resultService != null) return resultService
        }

        return null
    }

    private fun tryMatchManufacturerData(
        scanResult: ScanResult,
        record: android.bluetooth.le.ScanRecord,
        format: CustomFormat
    ): BeaconDevice? {
        val mfgData = record.getManufacturerSpecificData(format.manufacturerId) ?: return null

        if (format.frameSignature != null) {
            if (mfgData.size < format.frameSignature.size) return null
            for (i in format.frameSignature.indices) {
                if (mfgData[i] != format.frameSignature[i]) return null
            }
        }

        return BeaconDevice(
            bluetoothDevice = scanResult.device,
            address = scanResult.device.address,
            name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
            protocol = BeaconProtocol.CUSTOM_BLE,
            rssi = scanResult.rssi,
            manufacturerId = format.manufacturerId,
            manufacturerData = mfgData,
            serviceData = extractServiceData(record),
            serviceUuids = extractServiceUuids(record),
            rawAdvertisement = record.bytes ?: byteArrayOf(),
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            connectable = scanResult.isConnectable,
            isCustomFormat = true,
            customFormatName = format.name,
            advertiseFlags = record.advertiseFlags,
            txPowerLevel = record.txPowerLevel
        )
    }

    private fun tryMatchServiceData(
        scanResult: ScanResult,
        record: android.bluetooth.le.ScanRecord,
        format: CustomFormat
    ): BeaconDevice? {
        if (format.serviceUuid == null || format.serviceDataPrefix == null) return null

        val data = record.getServiceData(format.serviceUuid) ?: return null

        if (data.size < format.serviceDataPrefix.size) return null
        for (i in format.serviceDataPrefix.indices) {
            if (data[i] != format.serviceDataPrefix[i]) return null
        }

        return BeaconDevice(
            bluetoothDevice = scanResult.device,
            address = scanResult.device.address,
            name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
            protocol = BeaconProtocol.CUSTOM_BLE,
            rssi = scanResult.rssi,
            manufacturerId = null,
            serviceData = mapOf(format.serviceUuid to data),
            serviceUuids = extractServiceUuids(record),
            rawAdvertisement = record.bytes ?: byteArrayOf(),
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            connectable = scanResult.isConnectable,
            isCustomFormat = true,
            customFormatName = format.name,
            advertiseFlags = record.advertiseFlags,
            txPowerLevel = record.txPowerLevel
        )
    }

    private fun extractServiceData(record: android.bluetooth.le.ScanRecord): Map<ParcelUuid, ByteArray> {
        val map = mutableMapOf<ParcelUuid, ByteArray>()
        try {
            val uuids = record.serviceUuids ?: return map
            for (uuid in uuids) {
                val data = record.getServiceData(uuid) ?: continue
                map[uuid] = data
            }
        } catch (_: Exception) {}
        return map
    }

    private fun extractServiceUuids(record: android.bluetooth.le.ScanRecord): List<ParcelUuid> {
        return record.serviceUuids?.toList() ?: emptyList()
    }
}
