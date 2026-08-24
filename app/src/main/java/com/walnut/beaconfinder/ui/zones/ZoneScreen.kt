package com.walnut.beaconfinder.ui.zones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walnut.beaconfinder.data.db.ZoneEntity
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneScreen(viewModel: ZoneViewModel = hiltViewModel()) {
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingZone by remember { mutableStateOf<ZoneEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Zones") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Zone")
            }
        }
    ) { padding ->
        if (zones.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No zones configured",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Create zones to group beacons (e.g., Office, Front Door)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(zones) { zone ->
                    val beaconCount = try {
                        JSONArray(zone.beaconKeys).length()
                    } catch (_: Exception) { 0 }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(zone.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "$beaconCount beacon(s) \u00b7 ${if (zone.notificationEnabled) "Alerts ON" else "Alerts OFF"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { editingZone = zone }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteZone(zone) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ZoneDialog(
            name = "",
            beaconKeys = emptyList(),
            onDismiss = { showAddDialog = false },
            onSave = { name, keys ->
                viewModel.addZone(name, keys)
                showAddDialog = false
            }
        )
    }

    editingZone?.let { zone ->
        val existingKeys = try {
            val arr = JSONArray(zone.beaconKeys)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }

        ZoneDialog(
            name = zone.name,
            beaconKeys = existingKeys,
            onDismiss = { editingZone = null },
            onSave = { name, keys ->
                viewModel.updateZone(zone.copy(name = name, beaconKeys = JSONArray(keys).toString()))
                editingZone = null
            }
        )
    }
}

@Composable
fun ZoneDialog(
    name: String,
    beaconKeys: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var zoneName by remember { mutableStateOf(name) }
    var beaconInput by remember { mutableStateOf(beaconKeys.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Zone") },
        text = {
            Column {
                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it },
                    label = { Text("Zone Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = beaconInput,
                    onValueChange = { beaconInput = it },
                    label = { Text("Beacon Identity Keys (one per line)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Paste identity keys from known beacons list",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (zoneName.isNotBlank()) {
                        val keys = beaconInput.lines().filter { it.isNotBlank() }.map { it.trim() }
                        onSave(zoneName, keys)
                    }
                },
                enabled = zoneName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
