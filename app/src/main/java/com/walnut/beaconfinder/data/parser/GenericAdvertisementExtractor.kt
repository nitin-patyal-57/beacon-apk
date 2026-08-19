package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenericAdvertisementExtractor @Inject constructor() {

    fun extract(scanResult: ScanResult): BeaconDevice {
        val record = scanResult.scanRecord

        return BeaconDevice(
            bluetoothDevice = scanResult.device,
            address = scanResult.device.address,
            name = record?.deviceName ?: scanResult.device.name,
            protocol = BeaconProtocol.GENERIC_BLE,
            rssi = scanResult.rssi,
            txPower = record?.txPowerLevel,
            manufacturerId = extractManufacturerId(record),
            manufacturerData = extractManufacturerData(record),
            serviceData = extractServiceData(record),
            serviceUuids = extractServiceUuids(record),
            rawAdvertisement = record?.bytes ?: byteArrayOf(),
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            connectable = scanResult.isConnectable,
            advertiseFlags = record?.advertiseFlags,
            txPowerLevel = record?.txPowerLevel
        )
    }

    private fun extractManufacturerId(record: android.bluetooth.le.ScanRecord?): Int? {
        val mfgData = record?.getManufacturerSpecificData() ?: return null
        return if (mfgData.size() > 0) mfgData.keyAt(0) else null
    }

    private fun extractManufacturerData(record: android.bluetooth.le.ScanRecord?): ByteArray? {
        val mfgData = record?.getManufacturerSpecificData() ?: return null
        return if (mfgData.size() > 0) mfgData.valueAt(0) else null
    }

    private fun extractServiceData(record: android.bluetooth.le.ScanRecord?): Map<ParcelUuid, ByteArray> {
        val map = mutableMapOf<ParcelUuid, ByteArray>()
        try {
            val uuids = record?.serviceUuids ?: return map
            for (uuid in uuids) {
                val data = record.getServiceData(uuid) ?: continue
                map[uuid] = data
            }
        } catch (_: Exception) {}
        return map
    }

    private fun extractServiceUuids(record: android.bluetooth.le.ScanRecord?): List<ParcelUuid> {
        return record?.serviceUuids?.toList() ?: emptyList()
    }
}
