package com.walnut.beaconfinder.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val autoConnectEnabled by viewModel.autoConnectEnabled.collectAsStateWithLifecycle()
    val presenceTimeoutMs by viewModel.presenceTimeoutMs.collectAsStateWithLifecycle()
    val minRssi by viewModel.minRssi.collectAsStateWithLifecycle()
    val maxRetries by viewModel.maxRetries.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var crashLog by remember { mutableStateOf("") }
    var showCrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, BeaconFinderApp.CRASH_LOG_FILE)
            if (file.exists()) {
                crashLog = file.readText()
            }
        } catch (_: Exception) {}
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

            // Background Monitoring
            SettingsSection("Background Monitoring") {
                SettingsSwitch(
                    title = "Background Monitoring",
                    subtitle = "Scan for beacons when app is in background",
                    checked = monitoringEnabled,
                    onCheckedChange = { viewModel.setMonitoringEnabled(it) }
                )
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
