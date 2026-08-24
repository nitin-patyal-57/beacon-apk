package com.walnut.beaconfinder.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.BeaconFinderApp
import com.walnut.beaconfinder.ErrorLogManager
import com.walnut.beaconfinder.service.BackgroundScanService
import com.walnut.beaconfinder.util.ExportCsvUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLeaderboard: () -> Unit = {},
    onNavigateToProximityHistory: () -> Unit = {},
    onNavigateToScanHistory: () -> Unit = {}
) {
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val autoConnectEnabled by viewModel.autoConnectEnabled.collectAsStateWithLifecycle()
    val presenceTimeoutMs by viewModel.presenceTimeoutMs.collectAsStateWithLifecycle()
    val minRssi by viewModel.minRssi.collectAsStateWithLifecycle()
    val maxRetries by viewModel.maxRetries.collectAsStateWithLifecycle()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val quietHoursStart by viewModel.quietHoursStart.collectAsStateWithLifecycle()
    val quietHoursEnd by viewModel.quietHoursEnd.collectAsStateWithLifecycle()
    val notificationRangeMeters by viewModel.notificationRangeMeters.collectAsStateWithLifecycle()
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsStateWithLifecycle()
    val adaptiveScanEnabled by viewModel.adaptiveScanEnabled.collectAsStateWithLifecycle()
    val scanHistoryEnabled by viewModel.scanHistoryEnabled.collectAsStateWithLifecycle()
    val notificationGroupingEnabled by viewModel.notificationGroupingEnabled.collectAsStateWithLifecycle()
    val leaderboardPeriodHours by viewModel.leaderboardPeriodHours.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var crashLog by remember { mutableStateOf("") }
    var showCrashDialog by remember { mutableStateOf(false) }
    var errorLog by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showLogcatDialog by remember { mutableStateOf(false) }
    var logcatOutput by remember { mutableStateOf("") }

    var isBatteryOptimized by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isBatteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) != true
        }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    }

    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, BeaconFinderApp.CRASH_LOG_FILE)
            if (file.exists()) {
                crashLog = file.readText()
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(showErrorDialog) {
        if (showErrorDialog) {
            errorLog = ErrorLogManager.getLogFileContent(context)
        }
    }

    LaunchedEffect(showLogcatDialog) {
        if (showLogcatDialog) {
            logcatOutput = withContext(Dispatchers.IO) {
                ErrorLogManager.getLogcatErrors(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (crashLog.isNotBlank()) {
                SettingsSection("Crash Log") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        onClick = { showCrashDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Crash detected!",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Tap to view crash log",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection("Diagnostics") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    onClick = { showErrorDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "View Error Log",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "App errors and warnings captured during runtime",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    onClick = { showLogcatDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "View Logcat Errors",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "System logcat errors (last 300 lines)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    onClick = {
                        ErrorLogManager.clearLog(context)
                        val crashFile = File(context.filesDir, BeaconFinderApp.CRASH_LOG_FILE)
                        if (crashFile.exists()) crashFile.delete()
                        crashLog = ""
                        errorLog = ""
                        Toast.makeText(context, "All logs cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Clear All Logs",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Delete crash log and error log",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Background Monitoring
            SettingsSection("Background Monitoring") {
                SettingsSwitch(
                    title = "Background Monitoring",
                    subtitle = "Scan for beacons when app is in background",
                    checked = monitoringEnabled,
                    onCheckedChange = { viewModel.setMonitoringEnabled(it) }
                )
            }

            // Battery Optimization Warning
            if (isBatteryOptimized) {
                SettingsSection("Battery Optimization") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Battery optimization is ON",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Background scanning may stop when screen is off. Tap to disable battery optimization for reliable beacon detection.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Notifications
            SettingsSection("Notifications") {
                SettingsSwitch(
                    title = "Nearby Notifications",
                    subtitle = "Show notification when known beacon is detected",
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
            }

            // Auto Connect
            SettingsSection("Connection") {
                SettingsSwitch(
                    title = "Automatic Connection",
                    subtitle = "Auto-connect to known beacons with GATT",
                    checked = autoConnectEnabled,
                    onCheckedChange = { viewModel.setAutoConnectEnabled(it) }
                )

                SettingsSlider(
                    title = "Max Retries",
                    subtitle = "Maximum connection retry attempts",
                    value = maxRetries.toFloat(),
                    valueRange = 1f..10f,
                    onValueChange = { viewModel.setMaxRetries(it.toInt()) },
                    displayValue = "$maxRetries"
                )
            }

            // Presence
            SettingsSection("Presence Detection") {
                SettingsSlider(
                    title = "Presence Timeout",
                    subtitle = "Time before beacon is considered lost",
                    value = presenceTimeoutMs.toFloat(),
                    valueRange = 5_000f..120_000f,
                    onValueChange = { viewModel.setPresenceTimeoutMs(it.toLong()) },
                    displayValue = formatTimeout(presenceTimeoutMs)
                )

                SettingsSlider(
                    title = "Minimum RSSI",
                    subtitle = "Ignore beacons weaker than this signal",
                    value = minRssi.toFloat(),
                    valueRange = -100f..-30f,
                    onValueChange = { viewModel.setMinRssi(it.toInt()) },
                    displayValue = "$minRssi dBm"
                )
            }

            // Quiet Hours
            SettingsSection("Quiet Hours") {
                SettingsSwitch(
                    title = "Enable Quiet Hours",
                    subtitle = "Suppress TTS and alerts during set hours",
                    checked = quietHoursEnabled,
                    onCheckedChange = { viewModel.setQuietHoursEnabled(it) }
                )
                if (quietHoursEnabled) {
                    SettingsSlider(
                        title = "Start Hour",
                        subtitle = "Quiet period begins at this hour",
                        value = quietHoursStart.toFloat(),
                        valueRange = 0f..23f,
                        onValueChange = { viewModel.setQuietHoursStart(it.toInt()) },
                        displayValue = formatHour(quietHoursStart)
                    )
                    SettingsSlider(
                        title = "End Hour",
                        subtitle = "Quiet period ends at this hour",
                        value = quietHoursEnd.toFloat(),
                        valueRange = 0f..23f,
                        onValueChange = { viewModel.setQuietHoursEnd(it.toInt()) },
                        displayValue = formatHour(quietHoursEnd)
                    )
                }
            }

            // Distance Range
            SettingsSection("Notification Range") {
                SettingsSlider(
                    title = "Alert Distance",
                    subtitle = "Only alert when beacon is within this distance",
                    value = notificationRangeMeters.toFloat(),
                    valueRange = 1f..100f,
                    onValueChange = { viewModel.setNotificationRangeMeters(it.toDouble()) },
                    displayValue = String.format(java.util.Locale.US, "%.0fm", notificationRangeMeters)
                )
            }

            // Appearance
            SettingsSection("Appearance") {
                SettingsSwitch(
                    title = "Dark Mode",
                    subtitle = "Use dark theme (changes apply on restart)",
                    checked = darkModeEnabled,
                    onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                )
            }

            // Performance
            SettingsSection("Performance") {
                SettingsSwitch(
                    title = "Adaptive Scan",
                    subtitle = "Reduce scan rate when battery is low",
                    checked = adaptiveScanEnabled,
                    onCheckedChange = { viewModel.setAdaptiveScanEnabled(it) }
                )
            }

            // Notifications Advanced
            SettingsSection("Notification Options") {
                SettingsSwitch(
                    title = "Group Notifications",
                    subtitle = "Group similar beacon alerts together",
                    checked = notificationGroupingEnabled,
                    onCheckedChange = { viewModel.setNotificationGroupingEnabled(it) }
                )
            }

            // Data
            SettingsSection("Data") {
                SettingsSwitch(
                    title = "Scan History",
                    subtitle = "Track all seen beacons for leaderboard",
                    checked = scanHistoryEnabled,
                    onCheckedChange = { viewModel.setScanHistoryEnabled(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onClick = { onNavigateToLeaderboard() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Beacon Leaderboard",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Most frequently seen beacons",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    onClick = { onNavigateToProximityHistory() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Time Near Beacons",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "How long you were near each beacon",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    onClick = { onNavigateToScanHistory() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "All Seen Beacons",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "History of every beacon detected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Battery Status
            SettingsSection("Battery Status") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (batteryLevel <= 20) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Battery Level",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "$batteryLevel% ${if (batteryLevel <= 20) "⚠ Low" else ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Export
            SettingsSection("Export Data") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    onClick = {
                        val devices = BackgroundScanService.scannedDevices.value
                        if (devices.isEmpty()) {
                            Toast.makeText(context, "No scan data to export", Toast.LENGTH_SHORT).show()
                        } else {
                            val uri = ExportCsvUtil.exportScanResults(context, devices)
                            if (uri != null) ExportCsvUtil.shareFile(context, uri)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Export Scan Results",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Export current scan data as CSV",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("Crash Log") },
            text = {
                Text(
                    text = crashLog,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", crashLog))
                    Toast.makeText(context, "Crash log copied!", Toast.LENGTH_SHORT).show()
                    showCrashDialog = false
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error Log") },
            text = {
                Text(
                    text = errorLog.ifBlank { "No errors logged." },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("error_log", errorLog))
                    Toast.makeText(context, "Error log copied!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showLogcatDialog) {
        AlertDialog(
            onDismissRequest = { showLogcatDialog = false },
            title = { Text("Logcat Errors") },
            text = {
                Text(
                    text = logcatOutput.ifBlank { "No logcat errors found." },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("logcat_errors", logcatOutput))
                    Toast.makeText(context, "Logcat errors copied!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogcatDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(text = displayValue, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

private fun formatTimeout(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 60) {
        "${seconds / 60}m ${seconds % 60}s"
    } else {
        "${seconds}s"
    }
}

private fun formatHour(hour: Int): String {
    return String.format(java.util.Locale.US, "%02d:00", hour)
}
