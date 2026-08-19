package com.walnut.beaconfinder

import com.walnut.beaconfinder.data.parser.IBeaconParser
import org.junit.Assert.*
import org.junit.Test

class IBeaconParserTest {

    @Test
    fun `iBeacon minimum length check`() {
        assertEquals(23, IBeaconParser.IBEACON_MIN_LENGTH)
    }

    @Test
    fun `Apple manufacturer ID is correct`() {
        assertEquals(0x004C, IBeaconParser.APPLE_MFG_ID)
    }

    @Test
    fun `iBeacon UUID format`() {
        val uuidBytes = byteArrayOf(
            0x74, 0x27, 0x8B.toByte(), 0xDA.toByte(),
            0xB6.toByte(), 0x44, 0x45, 0x20,
            0x8F.toByte(), 0x0C, 0x72, 0x0E,
            0xAF.toByte(), 0x05, 0x99.toByte(), 0x35
        )
        val sb = StringBuilder()
        for (i in uuidBytes.indices) {
            sb.append(String.format("%02X", uuidBytes[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append("-")
        }
        assertEquals("74278BDA-B644-4520-8F0C-720EAF059935", sb.toString())
    }

    @Test
    fun `iBeacon major minor extraction`() {
        val major = 100
        val minor = 20
        val bytes = byteArrayOf(
            (major shr 8).toByte(), (major and 0xFF).toByte(),
            (minor shr 8).toByte(), (minor and 0xFF).toByte()
        )
        val extractedMajor = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val extractedMinor = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        assertEquals(100, extractedMajor)
        assertEquals(20, extractedMinor)
    }
}
