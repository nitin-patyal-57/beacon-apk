package com.walnut.beaconfinder.ui.knownbeacons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.data.db.KnownBeaconEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownBeaconsScreen(
    viewModel: KnownBeaconsViewModel = hiltViewModel()
) {
    val beacons by viewModel.beacons.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Known Beacons") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (beacons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No known beacons configured",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add beacons to enable notifications and auto-connect",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(beacons) { beacon ->
                    KnownBeaconCard(
                        beacon = beacon,
                        onDelete = { viewModel.deleteBeacon(beacon) },
                        onEdit = { viewModel.setEditingBeacon(beacon) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditBeaconDialog(
            beacon = null,
            onDismiss = { showAddDialog = false },
            onSave = { entity ->
                viewModel.addBeacon(entity)
                showAddDialog = false
            }
        )
    }

    viewModel.editingBeacon.collectAsStateWithLifecycle().value?.let { beacon ->
        AddEditBeaconDialog(
            beacon = beacon,
            onDismiss = { viewModel.setEditingBeacon(null) },
            onSave = { entity ->
                viewModel.updateBeacon(entity)
                viewModel.setEditingBeacon(null)
            }
        )
    }
}

@Composable
fun KnownBeaconCard(
    beacon: KnownBeaconEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = beacon.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = beacon.protocol,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            beacon.uuid?.let { DetailItem("UUID", it) }
            beacon.major?.let { DetailItem("Major", it.toString()) }
            beacon.minor?.let { DetailItem("Minor", it.toString()) }
            beacon.namespace?.let { DetailItem("Namespace", it) }
            beacon.instance?.let { DetailItem("Instance", it) }
            beacon.url?.let { DetailItem("URL", it) }

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailItem("Notify", if (beacon.notificationEnabled) "ON" else "OFF")
                DetailItem("Auto Connect", if (beacon.autoConnectEnabled) "ON" else "OFF")
            }
            DetailItem("Min RSSI", "${beacon.minRssi} dBm")
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${beacon.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(text = value, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBeaconDialog(
    beacon: KnownBeaconEntity?,
    onDismiss: () -> Unit,
    onSave: (KnownBeaconEntity) -> Unit
) {
    var name by remember { mutableStateOf(beacon?.name ?: "") }
    var protocol by remember { mutableStateOf(beacon?.protocol ?: "IBEACON") }
    var uuid by remember { mutableStateOf(beacon?.uuid ?: "") }
    var major by remember { mutableStateOf(beacon?.major?.toString() ?: "") }
    var minor by remember { mutableStateOf(beacon?.minor?.toString() ?: "") }
    var namespace by remember { mutableStateOf(beacon?.namespace ?: "") }
    var instance by remember { mutableStateOf(beacon?.instance ?: "") }
    var url by remember { mutableStateOf(beacon?.url ?: "") }
    var notificationEnabled by remember { mutableStateOf(beacon?.notificationEnabled ?: true) }
    var autoConnectEnabled by remember { mutableStateOf(beacon?.autoConnectEnabled ?: false) }
    var minRssi by remember { mutableStateOf(beacon?.minRssi?.toString() ?: "-80") }
    var timeout by remember { mutableStateOf((beacon?.presenceTimeoutMs ?: 30000L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (beacon != null) "Edit Beacon" else "Add Known Beacon") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Protocol selector
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = protocol,
                        onValueChange = {},
                        label = { Text("Protocol") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("IBEACON", "EDDYSTONE_UID", "EDDYSTONE_URL", "CUSTOM_BLE").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = { protocol = p; expanded = false }
                            )
                        }
                    }
                }

                // Protocol-specific fields
                when (protocol) {
                    "IBEACON" -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uuid,
                            onValueChange = { uuid = it },
                            label = { Text("UUID") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = major,
                                onValueChange = { major = it },
                                label = { Text("Major") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = minor,
                                onValueChange = { minor = it },
                                label = { Text("Minor") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    "EDDYSTONE_UID" -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = namespace,
                            onValueChange = { namespace = it },
                            label = { Text("Namespace") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = instance,
                            onValueChange = { instance = it },
                            label = { Text("Instance") },
                            singleLine = true
                        )
                    }
                    "EDDYSTONE_URL" -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("URL") },
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = notificationEnabled,
                        onCheckedChange = { notificationEnabled = it }
                    )
                    Text("Nearby Notification")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoConnectEnabled,
                        onCheckedChange = { autoConnectEnabled = it }
                    )
                    Text("Automatic Connection")
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = minRssi,
                    onValueChange = { minRssi = it },
                    label = { Text("Min RSSI (dBm)") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text("Presence Timeout (ms)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val identifierKey = when (protocol) {
                            "IBEACON" -> "iBeacon:$uuid:${major}:${minor}"
                            "EDDYSTONE_UID" -> "EddystoneUID:$namespace:$instance"
                            "EDDYSTONE_URL" -> "EddystoneURL:$url"
                            else -> "Custom:"
                        }
                        onSave(
                            KnownBeaconEntity(
                                id = beacon?.id ?: 0,
                                name = name,
                                protocol = protocol,
                                identifierKey = identifierKey,
                                uuid = uuid.ifBlank { null },
                                major = major.toIntOrNull(),
                                minor = minor.toIntOrNull(),
                                namespace = namespace.ifBlank { null },
                                instance = instance.ifBlank { null },
                                url = url.ifBlank { null },
                                notificationEnabled = notificationEnabled,
                                autoConnectEnabled = autoConnectEnabled,
                                minRssi = minRssi.toIntOrNull() ?: -80,
                                presenceTimeoutMs = timeout.toLongOrNull() ?: 30000L
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
