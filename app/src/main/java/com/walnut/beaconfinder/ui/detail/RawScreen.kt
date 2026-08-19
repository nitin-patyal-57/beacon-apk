package com.walnut.beaconfinder.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raw Advertisement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val hex = device?.rawAdvertisement?.joinToString(" ") {
                            String.format("%02X", it)
                        } ?: ""
                        clipboardManager.setText(AnnotatedString(hex))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            )
        }
    ) { padding ->
        device?.let { dev ->
            val rawBytes = dev.rawAdvertisement

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Raw hex dump
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "RAW ADVERTISEMENT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Group bytes by 16 for readability
                            val grouped = rawBytes.toList().chunked(16)
                            grouped.forEachIndexed { lineIndex, line ->
                                val offset = lineIndex * 16
                                val offsetStr = String.format("%04X", offset)
                                val hexStr = line.joinToString(" ") { String.format("%02X", it) }
                                val asciiStr = line.joinToString("") { b ->
                                    val c = b.toInt() and 0xFF
                                    if (c in 0x20..0x7E) c.toChar().toString() else "."
                                }

                                Text(
                                    text = "$offsetStr  $hexStr  $asciiStr",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // AD Structure parsing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "AD STRUCTURES",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            parseAdStructures(rawBytes).forEach { structure ->
                                Text(
                                    text = structure,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Manufacturer data
                dev.manufacturerData?.let { data ->
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "MANUFACTURER DATA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = data.joinToString(" ") { String.format("%02X", it) },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Service data
                if (dev.serviceData.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "SERVICE DATA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                dev.serviceData.forEach { (uuid, data) ->
                                    Text(
                                        text = "UUID: $uuid",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = data.joinToString(" ") { String.format("%02X", it) },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseAdStructures(rawBytes: ByteArray): List<String> {
    val structures = mutableListOf<String>()
    var i = 0
    while (i < rawBytes.size) {
        val length = rawBytes[i].toInt() and 0xFF
        if (length == 0) break

        if (i + length >= rawBytes.size) {
            structures.add("Truncated structure at offset $i")
            break
        }

        val type = rawBytes[i + 1].toInt() and 0xFF
        val data = rawBytes.copyOfRange(i + 2, i + 1 + length)

        val typeName = when (type) {
            0x01 -> "Flags"
            0x02 -> "Incomplete List of 16-bit Service UUIDs"
            0x03 -> "Complete List of 16-bit Service UUIDs"
            0x06 -> "Incomplete List of 128-bit Service UUIDs"
            0x07 -> "Complete List of 128-bit Service UUIDs"
            0x08 -> "Shortened Local Name"
            0x09 -> "Complete Local Name"
            0x0A -> "TX Power Level"
            0xFF -> "Manufacturer Specific Data"
            else -> "Type 0x${String.format("%02X", type)}"
        }

        structures.add("Offset $i: Length=$length Type=$typeName")
        if (type == 0xFF && data.size >= 2) {
            val mfgId = (data[1].toInt() and 0xFF shl 8) or (data[0].toInt() and 0xFF)
            structures.add("  Manufacturer ID: 0x${String.format("%04X", mfgId)}")
        }
        if (type == 0x09 || type == 0x08) {
            structures.add("  Name: ${String(data, Charsets.UTF_8)}")
        }

        i += 1 + length
    }
    return structures
}
