package com.walnut.beaconfinder.ui.customformats

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
import com.walnut.beaconfinder.data.db.CustomFormatEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFormatsScreen(
    viewModel: CustomFormatsViewModel = hiltViewModel()
) {
    val formats by viewModel.formats.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Custom Formats") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (formats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No custom formats defined",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Define custom beacon formats to detect proprietary protocols",
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
                items(formats) { format ->
                    CustomFormatCard(
                        format = format,
                        onDelete = { viewModel.deleteFormat(format) },
                        onEdit = { viewModel.setEditingFormat(format) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditFormatDialog(
            format = null,
            onDismiss = { showAddDialog = false },
            onSave = { entity ->
                viewModel.addFormat(entity)
                showAddDialog = false
            }
        )
    }

    viewModel.editingFormat.collectAsStateWithLifecycle().value?.let { format ->
        AddEditFormatDialog(
            format = format,
            onDismiss = { viewModel.setEditingFormat(null) },
            onSave = { entity ->
                viewModel.updateFormat(entity)
                viewModel.setEditingFormat(null)
            }
        )
    }
}

@Composable
fun CustomFormatCard(
    format: CustomFormatEntity,
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
                Text(text = format.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

            Text(
                text = "Manufacturer ID: 0x${String.format("%04X", format.manufacturerId)}",
                fontSize = 12.sp
            )
            format.frameSignatureHex?.let {
                Text(text = "Frame Signature: $it", fontSize = 12.sp)
            }
            Text(
                text = "Identifier: offset=${format.identifierOffset}, length=${format.identifierLength}",
                fontSize = 12.sp
            )
            format.serviceUuid?.let {
                Text(text = "Service UUID: $it", fontSize = 12.sp)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${format.name}?") },
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
fun AddEditFormatDialog(
    format: CustomFormatEntity?,
    onDismiss: () -> Unit,
    onSave: (CustomFormatEntity) -> Unit
) {
    var name by remember { mutableStateOf(format?.name ?: "") }
    var manufacturerId by remember { mutableStateOf(format?.manufacturerId?.let { String.format("%04X", it) } ?: "") }
    var frameSignature by remember { mutableStateOf(format?.frameSignatureHex ?: "") }
    var identifierOffset by remember { mutableStateOf(format?.identifierOffset?.toString() ?: "0") }
    var identifierLength by remember { mutableStateOf(format?.identifierLength?.toString() ?: "4") }
    var serviceUuid by remember { mutableStateOf(format?.serviceUuid ?: "") }
    var serviceDataPrefix by remember { mutableStateOf(format?.serviceDataPrefixHex ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format != null) "Edit Format" else "Add Custom Format") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = manufacturerId,
                    onValueChange = { manufacturerId = it },
                    label = { Text("Manufacturer ID (hex) *") },
                    placeholder = { Text("e.g. 004C") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = frameSignature,
                    onValueChange = { frameSignature = it },
                    label = { Text("Frame Signature (hex, optional)") },
                    placeholder = { Text("e.g. 02 15") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = identifierOffset,
                        onValueChange = { identifierOffset = it },
                        label = { Text("ID Offset") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = identifierLength,
                        onValueChange = { identifierLength = it },
                        label = { Text("ID Length") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = serviceUuid,
                    onValueChange = { serviceUuid = it },
                    label = { Text("Service UUID (optional)") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = serviceDataPrefix,
                    onValueChange = { serviceDataPrefix = it },
                    label = { Text("Service Data Prefix (hex, optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mfgId = manufacturerId.replace("0x", "").replace(" ", "").toIntOrNull(16) ?: return@TextButton
                    if (name.isNotBlank()) {
                        onSave(
                            CustomFormatEntity(
                                id = format?.id ?: 0,
                                name = name,
                                manufacturerId = mfgId,
                                frameSignatureHex = frameSignature.ifBlank { null },
                                identifierOffset = identifierOffset.toIntOrNull() ?: 0,
                                identifierLength = identifierLength.toIntOrNull() ?: 4,
                                serviceUuid = serviceUuid.ifBlank { null },
                                serviceDataPrefixHex = serviceDataPrefix.ifBlank { null }
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && manufacturerId.isNotBlank()
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
