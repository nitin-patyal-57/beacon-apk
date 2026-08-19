package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeaconParserEngine @Inject constructor(
    private val iBeaconParser: IBeaconParser,
    private val eddystoneParser: EddystoneParser,
    private val customBeaconParser: CustomBeaconParser,
    private val genericExtractor: GenericAdvertisementExtractor
) {

    private var customFormats: List<CustomBeaconParser.CustomFormat> = emptyList()

    fun setCustomFormats(formats: List<CustomBeaconParser.CustomFormat>) {
        customFormats = formats
    }

    fun parse(scanResult: ScanResult): BeaconDevice {
        // Priority 1: iBeacon
        try {
            val iBeacon = iBeaconParser.parse(scanResult)
            if (iBeacon != null) {
                Log.d(TAG, "iBeacon detected: ${iBeacon.iBeaconUuid} Major:${iBeacon.iBeaconMajor} Minor:${iBeacon.iBeaconMinor}")
                return iBeacon
            }
        } catch (e: Exception) {
            Log.e(TAG, "iBeacon parse error", e)
        }

        // Priority 2: Eddystone
        try {
            val eddystone = eddystoneParser.parse(scanResult)
            if (eddystone != null) {
                Log.d(TAG, "Eddystone detected: ${eddystone.protocol} Namespace:${eddystone.eddystoneNamespace}")
                return eddystone
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eddystone parse error", e)
        }

        // Priority 3: Custom BLE
        if (customFormats.isNotEmpty()) {
            try {
                val custom = customBeaconParser.parse(scanResult, customFormats)
                if (custom != null) {
                    Log.d(TAG, "Custom BLE detected: ${custom.customFormatName}")
                    return custom
                }
            } catch (e: Exception) {
                Log.e(TAG, "Custom parse error", e)
            }
        }

        // Priority 4: Generic BLE fallback - NEVER discard
        try {
            val generic = genericExtractor.extract(scanResult)
            Log.d(TAG, "Generic BLE: ${generic.address} Name:${generic.name}")
            return generic
        } catch (e: Exception) {
            Log.e(TAG, "Generic extract error", e)
            // Absolute fallback - create minimal BeaconDevice from raw data
            return BeaconDevice(
                bluetoothDevice = scanResult.device,
                address = scanResult.device.address,
                name = scanResult.device.name,
                protocol = BeaconProtocol.GENERIC_BLE,
                rssi = scanResult.rssi,
                rawAdvertisement = scanResult.scanRecord?.bytes ?: byteArrayOf(),
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                connectable = scanResult.isConnectable
            )
        }
    }

    companion object {
        private const val TAG = "BeaconParserEngine"
    }
}
