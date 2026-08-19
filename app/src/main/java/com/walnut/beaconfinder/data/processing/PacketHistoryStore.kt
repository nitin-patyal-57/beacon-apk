package com.walnut.beaconfinder.data.processing

import com.walnut.beaconfinder.data.model.RssiSample
import java.util.concurrent.ConcurrentLinkedDeque

class PacketHistoryStore(private val maxPackets: Int = 500) {
    private val history = ConcurrentLinkedDeque<PacketEntry>()

    data class PacketEntry(
        val timestamp: Long,
        val rssi: Int,
        val rawAdvertisement: ByteArray,
        val hexDump: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PacketEntry) return false
            return timestamp == other.timestamp
        }

        override fun hashCode(): Int = timestamp.hashCode()
    }

    fun addEntry(rssi: Int, rawAdvertisement: ByteArray) {
        val hexDump = rawAdvertisement.joinToString(" ") {
            String.format("%02X", it)
        }
        history.addLast(PacketEntry(System.currentTimeMillis(), rssi, rawAdvertisement, hexDump))
        while (history.size > maxPackets) {
            history.pollFirst()
        }
    }

    fun getHistory(): List<PacketEntry> = history.toList()

    fun getLatest(): PacketEntry? = history.lastOrNull()

    fun clear() = history.clear()

    fun calculateAdvertisingInterval(): Long? {
        val entries = history.toList()
        if (entries.size < 2) return null
        val intervals = mutableListOf<Long>()
        for (i in 1 until entries.size) {
            intervals.add(entries[i].timestamp - entries[i - 1].timestamp)
        }
        return intervals.average().toLong()
    }
}
