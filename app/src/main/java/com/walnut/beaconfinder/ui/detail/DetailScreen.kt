package com.walnut.beaconfinder.ui.detail

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.data.model.BeaconProtocol
import com.walnut.beaconfinder.data.model.ConnectionState
import com.walnut.beaconfinder.data.model.GattServiceInfo
import com.walnut.beaconfinder.ui.scanner.ProtocolChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onRawClick: (String) -> Unit,
    onRssiClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onGattClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val knownBeacon by viewModel.knownBeacon.collectAsStateWithLifecycle()
    val services by viewModel.discoveredServices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.displayName ?: "Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (device?.connectable == true) {
                        IconButton(
                            onClick = {
                                if (connectionState == ConnectionState.DISCONNECTED ||
                                    connectionState == ConnectionState.FAILED
                                ) {
                                    viewModel.connect()
                                } else {
                                    viewModel.disconnect()
                                }
                            }
                        ) {
                            Icon(
                                if (connectionState == ConnectionState.DISCONNECTED ||
                                    connectionState == ConnectionState.FAILED
                                ) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                                contentDescription = if (connectionState == ConnectionState.DISCONNECTED) "Connect" else "Disconnect"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        device?.let { dev ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Tab row
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Overview", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("BLE", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("Connection", modifier = Modifier.padding(12.dp))
                    }
                }

                when (selectedTab) {
                    0 -> OverviewTab(dev, viewModel, knownBeacon)
                    1 -> BleInfoTab(dev)
                    2 -> ConnectionTab(
                        dev,
                        connectionState,
                        services,
                        onRawClick = { onRawClick(dev.address) },
                        onRssiClick = { onRssiClick(dev.address) },
                        onHistoryClick = { onHistoryClick(dev.address) },
                        onGattClick = { onGattClick(dev.address) }
                    )
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun OverviewTab(
    device: com.walnut.beaconfinder.data.model.BeaconDevice,
    viewModel: DetailViewModel,
    knownBeacon: com.walnut.beaconfinder.data.db.KnownBeaconEntity?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Protocol & Identity
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Protocol",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp)
                        )
                        ProtocolChip(protocol = device.protocol)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "Name",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(text = device.displayName)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "Address",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(text = device.address, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "RSSI",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            text = "${device.rssi} dBm",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    device.txPower?.let { tx ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "TX Power",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(120.dp)
                            )
                            Text(
                                text = "$tx dBm",
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    viewModel.getDistance()?.let { dist ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "Distance",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(120.dp)
                            )
                            Text(text = dist)
                        }
                    }
                }
            }
        }

        // Protocol-specific details
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (device.protocol) {
            BeaconProtocol.IBEACON -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("iBeacon Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("UUID", device.iBeaconUuid ?: "N/A")
                        DetailRow("Major", device.iBeaconMajor?.toString() ?: "N/A")
                        DetailRow("Minor", device.iBeaconMinor?.toString() ?: "N/A")
                        DetailRow("TX Power", "${device.txPower ?: "N/A"} dBm")
                    }
                }
            }
            BeaconProtocol.EDDYSTONE_UID -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Eddystone UID", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Namespace", device.eddystoneNamespace ?: "N/A")
                        DetailRow("Instance", device.eddystoneInstance ?: "N/A")
                        DetailRow("TX Power", "${device.txPower ?: "N/A"} dBm")
                    }
                }
            }
            BeaconProtocol.EDDYSTONE_URL -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Eddystone URL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("URL", device.eddystoneUrl ?: "N/A")
                        DetailRow("TX Power", "${device.txPower ?: "N/A"} dBm")
                    }
                }
            }
            BeaconProtocol.EDDYSTONE_TLM -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Eddystone TLM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Version", device.eddystoneTlmVersion?.toString() ?: "N/A")
                        DetailRow("Battery", "${device.eddystoneBatteryVoltage ?: "N/A"} mV")
                        DetailRow("Temperature", "${device.eddystoneTemperature?.let { String.format("%.1f", it) } ?: "N/A"} °C")
                        DetailRow("Packets", device.eddystoneAdvCount?.toString() ?: "N/A")
                        device.eddystoneTimeSinceBoot?.let { uptime ->
                            val hours = uptime / 3600
                            val mins = (uptime % 3600) / 60
                            DetailRow("Uptime", "${hours}h ${mins}m")
                        }
                    }
                }
            }
            BeaconProtocol.EDDYSTONE_EID -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Eddystone EID", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("EID", device.eddystoneEid ?: "N/A")
                        DetailRow("TX Power", "${device.txPower ?: "N/A"} dBm")
                    }
                }
            }
            else -> {}
        }

        // Known beacon info
        knownBeacon?.let {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Known Beacon: ${it.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow("Notifications", if (it.notificationEnabled) "ON" else "OFF")
                        DetailRow("Auto Connect", if (it.autoConnectEnabled) "ON" else "OFF")
                        DetailRow("Min RSSI", "${it.minRssi} dBm")
                    }
                }
            }
        }
    }
}

@Composable
fun BleInfoTab(device: com.walnut.beaconfinder.data.model.BeaconDevice) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        device.manufacturerId?.let { mfgId ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Manufacturer Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Manufacturer ID", String.format("0x%04X", mfgId))
                        device.manufacturerData?.let { data ->
                            DetailRow("Data", data.joinToString(" ") {
                                String.format("%02X", it)
                            })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (device.serviceData.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Service Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        device.serviceData.forEach { (uuid, data) ->
                            DetailRow("UUID", uuid.toString())
                            DetailRow("Data", data.joinToString(" ") {
                                String.format("%02X", it)
                            })
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Service UUIDs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        device.serviceUuids.forEach { uuid ->
                            Text(
                                text = uuid.toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectionTab(
    device: com.walnut.beaconfinder.data.model.BeaconDevice,
    connectionState: ConnectionState,
    services: List<GattServiceInfo>,
    onRawClick: () -> Unit,
    onRssiClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onGattClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Quick actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRawClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Raw")
                }
                OutlinedButton(
                    onClick = onRssiClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RSSI")
                }
                OutlinedButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Connection status
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("State", connectionState.name)
                    DetailRow("Connectable", if (device.connectable) "Yes" else "No")
                    if (!device.connectable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This device does not expose a connectable GATT server.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // GATT services
        if (services.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = onGattClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BluetoothConnected, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View GATT Services & Characteristics")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(services) { service ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Service: ${service.uuid}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        service.characteristics.forEach { char ->
                            Text(
                                text = "  Char: ${char.uuid}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "    Props: ${charPropertiesText(char.properties)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp),
            fontSize = 13.sp
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun charPropertiesText(properties: Int): String {
    val props = mutableListOf<String>()
    if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NO_RESP")
    if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
    if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
    return props.joinToString(" | ")
}
