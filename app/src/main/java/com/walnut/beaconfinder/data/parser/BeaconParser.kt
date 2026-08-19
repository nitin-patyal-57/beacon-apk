package com.walnut.beaconfinder.data.parser

import android.bluetooth.le.ScanResult
import com.walnut.beaconfinder.data.model.BeaconDevice

interface BeaconParser {
    fun parse(scanResult: ScanResult): BeaconDevice?
}
