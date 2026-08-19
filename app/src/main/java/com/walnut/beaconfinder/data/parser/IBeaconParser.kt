package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IBeaconParser @Inject constructor() : BeaconParser {

    override fun parse(scanResult: ScanResult): BeaconDevice? {
        val record = scanResult.scanRecord ?: return null
        val mfgData = record.getManufacturerSpecificData() ?: return null

        val appleData = mfgData.get(APPLE_MFG_ID) ?: return null

        if (appleData.size < IBEACON_MIN_LENGTH) return null
        if (appleData[0] != 0x02.toByte() || appleData[1] != 0x15.toByte()) return null

        return try {
            val uuidBytes = appleData.copyOfRange(2, 18)
            val uuid = bytesToUuid(uuidBytes)
            val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
            val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
            val txPower = appleData[22].toInt().toByte().toInt()

            BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = record.deviceName ?: scanResult.device.name,
                protocol = BeaconProtocol.IBEACON,
                rssi = scanResult.rssi,
                txPower = txPower,
                iBeaconUuid = uuid,
                iBeaconMajor = major,
                iBeaconMinor = minor,
                manufacturerId = APPLE_MFG_ID,
                manufacturerData = appleData,
                serviceData = extractServiceData(record),
                serviceUuids = extractServiceUuids(record),
                rawAdvertisement = record.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable,
                advertiseFlags = record.advertiseFlags,
                txPowerLevel = record.txPowerLevel
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToUuid(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            sb.append(String.format("%02X", bytes[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append("-")
        }
        return sb.toString()
    }

    private fun extractServiceData(record: android.bluetooth.le.ScanRecord): Map<ParcelUuid, ByteArray> {
        val map = mutableMapOf<ParcelUuid, ByteArray>()
        try {
            val serviceUuids = record.serviceUuids ?: return map
            for (uuid in serviceUuids) {
                val data = record.getServiceData(uuid) ?: continue
                map[uuid] = data
            }
        } catch (_: Exception) {}
        return map
    }

    private fun extractServiceUuids(record: android.bluetooth.le.ScanRecord): List<ParcelUuid> {
        return record.serviceUuids?.toList() ?: emptyList()
    }

    companion object {
        const val APPLE_MFG_ID = 0x004C
        const val IBEACON_MIN_LENGTH = 23
    }
}
