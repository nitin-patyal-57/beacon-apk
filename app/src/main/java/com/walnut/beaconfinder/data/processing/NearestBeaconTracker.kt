package com.walnut.beaconfinder.data.processing

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.walnut.beaconfinder.BeaconFinderApp
import com.walnut.beaconfinder.MainActivity
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearestBeaconTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager
) {

    enum class RangeState { UNKNOWN, IN_RANGE, TEMPORARILY_LOST, OUT_OF_RANGE }

    private var enabled = false
    private var onNearestBeaconFound: ((BeaconDevice) -> Unit)? = null
    private var onOutOfRangeConfirmed: ((String) -> Unit)? = null

    private var trackedAddress: String? = null
    private var lastSpoken: String? = null
    private var trackedDevice: BeaconDevice? = null
    private var lastSpokenAt: Long = 0L

    private var rangeState = RangeState.UNKNOWN
    private var confirmedLostAt: Long = 0L

    private var inRangeCount = 0
    private var outOfRangeCount = 0

    fun reset() {
        trackedAddress = null
        lastSpoken = null
        trackedDevice = null
        lastSpokenAt = 0L
        rangeState = RangeState.UNKNOWN
        confirmedLostAt = 0L
        inRangeCount = 0
        outOfRangeCount = 0
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) reset()
    }

    fun setOnNearestBeaconFoundListener(listener: (BeaconDevice) -> Unit) {
        onNearestBeaconFound = listener
    }

    fun setOnOutOfRangeConfirmedListener(listener: (String) -> Unit) {
        onOutOfRangeConfirmed = listener
    }

    fun checkAndAnnounce(devices: List<BeaconDevice>): BeaconDevice? {
        if (!enabled) return null

        val now = System.currentTimeMillis()

        val nearest = devices
            .filter { it.protocol == BeaconProtocol.IBEACON ||
                it.protocol == BeaconProtocol.EDDYSTONE_UID ||
                it.protocol == BeaconProtocol.EDDYSTONE_URL ||
                it.protocol == BeaconProtocol.EDDYSTONE_TLM ||
                it.protocol == BeaconProtocol.EDDYSTONE_EID }
            .maxByOrNull { it.rssi }

        if (nearest == null) {
            handleNoBeacon(now)
            return null
        }

        val rssi = nearest.rssi
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "RSSI=$rssi state=$rangeState inCount=$inRangeCount outCount=$outOfRangeCount")

        when {
            rssi >= IN_RANGE_RSSI -> {
                outOfRangeCount = 0
                inRangeCount++
                if (inRangeCount >= CONFIRMATION_COUNT) {
                    handleConfirmedInRange(nearest, now)
                }
            }
            rssi <= OUT_OF_RANGE_RSSI -> {
                inRangeCount = 0
                outOfRangeCount++
                if (outOfRangeCount >= CONFIRMATION_COUNT) {
                    handleConfirmedOutOfRange(now)
                }
            }
            else -> {
                inRangeCount = 0
                outOfRangeCount = 0
                handleSilentZone(now)
            }
        }

        return if (rangeState == RangeState.IN_RANGE) nearest else null
    }

    private fun handleConfirmedInRange(device: BeaconDevice, now: Long) {
        when (rangeState) {
            RangeState.UNKNOWN, RangeState.OUT_OF_RANGE -> {
                rangeState = RangeState.IN_RANGE
                trackedAddress = device.address
                trackedDevice = device
                if (canSpeak() && lastSpoken != "in range") {
                    lastSpoken = "in range"
                    lastSpokenAt = now
                    Log.d(TAG, "Announcing in range: ${device.displayName} RSSI=${device.rssi}")
                    announceBeacon(device, "in range")
                }
                onNearestBeaconFound?.invoke(device)
            }
            RangeState.TEMPORARILY_LOST -> {
                rangeState = RangeState.IN_RANGE
                confirmedLostAt = 0L
                trackedAddress = device.address
                trackedDevice = device
                Log.d(TAG, "Beacon returned, back to IN_RANGE")
                onNearestBeaconFound?.invoke(device)
            }
            RangeState.IN_RANGE -> {
                trackedAddress = device.address
                trackedDevice = device
                onNearestBeaconFound?.invoke(device)
            }
        }
    }

    private fun handleConfirmedOutOfRange(now: Long) {
        when (rangeState) {
            RangeState.IN_RANGE -> {
                rangeState = RangeState.TEMPORARILY_LOST
                confirmedLostAt = now
                Log.d(TAG, "RSSI confirmed low, entering TEMPORARILY_LOST")
            }
            RangeState.TEMPORARILY_LOST -> {
                if (now - confirmedLostAt >= OUT_OF_RANGE_TIMEOUT_MS) {
                    rangeState = RangeState.OUT_OF_RANGE
                    confirmedLostAt = 0L
                    inRangeCount = 0
                    outOfRangeCount = 0
                    if (canSpeak() && lastSpoken != "out of range") {
                        lastSpoken = "out of range"
                        lastSpokenAt = now
                        Log.d(TAG, "Confirmed out of range")
                        ttsManager.speak("Beacon out of range")
                        trackedAddress?.let { onOutOfRangeConfirmed?.invoke(it) }
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleSilentZone(now: Long) {
        when (rangeState) {
            RangeState.IN_RANGE -> {
                rangeState = RangeState.TEMPORARILY_LOST
                confirmedLostAt = now
                Log.d(TAG, "RSSI in silent zone, entering TEMPORARILY_LOST")
            }
            RangeState.TEMPORARILY_LOST -> {
                if (now - confirmedLostAt >= OUT_OF_RANGE_TIMEOUT_MS) {
                    rangeState = RangeState.OUT_OF_RANGE
                    confirmedLostAt = 0L
                    inRangeCount = 0
                    outOfRangeCount = 0
                    if (canSpeak() && lastSpoken != "out of range") {
                        lastSpoken = "out of range"
                        lastSpokenAt = now
                        Log.d(TAG, "Confirmed out of range from silent zone")
                        ttsManager.speak("Beacon out of range")
                        trackedAddress?.let { onOutOfRangeConfirmed?.invoke(it) }
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleNoBeacon(now: Long) {
        inRangeCount = 0
        outOfRangeCount = 0
        when (rangeState) {
            RangeState.TEMPORARILY_LOST -> {
                if (now - confirmedLostAt >= OUT_OF_RANGE_TIMEOUT_MS) {
                    rangeState = RangeState.OUT_OF_RANGE
                    confirmedLostAt = 0L
                    if (canSpeak() && lastSpoken != "out of range") {
                        lastSpoken = "out of range"
                        lastSpokenAt = now
                        Log.d(TAG, "No beacon seen, confirmed out of range")
                        ttsManager.speak("Beacon out of range")
                        trackedAddress?.let { onOutOfRangeConfirmed?.invoke(it) }
                    }
                }
            }
            RangeState.IN_RANGE -> {
                rangeState = RangeState.TEMPORARILY_LOST
                confirmedLostAt = now
                Log.d(TAG, "Beacon disappeared, entering TEMPORARILY_LOST")
            }
            else -> {}
        }
    }

    private fun canSpeak(): Boolean {
        return (System.currentTimeMillis() - lastSpokenAt) >= COOLDOWN_MS
    }

    fun getRangeState(): RangeState = rangeState

    private fun announceBeacon(device: BeaconDevice, proximity: String) {
        val beaconName = device.name?.takeIf { it.isNotBlank() } ?: when (device.protocol) {
            BeaconProtocol.IBEACON -> "Unknown iBeacon"
            BeaconProtocol.EDDYSTONE_UID -> "Unknown Eddystone"
            BeaconProtocol.EDDYSTONE_URL -> "Unknown Eddystone"
            else -> "Unknown beacon"
        }

        val ttsMessage = "$beaconName, $proximity"
        Log.d(TAG, "TTS: $ttsMessage (RSSI=${device.rssi})")
        ttsManager.speak(ttsMessage)
        sendProximityNotification(device, beaconName, device.rssi, proximity)
    }

    private fun sendProximityNotification(device: BeaconDevice, beaconName: String, rssi: Int, proximity: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("DEVICE_ADDRESS", device.address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, device.address.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("$beaconName - $proximity")
            .setContentText("Signal: $rssi dBm")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_PROXIMITY + device.address.hashCode(), notification)
    }

    companion object {
        private const val TAG = "NearestBeaconTracker"
        private const val COOLDOWN_MS = 5_000L
        private const val NOTIFICATION_ID_PROXIMITY = 3000
        const val IN_RANGE_RSSI = -60
        const val OUT_OF_RANGE_RSSI = -90
        const val OUT_OF_RANGE_TIMEOUT_MS = 10_000L
        const val CONFIRMATION_COUNT = 2
    }
}
