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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearestBeaconTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager
) {
    private val rssiHistory = ConcurrentHashMap<String, MutableList<RssiSample>>()
    private val lastAnnounced = ConcurrentHashMap<String, Long>()
    private var enabled = false
    private var onNearestBeaconFound: ((BeaconDevice) -> Unit)? = null
    private var currentNearestAddress: String? = null
    private var startedAt: Long = 0L

    data class RssiSample(val rssi: Int, val timestamp: Long)

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            startedAt = System.currentTimeMillis()
            currentNearestAddress = null
        } else {
            currentNearestAddress = null
        }
    }

    fun setOnNearestBeaconFoundListener(listener: (BeaconDevice) -> Unit) {
        onNearestBeaconFound = listener
    }

    fun processDevice(device: BeaconDevice) {
        if (!enabled) return

        val history = rssiHistory.getOrPut(device.address) { mutableListOf() }
        synchronized(history) {
            history.add(RssiSample(device.rssi, System.currentTimeMillis()))
            val cutoff = System.currentTimeMillis() - HISTORY_WINDOW_MS
            history.removeAll { it.timestamp < cutoff }
        }
    }

    fun checkAndAnnounce(devices: List<BeaconDevice>): BeaconDevice? {
        if (!enabled) return null

        val now = System.currentTimeMillis()
        val recentAddresses = devices.filter { (now - it.lastSeen) < STALE_THRESHOLD_MS }.map { it.address }.toSet()

        rssiHistory.keys.filter { it !in recentAddresses }.forEach { staleAddr ->
            rssiHistory.remove(staleAddr)
            lastAnnounced.remove(staleAddr)
        }

        val recentBeacons = devices.filter {
            (now - it.lastSeen) < STALE_THRESHOLD_MS &&
            it.protocol != BeaconProtocol.GENERIC_BLE
        }
        if (recentBeacons.isEmpty()) {
            val warmupDone = (now - startedAt) > WARMUP_MS
            if (currentNearestAddress != null && warmupDone) {
                Log.d(TAG, "No recent beacons, stopping announcement")
                currentNearestAddress = null
                ttsManager.speak("Beacon out of range")
            }
            return null
        }

        val nearest = recentBeacons.maxByOrNull { device ->
            val history = rssiHistory[device.address]
            val avgRssi = if (history != null && history.isNotEmpty()) {
                synchronized(history) { history.map { it.rssi }.average() }
            } else {
                device.rssi.toDouble()
            }
            avgRssi
        } ?: return null

        val avgRssi = synchronized(rssiHistory[nearest.address] ?: ArrayList<RssiSample>()) {
            (rssiHistory[nearest.address]?.map { it.rssi }?.average() ?: nearest.rssi.toDouble())
        }

        Log.d(TAG, "Nearest iBeacon: ${nearest.displayName} avgRSSI=${avgRssi.toInt()} current=${nearest.rssi} connectable=${nearest.connectable}")

        if (avgRssi > PROXIMITY_THRESHOLD) {
            val now = System.currentTimeMillis()
            val lastTime = lastAnnounced[nearest.address]

            if (nearest.address != currentNearestAddress || (lastTime != null && (now - lastTime) > REANNOUNCE_INTERVAL_MS)) {
                currentNearestAddress = nearest.address
                lastAnnounced[nearest.address] = now
                announceBeacon(nearest, avgRssi.toInt())
            }
        } else {
            if (currentNearestAddress != null) {
                Log.d(TAG, "Nearest beacon signal too weak (${avgRssi.toInt()} dBm), going silent")
                currentNearestAddress = null
            }
        }

        onNearestBeaconFound?.invoke(nearest)
        return nearest
    }

    private fun announceBeacon(device: BeaconDevice, avgRssi: Int) {
        val beaconName = device.name ?: when (device.protocol) {
            BeaconProtocol.IBEACON -> "iBeacon ${device.iBeaconUuid?.take(8)}"
            BeaconProtocol.EDDYSTONE_UID -> "Eddystone ${device.eddystoneNamespace?.take(10)}"
            BeaconProtocol.EDDYSTONE_URL -> "Eddystone ${device.eddystoneUrl}"
            else -> device.address
        }
        val distance = estimateDistance(avgRssi)

        val proximity = when {
            distance < 1.0 -> "very close"
            distance < 3.0 -> "nearby"
            distance < 10.0 -> "in range"
            else -> "far"
        }

        val protocolName = when (device.protocol) {
            BeaconProtocol.IBEACON -> "iBeacon"
            BeaconProtocol.EDDYSTONE_UID -> "Eddystone UID"
            BeaconProtocol.EDDYSTONE_URL -> "Eddystone URL"
            BeaconProtocol.EDDYSTONE_TLM -> "Eddystone TLM"
            BeaconProtocol.EDDYSTONE_EID -> "Eddystone EID"
            BeaconProtocol.CUSTOM_BLE -> "Custom BLE"
            else -> "BLE device"
        }

        val ttsMessage = "$protocolName $beaconName detected, $proximity"
        Log.d(TAG, "Announcing: $ttsMessage")
        ttsManager.speak(ttsMessage)
        sendProximityNotification(device, beaconName, protocolName, avgRssi, proximity)
    }

    private fun estimateDistance(rssi: Int): Double {
        val txPower = -59
        val ratio = (txPower - rssi).toDouble() / (10 * 2.0)
        return if (ratio < 1.0) Math.pow(10.0, ratio) else Math.pow(10.0, ratio) * 0.89976
    }

    private fun sendProximityNotification(device: BeaconDevice, beaconName: String, protocolName: String, rssi: Int, proximity: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("DEVICE_ADDRESS", device.address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, device.address.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val details = when (device.protocol) {
            BeaconProtocol.IBEACON -> "UUID: ${device.iBeaconUuid?.take(8)}... M:${device.iBeaconMajor} m:${device.iBeaconMinor}"
            BeaconProtocol.EDDYSTONE_UID -> "NS: ${device.eddystoneNamespace?.take(10)}... Inst: ${device.eddystoneInstance}"
            BeaconProtocol.EDDYSTONE_URL -> "URL: ${device.eddystoneUrl}"
            else -> device.displayName
        }

        val notification = Notification.Builder(context, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("$protocolName $proximity: $beaconName")
            .setContentText("$details ($rssi dBm)")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_PROXIMITY + device.address.hashCode(), notification)
    }

    companion object {
        private const val TAG = "NearestBeaconTracker"
        private const val PROXIMITY_THRESHOLD = -90
        private const val REANNOUNCE_INTERVAL_MS = 10_000L
        private const val HISTORY_WINDOW_MS = 10_000L
        private const val STALE_THRESHOLD_MS = 8_000L
        private const val WARMUP_MS = 5_000L
        private const val NOTIFICATION_ID_PROXIMITY = 3000
    }
}
