package com.walnut.beaconfinder.data.processing

import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.PresenceState
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeaconPresenceTracker @Inject constructor() {

    data class PresenceInfo(
        val identityKey: String,
        val beaconName: String,
        var presenceState: PresenceState = PresenceState.NOT_PRESENT,
        var lastSeenTimestamp: Long = 0L,
        var firstDetectedTimestamp: Long = 0L,
        var notificationSent: Boolean = false,
        var previousState: PresenceState = PresenceState.NOT_PRESENT
    )

    private val presenceMap = mutableMapOf<String, PresenceInfo>()

    fun updatePresence(
        beacon: BeaconDevice,
        timeoutMs: Long = 30_000L,
        minRssi: Int = Int.MIN_VALUE
    ): PresenceInfo? {
        val identity = beacon.identityKey
        val existing = presenceMap[identity]

        if (beacon.rssi < minRssi) return existing

        val now = System.currentTimeMillis()

        if (existing == null) {
            val info = PresenceInfo(
                identityKey = identity,
                beaconName = beacon.displayName,
                presenceState = PresenceState.NEARBY,
                lastSeenTimestamp = now,
                firstDetectedTimestamp = now,
                notificationSent = false,
                previousState = PresenceState.NOT_PRESENT
            )
            presenceMap[identity] = info
            Log.d(TAG, "First detection: ${beacon.displayName} -> NEARBY")
            return info
        }

        existing.lastSeenTimestamp = now
        existing.previousState = existing.presenceState

        when (existing.presenceState) {
            PresenceState.NOT_PRESENT, PresenceState.LOST -> {
                existing.presenceState = PresenceState.RE_ENTERED
                existing.notificationSent = false
                Log.d(TAG, "Re-entry: ${beacon.displayName} -> RE_ENTERED")
            }
            PresenceState.RE_ENTERED -> {
                existing.presenceState = PresenceState.NEARBY
                Log.d(TAG, "Now nearby: ${beacon.displayName} -> NEARBY")
            }
            PresenceState.NEARBY -> {
                // Stay nearby
            }
        }

        return existing
    }

    fun checkTimeouts(timeoutMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        for ((key, info) in presenceMap) {
            if (info.presenceState != PresenceState.NOT_PRESENT &&
                (now - info.lastSeenTimestamp) > timeoutMs
            ) {
                info.previousState = info.presenceState
                info.presenceState = PresenceState.LOST
                Log.d(TAG, "Timeout: ${info.beaconName} -> LOST")
            }
        }
    }

    fun getPresence(identityKey: String): PresenceInfo? = presenceMap[identityKey]

    fun getAllPresence(): Map<String, PresenceInfo> = presenceMap.toMap()

    fun remove(identityKey: String) = presenceMap.remove(identityKey)

    fun clear() = presenceMap.clear()

    companion object {
        private const val TAG = "BeaconPresenceTracker"
    }
}
