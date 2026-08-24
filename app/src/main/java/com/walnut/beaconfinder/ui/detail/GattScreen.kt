package com.walnut.beaconfinder.ui.detail

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.data.model.ConnectionState
import com.walnut.beaconfinder.data.model.GattCharacteristicInfo
import com.walnut.beaconfinder.data.model.GattServiceInfo
import java.util.UUID

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val services by viewModel.discoveredServices.collectAsStateWithLifecycle()
    val characteristicValue by viewModel.characteristicValue.collectAsStateWithLifecycle()

    var expandedService by remember { mutableStateOf<String?>(null) }
    var writeDialogChar by remember { mutableStateOf<GattCharacteristicInfo?>(null) }
    var writeValue by remember { mutableStateOf("") }
    var notificationEnabledChars by remember { mutableStateOf(setOf<String>()) }
    var mtuValue by remember { mutableIntStateOf(512) }
    var connectionPriority by remember { mutableIntStateOf(0) }

    fun findCharacteristic(charInfo: GattCharacteristicInfo): BluetoothGattCharacteristic? {
        for (service in services) {
            for (char in service.characteristics) {
                if (char.uuid == charInfo.uuid) {
                    return BluetoothGattCharacteristic(
                        UUID.fromString(char.uuid.toString()),
                        char.properties,
                        0
                    )
                }
            }
        }
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GATT Services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (connectionState == ConnectionState.DISCONNECTED) {
                        IconButton(onClick = { viewModel.connect() }) {
                            Icon(Icons.Default.BluetoothConnected, contentDescription = "Connect")
                        }
                    } else if (connectionState == ConnectionState.READY) {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = "Disconnect")
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
            // Status bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = device?.displayName ?: "",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (connectionState) {
                            ConnectionState.DISCONNECTED -> "Disconnected"
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.CONNECTED -> "Connected"
                            ConnectionState.DISCOVERING_SERVICES -> "Discovering..."
                            ConnectionState.READY -> "Ready (${services.size} services)"
                            ConnectionState.DISCONNECTING -> "Disconnecting..."
                            ConnectionState.FAILED -> "Failed"
                        },
                        fontSize = 12.sp,
                        color = when (connectionState) {
                            ConnectionState.READY -> MaterialTheme.colorScheme.primary
                            ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }

            // Characteristic value display
            characteristicValue?.let { (addr, value) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Latest Read/Notify:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = value.joinToString(" ") { String.format("%02X", it) },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "ASCII: ${String(value, Charsets.UTF_8)}",
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Developer Tools
            if (connectionState == ConnectionState.READY) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Developer Tools", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Connection Priority
                        Text("Connection Priority", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0 to "HIGH", 1 to "BALANCED", 2 to "LOW").forEach { (value, label) ->
                                FilterChip(
                                    selected = connectionPriority == value,
                                    onClick = {
                                        connectionPriority = value
                                        viewModel.setConnectionPriority(value)
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // MTU
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = mtuValue.toString(),
                                onValueChange = { mtuValue = it.toIntOrNull() ?: 512 },
                                label = { Text("MTU") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            FilledTonalButton(
                                onClick = { viewModel.requestMtu(mtuValue) }
                            ) {
                                Text("Request MTU", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Read RSSI
                        FilledTonalButton(
                            onClick = { viewModel.readRemoteRssi() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Read Remote RSSI", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Services list
            when (connectionState) {
                ConnectionState.DISCONNECTED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Connect to view GATT services",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Please wait...")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(services) { service ->
                            ServiceCard(
                                service = service,
                                isExpanded = expandedService == service.uuid.toString(),
                                onToggle = {
                                    expandedService = if (expandedService == service.uuid.toString()) {
                                        null
                                    } else {
                                        service.uuid.toString()
                                    }
                                },
                                onRead = { char ->
                                    findCharacteristic(char)?.let { viewModel.readCharacteristic(it) }
                                },
                                onWrite = { char ->
                                    writeDialogChar = char
                                },
                                onToggleNotification = { char, enabled ->
                                    findCharacteristic(char)?.let {
                                        viewModel.toggleNotification(it, enabled)
                                    }
                                    notificationEnabledChars = if (enabled) {
                                        notificationEnabledChars + char.uuid.toString()
                                    } else {
                                        notificationEnabledChars - char.uuid.toString()
                                    }
                                },
                                notificationEnabledChars = notificationEnabledChars
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    // Write dialog
    writeDialogChar?.let { charInfo ->
        AlertDialog(
            onDismissRequest = { writeDialogChar = null },
            title = { Text("Write Characteristic") },
            text = {
                Column {
                    Text(
                        text = "UUID: ${charInfo.uuid}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = writeValue,
                        onValueChange = { writeValue = it },
                        label = { Text("Hex value") },
                        placeholder = { Text("e.g. 01 02 FF") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val bytes = writeValue.replace(" ", "")
                        .chunked(2)
                        .map { it.toInt(16).toByte() }
                        .toByteArray()
                    val char = findCharacteristic(charInfo)
                    if (char != null) {
                        viewModel.writeCharacteristic(char, bytes)
                    }
                    writeDialogChar = null
                    writeValue = ""
                }) {
                    Text("Write")
                }
            },
            dismissButton = {
                TextButton(onClick = { writeDialogChar = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ServiceCard(
    service: GattServiceInfo,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRead: (GattCharacteristicInfo) -> Unit,
    onWrite: (GattCharacteristicInfo) -> Unit,
    onToggleNotification: (GattCharacteristicInfo, Boolean) -> Unit,
    notificationEnabledChars: Set<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Service header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Service",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = service.uuid.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            // Characteristics
            if (isExpanded) {
                service.characteristics.forEach { charInfo ->
                    @Suppress("DEPRECATION")
                    Divider(modifier = Modifier.padding(horizontal = 12.dp))
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Characteristic",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = charInfo.uuid.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Properties: ${
                                buildList {
                                    if (charInfo.canRead) add("READ")
                                    if (charInfo.canWrite) add("WRITE")
                                    if (charInfo.canWriteNoResponse) add("WRITE_NO_RESP")
                                    if (charInfo.canNotify) add("NOTIFY")
                                    if (charInfo.canIndicate) add("INDICATE")
                                }.joinToString(" | ")
                            }",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (charInfo.canRead) {
                                FilledTonalButton(
                                    onClick = { onRead(charInfo) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.ReadMore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Read", fontSize = 10.sp)
                                }
                            }
                            if (charInfo.canWrite || charInfo.canWriteNoResponse) {
                                FilledTonalButton(
                                    onClick = { onWrite(charInfo) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Write", fontSize = 10.sp)
                                }
                            }
                            if (charInfo.canNotify || charInfo.canIndicate) {
                                val isEnabled = notificationEnabledChars.contains(charInfo.uuid.toString())
                                FilledTonalButton(
                                    onClick = {
                                        onToggleNotification(charInfo, !isEnabled)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        if (isEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(if (isEnabled) "Stop" else "Notify", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
