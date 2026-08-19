package com.walnut.beaconfinder.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssiScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()

    var timeWindow by remember { mutableStateOf(30_000L) }

    LaunchedEffect(device) {
        device?.let {
            viewModel.addRssiSample(it.rssi)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSSI Graph") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Current RSSI
            item {
                device?.let { dev ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Current RSSI", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${dev.rssi} dBm",
                                fontSize = 32.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    dev.rssi > -50 -> Color(0xFF4CAF50)
                                    dev.rssi > -70 -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                            viewModel.rssiProcessor.getMovingAverage(10)?.let { avg ->
                                Text(
                                    text = "Average: ${String.format("%.1f", avg)} dBm",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Time window selector
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10_000L to "10s", 30_000L to "30s", 60_000L to "1m", 300_000L to "5m").forEach { (ms, label) ->
                        FilterChip(
                            selected = timeWindow == ms,
                            onClick = { timeWindow = ms },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // RSSI Graph
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("RSSI History", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        val samples = viewModel.rssiProcessor.getSamplesWithinTimeWindow(timeWindow)

                        if (samples.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    "Collecting data...",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                val width = size.width
                                val height = size.height
                                val minRssi = -90f
                                val maxRssi = -20f
                                val range = maxRssi - minRssi

                                // Grid lines
                                for (rssi in -80..-30 step 10) {
                                    val y = height - ((rssi - minRssi) / range * height)
                                    drawLine(
                                        color = Color.Gray.copy(alpha = 0.3f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 1f
                                    )
                                }

                                // Data points
                                if (samples.size > 1) {
                                    val path = Path()
                                    samples.forEachIndexed { index, sample ->
                                        val x = index.toFloat() / (samples.size - 1) * width
                                        val y = height - ((sample.rssi - minRssi) / range * height)
                                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF2196F3),
                                        style = Stroke(width = 2f)
                                    )
                                }

                                // Latest point
                                samples.lastOrNull()?.let { latest ->
                                    val x = width
                                    val y = height - ((latest.rssi - minRssi) / range * height)
                                    drawCircle(
                                        color = Color(0xFFF44336),
                                        radius = 6f,
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
