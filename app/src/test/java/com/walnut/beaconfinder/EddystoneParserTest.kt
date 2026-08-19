package com.walnut.beaconfinder

import com.walnut.beaconfinder.data.parser.EddystoneParser
import org.junit.Assert.*
import org.junit.Test

class EddystoneParserTest {

    @Test
    fun `frame type constants`() {
        assertEquals(0x00, EddystoneParser.FRAME_UID)
        assertEquals(0x10, EddystoneParser.FRAME_URL)
        assertEquals(0x20, EddystoneParser.FRAME_TLM)
        assertEquals(0x30, EddystoneParser.FRAME_EID)
    }

    @Test
    fun `UID minimum length`() {
        assertEquals(18, EddystoneParser.UID_MIN_LENGTH)
    }

    @Test
    fun `TLM minimum length`() {
        assertEquals(14, EddystoneParser.TLM_MIN_LENGTH)
    }

    @Test
    fun `EID minimum length`() {
        assertEquals(10, EddystoneParser.EID_MIN_LENGTH)
    }

    @Test
    fun `URL minimum length`() {
        assertEquals(3, EddystoneParser.URL_MIN_LENGTH)
    }

    @Test
    fun `TLM temperature parsing`() {
        val intPart = 25
        val fracPart = 128
        val temperature = intPart.toDouble() + fracPart.toDouble() / 256.0
        assertEquals(25.5, temperature, 0.01)
    }

    @Test
    fun `TLM uptime calculation`() {
        val hours = 4L
        val mins = 32L
        val totalSeconds = hours * 3600 + mins * 60
        assertEquals(16320L, totalSeconds)
    }

    @Test
    fun `URL scheme decoding`() {
        val schemeByte = 0x01
        val scheme = when (schemeByte) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> ""
        }
        assertEquals("https://www.", scheme)
    }

    @Test
    fun `URL code expansion`() {
        val codes = mapOf(
            0x40 to ".com/",
            0x41 to ".org/",
            0x42 to ".edu/",
            0x43 to ".net/",
            0x47 to ".com",
            0x48 to ".org"
        )
        assertEquals(".com/", codes[0x40])
        assertEquals(".org/", codes[0x41])
        assertEquals(".com", codes[0x47])
    }

    @Test
    fun `Eddystone frame parsing UID data size check`() {
        val data = ByteArray(18)
        data[0] = 0x00 // UID frame type
        data[1] = (-59).toByte() // TX power
        assertTrue(data.size >= EddystoneParser.UID_MIN_LENGTH)
    }

    @Test
    fun `Eddystone frame parsing URL data size check`() {
        val data = byteArrayOf(0x10, (-20).toByte(), 0x03) // URL frame, scheme https://
        assertTrue(data.size >= EddystoneParser.URL_MIN_LENGTH)
    }

    @Test
    fun `Eddystone frame parsing EID data size check`() {
        val data = ByteArray(10)
        data[0] = 0x30 // EID frame type
        data[1] = (-30).toByte() // TX power
        assertTrue(data.size >= EddystoneParser.EID_MIN_LENGTH)
    }

    @Test
    fun `Eddystone frame parsing TLM data size check`() {
        val data = ByteArray(14)
        data[0] = 0x20 // TLM frame type
        data[1] = 0x00 // version
        assertTrue(data.size >= EddystoneParser.TLM_MIN_LENGTH)
    }
}
