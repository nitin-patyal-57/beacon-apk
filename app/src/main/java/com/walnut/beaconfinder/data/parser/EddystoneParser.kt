package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EddystoneParser @Inject constructor() : BeaconParser {

    override fun parse(scanResult: ScanResult): BeaconDevice? {
        val record = scanResult.scanRecord ?: return null
        val serviceData = record.getServiceData(EDDYSTONE_UUID) ?: return null

        if (serviceData.isEmpty()) return null

        val frameType = serviceData[0].toInt() and 0xFF

        return when (frameType) {
            FRAME_UID -> parseUid(scanResult, serviceData)
            FRAME_URL -> parseUrl(scanResult, serviceData)
            FRAME_TLM -> parseTlm(scanResult, serviceData)
            FRAME_EID -> parseEid(scanResult, serviceData)
            else -> null
        }
    }

    private fun parseUid(scanResult: ScanResult, data: ByteArray): BeaconDevice? {
        if (data.size < UID_MIN_LENGTH) return null
        return try {
            val txPower = data[1].toInt().toByte().toInt()
            val namespace = data.copyOfRange(2, 12).joinToString("") { String.format("%02X", it) }
            val instance = data.copyOfRange(12, 18).joinToString("") { String.format("%02X", it) }

            BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
                protocol = BeaconProtocol.EDDYSTONE_UID,
                rssi = scanResult.rssi,
                txPower = txPower,
                eddystoneNamespace = namespace,
                eddystoneInstance = instance,
                serviceData = mapOf(EDDYSTONE_UUID to data),
                serviceUuids = extractServiceUuids(scanResult.scanRecord),
                rawAdvertisement = scanResult.scanRecord?.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable,
                advertiseFlags = scanResult.scanRecord?.advertiseFlags,
                txPowerLevel = scanResult.scanRecord?.txPowerLevel
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUrl(scanResult: ScanResult, data: ByteArray): BeaconDevice? {
        if (data.size < URL_MIN_LENGTH) return null
        return try {
            val txPower = data[1].toInt().toByte().toInt()
            val urlScheme = when (data[2].toInt() and 0xFF) {
                0x00 -> "http://www."
                0x01 -> "https://www."
                0x02 -> "http://"
                0x03 -> "https://"
                else -> ""
            }

            val sb = StringBuilder(urlScheme)
            for (i in 3 until data.size) {
                val b = data[i].toInt() and 0xFF
                when {
                    b in 0x00..0x23 -> sb.append(URL_CODE_MAP[b] ?: "")
                    b == 0x40 -> sb.append(".com/")
                    b == 0x41 -> sb.append(".org/")
                    b == 0x42 -> sb.append(".edu/")
                    b == 0x43 -> sb.append(".net/")
                    b == 0x44 -> sb.append(".info/")
                    b == 0x45 -> sb.append(".biz/")
                    b == 0x46 -> sb.append(".gov/")
                    b == 0x47 -> sb.append(".com")
                    b == 0x48 -> sb.append(".org")
                    b == 0x49 -> sb.append(".edu")
                    b == 0x4A -> sb.append(".net")
                    b == 0x4B -> sb.append(".info")
                    b == 0x4C -> sb.append(".biz")
                    b == 0x4D -> sb.append(".gov")
                    else -> sb.append(b.toChar())
                }
            }

            BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
                protocol = BeaconProtocol.EDDYSTONE_URL,
                rssi = scanResult.rssi,
                txPower = txPower,
                eddystoneUrl = sb.toString(),
                serviceData = mapOf(EDDYSTONE_UUID to data),
                serviceUuids = extractServiceUuids(scanResult.scanRecord),
                rawAdvertisement = scanResult.scanRecord?.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable,
                advertiseFlags = scanResult.scanRecord?.advertiseFlags,
                txPowerLevel = scanResult.scanRecord?.txPowerLevel
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTlm(scanResult: ScanResult, data: ByteArray): BeaconDevice? {
        if (data.size < TLM_MIN_LENGTH) return null
        return try {
            val version = data[1].toInt() and 0xFF
            val batteryVoltage = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val temperature = data[4].toInt().toDouble() + (data[5].toInt() and 0xFF).toDouble() / 256.0
            val advCount = ((data[6].toInt() and 0xFF).toLong() shl 24) or
                    ((data[7].toInt() and 0xFF).toLong() shl 16) or
                    ((data[8].toInt() and 0xFF).toLong() shl 8) or
                    (data[9].toInt() and 0xFF).toLong()
            val timeSinceBoot = ((data[10].toInt() and 0xFF).toLong() shl 24) or
                    ((data[11].toInt() and 0xFF).toLong() shl 16) or
                    ((data[12].toInt() and 0xFF).toLong() shl 8) or
                    (data[13].toInt() and 0xFF).toLong()

            BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
                protocol = BeaconProtocol.EDDYSTONE_TLM,
                rssi = scanResult.rssi,
                eddystoneTlmVersion = version,
                eddystoneBatteryVoltage = batteryVoltage,
                eddystoneTemperature = temperature,
                eddystoneAdvCount = advCount,
                eddystoneTimeSinceBoot = timeSinceBoot,
                serviceData = mapOf(EDDYSTONE_UUID to data),
                serviceUuids = extractServiceUuids(scanResult.scanRecord),
                rawAdvertisement = scanResult.scanRecord?.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable,
                advertiseFlags = scanResult.scanRecord?.advertiseFlags,
                txPowerLevel = scanResult.scanRecord?.txPowerLevel
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEid(scanResult: ScanResult, data: ByteArray): BeaconDevice? {
        if (data.size < EID_MIN_LENGTH) return null
        return try {
            val txPower = data[1].toInt().toByte().toInt()
            val eid = data.copyOfRange(2, 10).joinToString("") { String.format("%02X", it) }

            BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = scanResult.scanRecord?.deviceName ?: scanResult.device.name,
                protocol = BeaconProtocol.EDDYSTONE_EID,
                rssi = scanResult.rssi,
                txPower = txPower,
                eddystoneEid = eid,
                serviceData = mapOf(EDDYSTONE_UUID to data),
                serviceUuids = extractServiceUuids(scanResult.scanRecord),
                rawAdvertisement = scanResult.scanRecord?.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable,
                advertiseFlags = scanResult.scanRecord?.advertiseFlags,
                txPowerLevel = scanResult.scanRecord?.txPowerLevel
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractServiceUuids(record: android.bluetooth.le.ScanRecord?): List<ParcelUuid> {
        return record?.serviceUuids?.toList() ?: emptyList()
    }

    companion object {
        val EDDYSTONE_UUID: ParcelUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

        const val FRAME_UID = 0x00
        const val FRAME_URL = 0x10
        const val FRAME_TLM = 0x20
        const val FRAME_EID = 0x30

        const val UID_MIN_LENGTH = 18
        const val URL_MIN_LENGTH = 3
        const val TLM_MIN_LENGTH = 14
        const val EID_MIN_LENGTH = 10

        private val URL_CODE_MAP = mapOf(
            0x00 to ".",
            0x01 to ".",
            0x02 to ".",
            0x03 to "/",
            0x04 to ":",
            0x05 to "/",
            0x06 to ".",
            0x07 to ".",
            0x08 to ".",
            0x09 to ".",
            0x0A to "0",
            0x0B to "1",
            0x0C to "2",
            0x0D to "3",
            0x0E to "4",
            0x0F to "5",
            0x10 to "6",
            0x11 to "7",
            0x12 to "8",
            0x13 to "9",
            0x14 to "-",
            0x15 to ".",
            0x16 to "/",
            0x17 to "?",
            0x18 to ",",
            0x19 to ".",
            0x1A to "_",
            0x1B to "-",
            0x1C to "~",
            0x1D to "?",
            0x1E to "?",
            0x1F to "?",
            0x20 to "?"
        )
    }
}
