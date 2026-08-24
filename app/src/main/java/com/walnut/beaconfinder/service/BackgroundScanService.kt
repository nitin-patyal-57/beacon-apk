package com.walnut.beaconfinder.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.ParcelUuid
import android.speech.tts.TextToSpeech
import android.util.Log
import com.walnut.beaconfinder.BeaconFinderApp
import com.walnut.beaconfinder.ErrorLogManager
import com.walnut.beaconfinder.MainActivity
import com.walnut.beaconfinder.data.db.BeaconDatabase
import com.walnut.beaconfinder.data.db.KnownBeaconEntity
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.PresenceState
import com.walnut.beaconfinder.data.parser.BeaconParserEngine
import com.walnut.beaconfinder.data.parser.CustomBeaconParser
import com.walnut.beaconfinder.data.processing.BeaconPresenceTracker
import com.walnut.beaconfinder.data.processing.CooldownTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class BackgroundScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
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
    private var quietHoursEnabled = false
    private var quietHoursStart = 22
    private var quietHoursEnd = 7
    private var notificationRangeMeters = 50.0
    private var zones = listOf<com.walnut.beaconfinder.data.db.ZoneEntity>()
    private val zonePresenceMap = ConcurrentHashMap<String, MutableSet<String>>()
    private val zoneBeaconKeysCache = ConcurrentHashMap<String, List<String>>()
    private var mediaPlayer: android.media.MediaPlayer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastScanResultTime: Long = 0L
    private var healthCheckJob: Job? = null
    private var presenceTimeoutJob: Job? = null
    private var bgScanRetryCount = 0
    private var lastScanStartTime: Long = 0L
    private var lastNotificationUpdate: Long = 0L
    private var lastStaleCleanup: Long = 0L
    private var uiDirty = false
    private var uiBatchJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastTtsSpoken: String? = null
    private var lastTtsAt: Long = 0L
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var btStateReceiver: BroadcastReceiver? = null
    private val rssiZoneMap = ConcurrentHashMap<String, RssiZoneState>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundScanService created")
        doStartForeground()
        acquireWakeLock()
        initTts()
        setServiceAlive(true)
        ScanWatchdogReceiver.schedule(this)
        startHeartbeat()
        startHealthCheck()
        startPresenceTimeoutChecker()
        registerBtStateReceiver()
        loadSettings()
        scope.launch {
            loadKnownBeacons()
            loadCustomFormats()
            loadZones()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("beacon_finder_prefs", MODE_PRIVATE)
        quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", false)
        quietHoursStart = prefs.getInt("quiet_hours_start", 22)
        quietHoursEnd = prefs.getInt("quiet_hours_end", 7)
        notificationRangeMeters = prefs.getFloat("notification_range_meters", 50f).toDouble()
        Log.d(TAG, "Settings loaded: quietHours=$quietHoursEnabled $quietHoursStart-$quietHoursEnd, range=${notificationRangeMeters}m")
    }

    private fun doStartForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Monitoring active"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Monitoring active"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot start foreground service - missing permissions", e)
            ErrorLogManager.logError(TAG, "Cannot start foreground service - missing permissions", e)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed, continuing as background service", e)
            ErrorLogManager.logWarning(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun registerBtStateReceiver() {
        btStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.d(TAG, "BT turned ON internally → restarting scan")
                            scope.launch {
                                delay(1000)
                                startBackgroundScan(forceRestart = true)
                            }
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d(TAG, "BT turned OFF internally → stopping scan")
                            stopBackgroundScan()
                            updateNotification("Waiting for Bluetooth...")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(btStateReceiver, filter)
        Log.d(TAG, "BT state receiver registered")
    }

    private fun initTts() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set TTS audio attributes", e)
                }
                ttsReady = true
                Log.d(TAG, "TTS initialized")
            } else {
                Log.w(TAG, "TTS init failed: $status, retrying in 5s")
                scope.launch {
                    delay(5000)
                    initTts()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                doStartForeground()
                startBackgroundScan(forceRestart = true)
            }
            ACTION_STOP -> stopBackgroundScan()
            null -> {
                Log.d(TAG, "Service recreated by system (START_STICKY), restarting scan")
                doStartForeground()
                startBackgroundScan(forceRestart = true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBackgroundScan()
        unregisterBtStateReceiver()
        releaseWakeLock()
        try {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        setServiceAlive(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun unregisterBtStateReceiver() {
        try {
            btStateReceiver?.let { unregisterReceiver(it) }
            btStateReceiver = null
            Log.d(TAG, "BT state receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister BT state receiver", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, rescheduling watchdog + restarting service")
        ScanWatchdogReceiver.schedule(this)
        scope.launch {
            delay(1000)
            startBackgroundScan(forceRestart = true)
        }
    }

    private fun setServiceAlive(alive: Boolean) {
        getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(ScanWatchdogReceiver.KEY_SERVICE_ALIVE, alive)
            .putLong(ScanWatchdogReceiver.KEY_LAST_ALIVE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(10_000L)
                setServiceAlive(true)
            }
        }
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
            Log.d(TAG, "Reloaded ${formats.size} custom formats")
        }
    }

    private fun loadZones() {
        scope.launch {
            val db = BeaconDatabase.getInstance(applicationContext)
            zones = db.zoneDao().getAllSync()
            zoneBeaconKeysCache.clear()
            for (zone in zones) {
                try {
                    val arr = org.json.JSONArray(zone.beaconKeys)
                    zoneBeaconKeysCache[zone.name] = (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) {}
            }
            Log.d(TAG, "Loaded ${zones.size} zones")
        }
    }

    fun reloadKnownBeacons() {
        scope.launch {
            val db = BeaconDatabase.getInstance(applicationContext)
            knownBeacons = db.knownBeaconDao().getAll()
            Log.d(TAG, "Reloaded ${knownBeacons.size} known beacons")
        }
    }

    fun reloadCustomFormats() {
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
            Log.d(TAG, "Reloaded ${formats.size} custom formats")
        }
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()

        val iBeaconFilter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf())
            .build()
        filters.add(iBeaconFilter)

        val eddystoneFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(EDDYSTONE_SERVICE_UUID))
            .build()
        filters.add(eddystoneFilter)

        return filters
    }

    private fun startBackgroundScan(forceRestart: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRestart && now - lastScanStartTime < MIN_SCAN_START_INTERVAL_MS) {
            Log.d(TAG, "Scan start throttled (last start was ${(now - lastScanStartTime) / 1000}s ago)")
            isServiceScanning = false
            return
        }
        if (!forceRestart && scanCallback != null && scanner != null) {
            Log.d(TAG, "Scan already active, skipping restart")
            return
        }

        reloadKnownBeacons()
        reloadCustomFormats()

        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required permissions")
            ErrorLogManager.logWarning(TAG, "Missing required permissions, scan cannot start")
            updateNotification("Missing permissions. Grant all required permissions.")
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled - will restart when BT turns on")
            updateNotification("Waiting for Bluetooth...")
            return
        }

        stopBleScan()

        try {
            scanner = adapter.bluetoothLeScanner
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting scanner", e)
            ErrorLogManager.logError(TAG, "SecurityException getting BLE scanner", e)
            updateNotification("Bluetooth permission denied.")
            return
        }

        if (scanner == null) {
            Log.w(TAG, "BLE scanner null")
            ErrorLogManager.logWarning(TAG, "BLE scanner returned null - BT may be toggling")
            updateNotification("BLE scanner unavailable.")
            return
        }

        val filters = buildScanFilters()
        val isScreenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
        val settings = if (isScreenOn) {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
        } else {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build()
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    processResult(result)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException in scan callback", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scan callback", e)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    try {
                        processResult(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in batch callback", e)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                ErrorLogManager.logError(TAG, "BLE scan failed with code $errorCode (attempt ${bgScanRetryCount + 1}/5)")
                scanCallback = null
                scanner = null
                isServiceScanning = false
                bgScanRetryCount++
                if (bgScanRetryCount <= 5) {
                    val backoffMs = 3000L * bgScanRetryCount
                    updateNotification("Scan failed (code $errorCode), retry ${bgScanRetryCount}/5 in ${backoffMs / 1000}s...")
                    Log.w(TAG, "Retrying scan in ${backoffMs}ms (attempt $bgScanRetryCount)")
                    scope.launch {
                        delay(backoffMs)
                        startBackgroundScan(forceRestart = true)
                    }
                } else {
                    Log.e(TAG, "Scan failed after 5 retries, waiting 60s")
                    updateNotification("Scan failed. Retrying in 60s...")
                    bgScanRetryCount = 0
                    scope.launch {
                        delay(60_000L)
                        startBackgroundScan(forceRestart = true)
                    }
                }
            }
        }

        try {
            val cb = scanCallback ?: return
            scanner?.startScan(filters, settings, cb)
            lastScanStartTime = System.currentTimeMillis()
            bgScanRetryCount = 0
            lastScanResultTime = System.currentTimeMillis()
            isServiceScanning = true
            startUiBatchUpdates()
            Log.d(TAG, "Background scan started (${filters.size} filters, screenOn=$isScreenOn)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied starting scan", e)
            ErrorLogManager.logError(TAG, "Permission denied starting BLE scan", e)
            scanner = null
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan", e)
            ErrorLogManager.logError(TAG, "Exception starting BLE scan: ${e.message}", e)
            scanner = null
        }
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                val elapsed = System.currentTimeMillis() - lastScanResultTime
                if (lastScanResultTime > 0 && elapsed > HEALTH_CHECK_TIMEOUT_MS) {
                    Log.w(TAG, "No scan results for ${elapsed / 1000}s, restarting scan")
                    updateNotification("Scan stalled, restarting...")
                    stopBleScan()
                    delay(1000)
                    scanner = null
                    startBackgroundScan(forceRestart = true)
                }
            }
        }
    }

    private fun startPresenceTimeoutChecker() {
        presenceTimeoutJob?.cancel()
        presenceTimeoutJob = scope.launch {
            while (isActive) {
                delay(PRESENCE_CHECK_INTERVAL_MS)
                val timedOut = presenceTracker.checkTimeouts(PRESENCE_TIMEOUT_MS)
                for (info in timedOut) {
                    if (!isQuietHours()) {
                        speak("${info.beaconName} is out of range")
                    }
                    if (cooldownTracker.canNotify(info.identityKey, 60_000L)) {
                        sendOutOfRangeNotification(info.beaconName, info.beaconName)
                    }
                    logAlert(info.beaconName, info.identityKey, "UNKNOWN", "OUT_OF_RANGE", -100, -1.0)
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val hasBtScan = checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasBtConnect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasBtScan || !hasBtConnect) {
                return false
            }
        }
        val hasFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFineLocation || hasCoarseLocation
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BeaconFinder::BackgroundScanWakeLock"
            )?.apply {
                acquire()
                Log.d(TAG, "Wake lock acquired (indefinite)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_SCAN_ONLY,
                "BeaconFinder::BackgroundScanWifiLock"
            )?.apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wifi lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (_: Exception) {}
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun processResult(result: ScanResult) {
        lastScanResultTime = System.currentTimeMillis()
        lastResultTimestamp = System.currentTimeMillis()

        val beacon = try {
            parserEngine.parse(result)
        } catch (e: Exception) {
            Log.e(TAG, "Parser error", e)
            return
        }

        if (beacon.protocol != BeaconProtocol.IBEACON &&
            beacon.protocol != BeaconProtocol.EDDYSTONE_UID &&
            beacon.protocol != BeaconProtocol.EDDYSTONE_URL &&
            beacon.protocol != BeaconProtocol.EDDYSTONE_TLM &&
            beacon.protocol != BeaconProtocol.EDDYSTONE_EID) {
            return
        }

        val now = System.currentTimeMillis()

        val existing = sharedDeviceMap[beacon.address]
        if (existing != null) {
            existing.lastSeen = now
            existing.rssi = beacon.rssi
            existing.name = beacon.name ?: existing.name
            existing.iBeaconUuid = existing.iBeaconUuid ?: beacon.iBeaconUuid
            existing.iBeaconMajor = existing.iBeaconMajor ?: beacon.iBeaconMajor
            existing.iBeaconMinor = existing.iBeaconMinor ?: beacon.iBeaconMinor
            existing.eddystoneNamespace = existing.eddystoneNamespace ?: beacon.eddystoneNamespace
            existing.eddystoneInstance = existing.eddystoneInstance ?: beacon.eddystoneInstance
            existing.eddystoneUrl = existing.eddystoneUrl ?: beacon.eddystoneUrl
        } else {
            sharedDeviceMap[beacon.address] = beacon.copy(firstSeen = now, lastSeen = now)
        }
        uiDirty = true

        if (now - lastStaleCleanup > STALE_CLEANUP_INTERVAL_MS) {
            lastStaleCleanup = now
            sharedDeviceMap.entries.removeIf { (_, device) ->
                (now - device.lastSeen) > 15_000L
            }
            if (sharedDeviceMap.size > MAX_DEVICE_CACHE_SIZE) {
                val oldest = sharedDeviceMap.entries.sortedBy { it.value.lastSeen }
                    .take(sharedDeviceMap.size - MAX_DEVICE_CACHE_SIZE)
                oldest.forEach { sharedDeviceMap.remove(it.key) }
            }
        }

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

        val beaconName = match?.name ?: when (beacon.protocol) {
            BeaconProtocol.IBEACON -> "Unknown iBeacon"
            BeaconProtocol.EDDYSTONE_UID -> "Unknown Eddystone"
            else -> "Unknown beacon"
        }

        val zone = when {
            beacon.rssi >= RSSI_THRESHOLD_IN_RANGE -> RssiZone.IN_RANGE
            beacon.rssi <= RSSI_THRESHOLD_OUT_OF_RANGE -> RssiZone.OUT_OF_RANGE
            else -> RssiZone.SILENT
        }

        val distanceM = calculateDistance(beacon.rssi)

        val zoneState = rssiZoneMap.getOrPut(beacon.identityKey) { RssiZoneState() }
        val previousZone = zoneState.zone

        if (zone != previousZone) {
            zoneState.zone = zone

            when (zone) {
                RssiZone.IN_RANGE -> {
                    if (previousZone != RssiZone.IN_RANGE) {
                        if (distanceM <= notificationRangeMeters) {
                            if (!isQuietHours()) {
                                speak("$beaconName is in range")
                            }
                            if (cooldownTracker.canNotify(beacon.identityKey, 30_000L)) {
                                sendNearbyNotification(beaconName, beacon.displayName)
                            }
                            logAlert(beaconName, beacon.address, beacon.protocol.name, "IN_RANGE", beacon.rssi, distanceM)
                        }
                    }
                }
                RssiZone.OUT_OF_RANGE -> {
                    if (previousZone == RssiZone.IN_RANGE || previousZone == RssiZone.SILENT) {
                        if (!isQuietHours()) {
                            speak("$beaconName is out of range")
                        }
                        if (cooldownTracker.canNotify(beacon.identityKey, 30_000L)) {
                            sendOutOfRangeNotification(beaconName, beacon.displayName)
                        }
                        logAlert(beaconName, beacon.address, beacon.protocol.name, "OUT_OF_RANGE", beacon.rssi, distanceM)
                    }
                }
                RssiZone.SILENT -> { }
            }
        }

        if (zone == RssiZone.IN_RANGE && match?.soundUri != null) {
            if (cooldownTracker.canNotify("sound:${beacon.identityKey}", 10_000L)) {
                playCustomSound(match.soundUri)
            }
        }

        if (zone == RssiZone.IN_RANGE) {
            checkZones(beacon.identityKey, beaconName)
        }

        val presence = presenceTracker.updatePresence(
            beacon,
            timeoutMs = match?.presenceTimeoutMs ?: PRESENCE_TIMEOUT_MS,
            minRssi = match?.minRssi ?: Int.MIN_VALUE
        )
        if (presence != null && presence.presenceState == PresenceState.LOST &&
            presence.previousState != PresenceState.LOST) {
            speak("$beaconName is out of range")
            if (cooldownTracker.canNotify(beacon.identityKey, 60_000L)) {
                sendOutOfRangeNotification(beaconName, beacon.displayName)
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
            return
        }
        val now = System.currentTimeMillis()
        if (text == lastTtsSpoken && (now - lastTtsAt) < TTS_COOLDOWN_MS) return
        lastTtsSpoken = text
        lastTtsAt = now

        try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "bg_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d(TAG, "TTS speaking: $text")

        scope.launch {
            delay(4000L)
            try {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } catch (_: Exception) {}
        }
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
        val id = NOTIFICATION_ID_BEACON + 10000 + (System.nanoTime().toInt() and 0x7FFF)
        nm.notify(id, notification)
    }

    private fun sendOutOfRangeNotification(configName: String, beaconName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("Beacon Out of Range")
            .setContentText("$configName beacon ($beaconName) is out of range")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val id = NOTIFICATION_ID_BEACON + 20000 + (System.nanoTime().toInt() and 0x7FFF)
        nm.notify(id, notification)
    }

    private fun sendZoneNotification(zoneName: String, action: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BeaconFinderApp.CHANNEL_NEARBY)
            .setContentTitle("Zone Alert")
            .setContentText("You have $action the $zoneName zone")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val id = NOTIFICATION_ID_BEACON + 30000 + (System.nanoTime().toInt() and 0x7FFF)
        nm.notify(id, notification)
    }

    private fun stopBackgroundScan() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        presenceTimeoutJob?.cancel()
        presenceTimeoutJob = null
        stopBleScan()
    }

    private fun stopBleScan() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        scanCallback = null
        isServiceScanning = false
        stopUiBatchUpdates()
    }

    private fun startUiBatchUpdates() {
        uiBatchJob?.cancel()
        uiBatchJob = scope.launch {
            while (isActive) {
                delay(UI_BATCH_INTERVAL_MS)
                if (uiDirty) {
                    uiDirty = false
                    _scannedDevices.value = sharedDeviceMap.toMap()
                }
            }
        }
    }

    private fun stopUiBatchUpdates() {
        uiBatchJob?.cancel()
        uiBatchJob = null
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
        try {
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdate < NOTIFICATION_UPDATE_MIN_INTERVAL_MS) return
            lastNotificationUpdate = now
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID, createNotification(text))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun playCustomSound(uri: String?) {
        if (uri == null) return
        try {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(applicationContext, android.net.Uri.parse(uri))
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play custom sound: $uri", e)
        }
    }

    private fun checkZones(beaconIdentityKey: String, beaconName: String) {
        for (zone in zones) {
            if (!zone.notificationEnabled) continue
            val zoneBeacons = zoneBeaconKeysCache[zone.name] ?: continue

            if (beaconIdentityKey in zoneBeacons) {
                val currentZoneBeacons = zonePresenceMap.getOrPut(zone.name) { mutableSetOf() }
                val wasEmpty = currentZoneBeacons.isEmpty()
                currentZoneBeacons.add(beaconIdentityKey)

                if (wasEmpty && currentZoneBeacons.isNotEmpty()) {
                    if (!isQuietHours()) {
                        speak("Entering ${zone.name}")
                    }
                    if (cooldownTracker.canNotify("zone:${zone.name}", 60_000L)) {
                        sendZoneNotification(zone.name, "entered")
                    }
                }
            }
        }
    }

    private fun calculateDistance(rssi: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = (-59.0 - rssi) / (10 * 2.0)
        return Math.pow(10.0, ratio)
    }

    private fun isQuietHours(): Boolean {
        if (!quietHoursEnabled) return false
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (quietHoursStart <= quietHoursEnd) {
            hour in quietHoursStart until quietHoursEnd
        } else {
            hour >= quietHoursStart || hour < quietHoursEnd
        }
    }

    private fun logAlert(beaconName: String, address: String, protocol: String, type: String, rssi: Int, distance: Double) {
        scope.launch {
            try {
                val db = BeaconDatabase.getInstance(applicationContext)
                db.alertHistoryDao().insert(
                    com.walnut.beaconfinder.data.db.AlertHistoryEntity(
                        beaconName = beaconName,
                        beaconAddress = address,
                        protocol = protocol,
                        alertType = type,
                        rssi = rssi,
                        distanceMeters = distance
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log alert", e)
            }
        }
    }

    enum class RssiZone { IN_RANGE, SILENT, OUT_OF_RANGE }

    data class RssiZoneState(
        var zone: RssiZone = RssiZone.SILENT,
        var lastAlertTime: Long = 0L
    )

    companion object {
        const val TAG = "BackgroundScanService"
        const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_BEACON = 2000
        const val ACTION_START = "com.walnut.beaconfinder.START_BACKGROUND"
        const val ACTION_STOP = "com.walnut.beaconfinder.STOP_BACKGROUND"
        private const val TTS_COOLDOWN_MS = 3_000L
        private const val HEALTH_CHECK_INTERVAL_MS = 10_000L
        private const val HEALTH_CHECK_TIMEOUT_MS = 20_000L
        private const val EDDYSTONE_SERVICE_UUID = "0000FEAA-0000-1000-8000-00805F9B34FB"
        private const val MIN_SCAN_START_INTERVAL_MS = 5_000L
        private const val PRESENCE_CHECK_INTERVAL_MS = 10_000L
        private const val PRESENCE_TIMEOUT_MS = 30_000L
        private const val NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 5_000L
        private const val STALE_CLEANUP_INTERVAL_MS = 10_000L
        private const val UI_BATCH_INTERVAL_MS = 200L
        private const val MAX_DEVICE_CACHE_SIZE = 100
        private const val RSSI_THRESHOLD_IN_RANGE = -60
        private const val RSSI_THRESHOLD_OUT_OF_RANGE = -90

        private val _scannedDevices = MutableStateFlow<Map<String, BeaconDevice>>(emptyMap())
        val scannedDevices: StateFlow<Map<String, BeaconDevice>> = _scannedDevices.asStateFlow()

        @Volatile
        var isServiceScanning = false
            private set

        private val sharedDeviceMap = ConcurrentHashMap<String, BeaconDevice>()

        @Volatile
        private var lastResultTimestamp: Long = 0L

        fun isScanAlive(): Boolean {
            if (!isServiceScanning) return false
            return System.currentTimeMillis() - lastResultTimestamp < 20_000L
        }

        fun forceRestart(context: Context) {
            val intent = Intent(context, BackgroundScanService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startForegroundService(intent)
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "forceRestart startService fallback failed", e2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force restart service", e)
            }
        }

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= 31) {
                val hasBtConnect = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val hasBtScan = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                if (!hasBtConnect || !hasBtScan) {
                    Log.w(TAG, "Cannot start - Bluetooth permissions not granted")
                    return
                }
            }
            val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFineLocation && !hasCoarseLocation) {
                Log.w(TAG, "Cannot start - Location permission not granted")
                return
            }
            val intent = Intent(context, BackgroundScanService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startForegroundService(intent)
                Log.d(TAG, "startForegroundService succeeded")
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "ForegroundServiceStartNotAllowed (Android 12+ bg), trying startService fallback")
                ErrorLogManager.logWarning(TAG, "ForegroundServiceStartNotAllowed - trying startService fallback")
                try {
                    context.startService(intent)
                    Log.d(TAG, "startService fallback succeeded")
                } catch (e2: Exception) {
                    Log.e(TAG, "startService fallback also failed", e2)
                    ErrorLogManager.logError(TAG, "startService fallback failed", e2)
                    ScanWatchdogReceiver.schedule(context)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException starting foreground service", e)
                ErrorLogManager.logError(TAG, "SecurityException starting foreground service", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
                ErrorLogManager.logError(TAG, "Failed to start foreground service: ${e.message}", e)
                ScanWatchdogReceiver.schedule(context)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
