package com.walnut.beaconfinder.ui.scanner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.ConnectionState

private const val TAG = "ScannerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onDeviceClick: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val autoConnectEnabled by viewModel.autoConnectEnabled.collectAsStateWithLifecycle()
    val lastConnectedBeacon by viewModel.lastConnectedBeacon.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var showPermDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            permissionsGranted = true
            showPermDenied = false
        } else {
            permissionsGranted = false
            showPermDenied = true
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            Log.d(TAG, "Permissions granted, starting scan")
            try {
                viewModel.startScan()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan after permission grant", e)
            }
        }
    }

    fun requestPermissions() {
        try {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 31) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error launching permission request", e)
        }
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val alreadyGranted = needed.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            permissionsGranted = true
        } else {
            requestPermissions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BeaconFinder") },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            scanError?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
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
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (showPermDenied) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
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
                        Text(
                            text = "Permissions required. Grant Bluetooth & Location in Settings.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            lastConnectedBeacon?.let { beacon ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nearest: ${beacon.displayName}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = "UUID: ${beacon.iBeaconUuid?.take(8)}... Major:${beacon.iBeaconMajor} Minor:${beacon.iBeaconMinor}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF1B5E20).copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { viewModel.speakBeaconName(beacon) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Speak", tint = Color(0xFF1B5E20))
                        }
                    }
                }
            }

            SystemStatusCard()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isScanning) {
                            viewModel.stopScan()
                        } else {
                            showPermDenied = false
                            permissionsGranted = false
                            requestPermissions()
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isScanning) "Stop" else "Scan")
                }

                Text(
                    text = "Devices: ${devices.size}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                FilterChip(
                    selected = autoConnectEnabled,
                    onClick = { viewModel.toggleAutoConnect() },
                    label = { Text("Proximity Alert", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            if (autoConnectEnabled) Icons.Default.LinkOff else Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.padding(end = 4.dp)
                )

                OutlinedButton(
                    onClick = { viewModel.clearDevices() },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = { Text("Search devices...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.address) }
                    )
                }

                if (devices.isEmpty() && isScanning) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Scanning for BLE devices...")
                            }
                        }
                    }
                }

                if (devices.isEmpty() && !isScanning) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Press Scan to discover BLE devices",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: BeaconDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    ProtocolChip(protocol = device.protocol)
                    Spacer(modifier = Modifier.height(4.dp))
                    RssiIndicator(rssi = device.rssi)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (device.protocol) {
                BeaconProtocol.IBEACON -> {
                    Text(
                        text = "UUID: ${device.iBeaconUuid?.take(8)}...",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Major: ${device.iBeaconMajor}  Minor: ${device.iBeaconMinor}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BeaconProtocol.EDDYSTONE_UID -> {
                    Text(
                        text = "Namespace: ${device.eddystoneNamespace?.take(10)}...",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Instance: ${device.eddystoneInstance}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BeaconProtocol.EDDYSTONE_URL -> {
                    Text(
                        text = "URL: ${device.eddystoneUrl}",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BeaconProtocol.EDDYSTONE_TLM -> {
                    Text(
                        text = "Battery: ${device.eddystoneBatteryVoltage}mV  Temp: ${device.eddystoneTemperature?.let { String.format("%.1f", it) }}\u00B0C",
                        fontSize = 12.sp
                    )
                }
                BeaconProtocol.CUSTOM_BLE, BeaconProtocol.GENERIC_BLE -> {}
                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("In Range", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }

                if (device.connectionState != ConnectionState.DISCONNECTED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (device.connectionState) {
                                        ConnectionState.CONNECTED,
                                        ConnectionState.READY -> Color.Blue
                                        ConnectionState.CONNECTING,
                                        ConnectionState.DISCOVERING_SERVICES -> Color.Yellow
                                        else -> Color.Red
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = device.connectionState.name,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!device.connectable) {
                    Text(
                        text = "Advertisement Only",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProtocolChip(protocol: BeaconProtocol) {
    val (color, label) = when (protocol) {
        BeaconProtocol.IBEACON -> Color(0xFF4CAF50) to "iBeacon"
        BeaconProtocol.EDDYSTONE_UID -> Color(0xFF2196F3) to "Eddystone UID"
        BeaconProtocol.EDDYSTONE_URL -> Color(0xFF03A9F4) to "Eddystone URL"
        BeaconProtocol.EDDYSTONE_TLM -> Color(0xFF00BCD4) to "Eddystone TLM"
        BeaconProtocol.EDDYSTONE_EID -> Color(0xFF009688) to "Eddystone EID"
        BeaconProtocol.CUSTOM_BLE -> Color(0xFFFF9800) to "Custom"
        BeaconProtocol.GENERIC_BLE -> Color(0xFF9E9E9E) to "Generic BLE"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RssiIndicator(rssi: Int) {
    val color = when {
        rssi > -50 -> Color(0xFF4CAF50)
        rssi > -70 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    val bars = when {
        rssi > -50 -> 4
        rssi > -60 -> 3
        rssi > -70 -> 2
        rssi > -80 -> 1
        else -> 0
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + index * 4).dp)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < bars) color else color.copy(alpha = 0.2f))
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${rssi}dBm",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
fun SystemStatusCard() {
    val context = LocalContext.current
    var btEnabled by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }
    var batteryOptimal by remember { mutableStateOf(true) }
    var serviceRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val btManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        btEnabled = btManager?.adapter?.isEnabled == true
        locationGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            batteryOptimal = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            batteryOptimal = true
        }
        serviceRunning = com.walnut.beaconfinder.service.BackgroundScanService.isServiceScanning
    }

    val allGood = btEnabled && locationGranted && batteryOptimal && serviceRunning

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allGood) Color(0xFF1B5E20).copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "System Status",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (allGood) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusItem("Bluetooth", btEnabled)
            StatusItem("Location Permission", locationGranted)
            StatusItem("Battery Optimization", batteryOptimal)
            StatusItem("Background Service", serviceRunning)
        }
    }
}

@Composable
fun StatusItem(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ok) "\u2713" else "\u2717",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
