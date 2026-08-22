package com.walnut.beaconfinder

import android.content.Context
import com.walnut.beaconfinder.data.processing.DistanceCalculator
import com.walnut.beaconfinder.data.processing.RssiProcessor
import com.walnut.beaconfinder.data.processing.CooldownTracker
import com.walnut.beaconfinder.data.processing.BeaconPresenceTracker
import com.walnut.beaconfinder.data.processing.NearestBeaconTracker
import com.walnut.beaconfinder.data.processing.TtsManager
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.PresenceState
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProcessingTests {

    @Test
    fun `distance estimation with known txPower`() {
        val distance = DistanceCalculator.estimateDistance(-59, -59)
        assertEquals(1.0, distance, 0.5)
    }

    @Test
    fun `distance estimation with weaker signal`() {
        val distance = DistanceCalculator.estimateDistance(-79, -59)
        assertTrue(distance > 1.0)
    }

    @Test
    fun `distance formatting meters`() {
        val result = DistanceCalculator.formatDistance(2.5)
        assertTrue(result.contains("m"))
    }

    @Test
    fun `distance formatting centimeters`() {
        val result = DistanceCalculator.formatDistance(0.5)
        assertTrue(result.contains("cm"))
    }

    @Test
    fun `RssiProcessor moving average`() {
        val processor = RssiProcessor()
        processor.addSample(-50, 1000)
        processor.addSample(-60, 2000)
        processor.addSample(-70, 3000)
        val avg = processor.getMovingAverage(3)
        assertNotNull(avg)
        assertEquals(-60.0, avg!!, 0.1)
    }

    @Test
    fun `RssiProcessor getLatest`() {
        val processor = RssiProcessor()
        processor.addSample(-50, 1000)
        processor.addSample(-60, 2000)
        assertEquals(-60, processor.getLatest())
    }

    @Test
    fun `RssiProcessor time window filtering`() {
        val processor = RssiProcessor()
        val now = System.currentTimeMillis()
        processor.addSample(-50, now - 10000)
        processor.addSample(-60, now)
        val recent = processor.getSamplesWithinTimeWindow(5000)
        assertEquals(1, recent.size)
    }

    @Test
    fun `CooldownTracker prevents rapid notifications`() {
        val tracker = CooldownTracker()
        assertTrue(tracker.canNotify("beacon1", 1000))
        assertFalse(tracker.canNotify("beacon1", 1000))
    }

    @Test
    fun `CooldownTracker independent per beacon`() {
        val tracker = CooldownTracker()
        assertTrue(tracker.canNotify("beacon1", 1000))
        assertTrue(tracker.canNotify("beacon2", 1000))
    }

    @Test
    fun `CooldownTracker reset allows re-notification`() {
        val tracker = CooldownTracker()
        tracker.canNotify("beacon1", 1000)
        tracker.reset("beacon1")
        assertTrue(tracker.canNotify("beacon1", 1000))
    }

    @Test
    fun `Presence tracker first detection`() {
        val tracker = BeaconPresenceTracker()
        val beacon = createTestBeacon("AA:BB:CC:DD:EE:FF")
        val info = tracker.updatePresence(beacon, 30000)
        assertNotNull(info)
        assertEquals(PresenceState.NEARBY, info?.presenceState)
    }

    @Test
    fun `Presence tracker repeated detection stays NEARBY`() {
        val tracker = BeaconPresenceTracker()
        val beacon = createTestBeacon("AA:BB:CC:DD:EE:FF")
        tracker.updatePresence(beacon, 30000)
        val info2 = tracker.updatePresence(beacon, 30000)
        assertEquals(PresenceState.NEARBY, info2?.presenceState)
    }

    @Test
    fun `Presence timeout detection`() {
        val tracker = BeaconPresenceTracker()
        val beacon = createTestBeacon("AA:BB:CC:DD:EE:FF")
        tracker.updatePresence(beacon, 0)
        // Allow time to pass
        Thread.sleep(10)
        tracker.checkTimeouts(0)
        val info = tracker.getPresence(beacon.identityKey)
        assertEquals(PresenceState.LOST, info?.presenceState)
    }

    @Test
    fun `BeaconDevice identity key for generic ble`() {
        val device = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.GENERIC_BLE,
            rssi = -50
        )
        assertEquals("Generic:AA:BB:CC:DD:EE:FF", device.identityKey)
    }

    @Test
    fun `BeaconDevice identity key for iBeacon`() {
        val device = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.IBEACON,
            iBeaconUuid = "12345678-1234-1234-1234-123456789ABC",
            iBeaconMajor = 1,
            iBeaconMinor = 2,
            rssi = -50
        )
        assertEquals("iBeacon:12345678-1234-1234-1234-123456789ABC:1:2", device.identityKey)
    }

    @Test
    fun `BeaconDevice identity key for Eddystone UID`() {
        val device = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.EDDYSTONE_UID,
            eddystoneNamespace = "01020304050607080900",
            eddystoneInstance = "AABBCCDDEEFF",
            rssi = -50
        )
        assertEquals("EddystoneUID:01020304050607080900:AABBCCDDEEFF", device.identityKey)
    }

    @Test
    fun `BeaconDevice identity key for Eddystone URL`() {
        val device = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.EDDYSTONE_URL,
            eddystoneUrl = "https://example.com",
            rssi = -50
        )
        assertEquals("EddystoneURL:https://example.com", device.identityKey)
    }

    @Test
    fun `BeaconDevice distance calculation`() {
        val device = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.IBEACON,
            rssi = -59,
            txPower = -59
        )
        assertNotNull(device.distance)
    }

    private fun createTestBeacon(address: String) = BeaconDevice(
        address = address,
        protocol = BeaconProtocol.IBEACON,
        iBeaconUuid = "12345678-1234-1234-1234-123456789ABC",
        iBeaconMajor = 1,
        iBeaconMinor = 1,
        rssi = -50
    )

    // ==================== NearestBeaconTracker Hybrid (Packet + RSSI) Tests ====================

    @MockK lateinit var mockContext: Context
    @MockK lateinit var mockTts: TtsManager
    private lateinit var tracker: NearestBeaconTracker

    @Before
    fun setupTrackerTests() {
        MockKAnnotations.init(this)
        tracker = NearestBeaconTracker(mockContext, mockTts)
        tracker.setEnabled(true)
    }

    private fun createBeacon(address: String, rssi: Int) = BeaconDevice(
        address = address,
        protocol = BeaconProtocol.IBEACON,
        iBeaconUuid = "12345678-1234-1234-1234-123456789ABC",
        iBeaconMajor = 1,
        iBeaconMinor = 1,
        rssi = rssi
    )

    @Test
    fun `initial state is OUTSIDE`() {
        assertEquals(NearestBeaconTracker.RangeState.OUTSIDE, tracker.getRangeState())
    }

    @Test
    fun `packet with RSSI >= -80 transitions to IN_RANGE`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        val result = tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())
        assertNotNull(result)
    }

    @Test
    fun `packet with RSSI < -80 stays OUT`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -90)
        val result = tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.OUTSIDE, tracker.getRangeState())
        assertNull(result)
    }

    @Test
    fun `stays IN_RANGE while packets with good RSSI arrive`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        tracker.checkAndAnnounce(listOf(beacon))
        tracker.checkAndAnnounce(listOf(beacon))
        tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())
    }

    @Test
    fun `weak packet does not change state when IN_RANGE`() {
        val strong = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        tracker.checkAndAnnounce(listOf(strong))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())

        // Weak packet - should not exit immediately
        val weak = createBeacon("AA:BB:CC:DD:EE:FF", -90)
        tracker.checkAndAnnounce(listOf(weak))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())
    }

    @Test
    fun `returns null when disabled`() {
        tracker.setEnabled(false)
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        val result = tracker.checkAndAnnounce(listOf(beacon))
        assertNull(result)
    }

    @Test
    fun `ignores GENERIC_BLE devices`() {
        val genericBeacon = BeaconDevice(
            address = "AA:BB:CC:DD:EE:FF",
            protocol = BeaconProtocol.GENERIC_BLE,
            rssi = -50
        )
        val result = tracker.checkAndAnnounce(listOf(genericBeacon))
        assertNull(result)
        assertEquals(NearestBeaconTracker.RangeState.OUTSIDE, tracker.getRangeState())
    }

    @Test
    fun `reset clears all state`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())

        tracker.reset()
        assertEquals(NearestBeaconTracker.RangeState.OUTSIDE, tracker.getRangeState())
    }

    @Test
    fun `last in range packet time is updated only for strong packets`() {
        val strong = createBeacon("AA:BB:CC:DD:EE:FF", -70)
        val before = System.currentTimeMillis()
        tracker.checkAndAnnounce(listOf(strong))
        val after = System.currentTimeMillis()

        assertTrue(tracker.getLastInRangePacketTime() in before..after)

        // Weak packet should NOT update time
        val weak = createBeacon("AA:BB:CC:DD:EE:FF", -90)
        val timeBeforeWeak = tracker.getLastInRangePacketTime()
        tracker.checkAndAnnounce(listOf(weak))
        assertEquals(timeBeforeWeak, tracker.getLastInRangePacketTime())
    }

    @Test
    fun `boundary RSSI at -80 is accepted`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -80)
        val result = tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.IN_RANGE, tracker.getRangeState())
        assertNotNull(result)
    }

    @Test
    fun `boundary RSSI at -81 is rejected`() {
        val beacon = createBeacon("AA:BB:CC:DD:EE:FF", -81)
        val result = tracker.checkAndAnnounce(listOf(beacon))
        assertEquals(NearestBeaconTracker.RangeState.OUTSIDE, tracker.getRangeState())
        assertNull(result)
    }
}
