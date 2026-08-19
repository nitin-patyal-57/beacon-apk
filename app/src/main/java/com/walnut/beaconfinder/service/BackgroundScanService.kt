package com.walnut.beaconfinder.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.walnut.beaconfinder.BeaconFinderApp
import com.walnut.beaconfinder.MainActivity
import com.walnut.beaconfinder.R
import com.walnut.beaconfinder.data.db.BeaconDatabase
import com.walnut.beaconfinder.data.db.KnownBeaconEntity
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.PresenceState
import com.walnut.beaconfinder.data.parser.BeaconParserEngine
import com.walnut.beaconfinder.data.parser.CustomBeaconParser
import com.walnut.beaconfinder.data.processing.BeaconPresenceTracker
import com.walnut.beaconfinder.data.processing.CooldownTracker
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class BackgroundScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val parserEngine = BeaconParserEngine(
        com.walnut.beaconfinder.data.parser.IBeaconParser(),
        com.walnut.beaconfinder.data.parser.EddystoneParser(),
        CustomBeaconParser(),
        com.walnut.beaconfinder.data.parser.GenericAdvertisementExtractor()
    )
    private val presenceTracker = BeaconPresenceTracker()
    private val cooldownTracker = CooldownTracker()
    private var knownBeacons = listOf<KnownBeaconEntity>()

    private val discoveredBeacons = ConcurrentHashMap<String, Long>()
    private val BEACON_DISCOVERY_COOLDOWN_MS = 15_000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundScanService created")
        try {
            startForeground(NOTIFICATION_ID, createNotification("Monitoring active"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot start foreground service - missing permissions", e)
            stopSelf()
            return
        }
        scope.launch {
            loadKnownBeacons()
            loadCustomFormats()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBackgroundScan()
            ACTION_STOP -> stopBackgroundScan()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBackgroundScan()
        scope.cancel()
        super.onDestroy()
    }

    private fun loadKnownBeacons() {
        scope.launch {
            val db = BeaconDatabase.getInstance(applicationContext)
            knownBeacons = db.knownBeaconDao().getAll()
        }
    }

    private fun loadCustomFormats() {
        scope.launch {
            val db = BeaconDatabase.getInstance(applicationContext)
            val formats = db.customFormatDao().getAll().map { entity ->
                CustomBeaconParser.CustomFormat(
                    name = entity.name,
                    manufacturerId = entity.manufacturerId,
                    frameSignature = entity.frameSignatureHex?.let { hexToBytes(it) },
                    identifierOffset = entity.identifierOffset,
                    identifierLength = entity.identifierLength
                )
            }
            parserEngine.setCustomFormats(formats)
        }
    }

    private fun startBackgroundScan() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner unavailable")
            stopSelf()
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Background scan failed: $errorCode")
                updateNotification("Scan failed. Restarting...")
                scope.launch {
                    delay(5000)
                    startBackgroundScan()
                }
            }
        }

        try {
            scanner?.startScan(null, settings, scanCallback)
            updateNotification("Monitoring ${knownBeacons.size} known beacons")
            Log.d(TAG, "Background scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for background scan", e)
            stopSelf()
        }
    }

    private fun processResult(result: ScanResult) {
        val beacon = parserEngine.parse(result)

        // Check if this matches any known beacon
        val match = knownBeacons.find { entity ->
            when (entity.protocol) {
                "IBEACON" -> entity.uuid == beacon.iBeaconUuid &&
                        (entity.major == null || entity.major == beacon.iBeaconMajor) &&
                        (entity.minor == null || entity.minor == beacon.iBeaconMinor)
                "EDDYSTONE_UID" -> entity.namespace == beacon.eddystoneNamespace &&
                        (entity.instance == null || entity.instance == beacon.eddystoneInstance)
                "EDDYSTONE_URL" -> entity.url == beacon.eddystoneUrl
                else -> false
            }
        }

        if (match != null) {
            // Known beacon - use presence tracker with configured settings
            val presence = presenceTracker.updatePresence(beacon, match.presenceTimeoutMs, match.minRssi)
            if (presence != null && match.notificationEnabled) {
                when (presence.presenceState) {
                    PresenceState.NEARBY, PresenceState.RE_ENTERED -> {
                        if (!presence.notificationSent && cooldownTracker.canNotify(beacon.identityKey, 30_000L)) {
                            sendNearbyNotification(match.name, beacon.displayName)
                            presence.notificationSent = true
                        }
                    }
                    else -> {}
                }
            }
        } else {
            // Any other nearby beacon - notify for all detected BLE devices
            notifyForDiscoveredBeacon(beacon)
        }
    }

    private fun notifyForDiscoveredBeacon(beacon: com.walnut.beaconfinder.data.model.BeaconDevice) {
        // Check cooldown to avoid notification spam
        val lastSeen = discoveredBeacons[beacon.identityKey]
        val now = System.currentTimeMillis()
        if (lastSeen != null && (now - lastSeen) < BEACON_DISCOVERY_COOLDOWN_MS) {
            return
        }
        discoveredBeacons[beacon.identityKey] = now

        val protocolName = when (beacon.protocol) {
            com.walnut.beaconfinder.data.model.BeaconProtocol.IBEACON -> "iBeacon"
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_UID -> "Eddystone UID"
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_URL -> "Eddystone URL"
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_TLM -> "Eddystone TLM"
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_EID -> "Eddystone EID"
            com.walnut.beaconfinder.data.model.BeaconProtocol.CUSTOM_BLE -> "Custom BLE"
            com.walnut.beaconfinder.data.model.BeaconProtocol.GENERIC_BLE -> "BLE Device"
        }

        val details = when (beacon.protocol) {
            com.walnut.beaconfinder.data.model.BeaconProtocol.IBEACON -> {
                "UUID: ${beacon.iBeaconUuid?.take(8)}... Major: ${beacon.iBeaconMajor} Minor: ${beacon.iBeaconMinor}"
            }
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_UID -> {
                "Namespace: ${beacon.eddystoneNamespace?.take(10)}... Instance: ${beacon.eddystoneInstance}"
            }
            com.walnut.beaconfinder.data.model.BeaconProtocol.EDDYSTONE_URL -> {
                "URL: ${beacon.eddystoneUrl}"
            }
            else -> {
                val name = beacon.displayName
                val rssi = beacon.rssi
                val services = if (beacon.serviceUuids.isNotEmpty()) " ${beacon.serviceUuids.size} services" else ""
                "$name (${rssi}dBm)$services"
            }
        }

        sendDiscoveredNotification(protocolName, details, beacon.address)
    }

    private fun sendDiscoveredNotification(protocolName: String, details: String, address: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("DEVICE_ADDRESS", address)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, address.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("Nearby: $protocolName")
            .setContentText(details)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID + address.hashCode(), notification)
    }

    private fun sendNearbyNotification(configName: String, beaconName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("Beacon Nearby")
            .setContentText("$configName beacon ($beaconName) is nearby")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID + System.currentTimeMillis().toInt(), notification)
    }

    private fun stopBackgroundScan() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        scanCallback = null
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, BeaconFinderApp.CHANNEL_MONITORING)
            .setContentTitle("BeaconFinder Monitoring")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        const val TAG = "BackgroundScanService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.walnut.beaconfinder.START_BACKGROUND"
        const val ACTION_STOP = "com.walnut.beaconfinder.STOP_BACKGROUND"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 31) {
                val hasBtConnect = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val hasBtScan = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                if (!hasBtConnect && !hasBtScan) {
                    Log.w(TAG, "Cannot start BackgroundScanService - Bluetooth permissions not granted")
                    return
                }
            }
            val intent = Intent(context, BackgroundScanService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
