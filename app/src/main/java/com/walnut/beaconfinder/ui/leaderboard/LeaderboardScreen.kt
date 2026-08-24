package com.walnut.beaconfinder.ui.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walnut.beaconfinder.data.db.BeaconDatabase
import com.walnut.beaconfinder.data.db.LeaderboardEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(listOf<LeaderboardEntry>()) }
    var periodHours by remember { mutableIntStateOf(24) }

    LaunchedEffect(periodHours) {
        withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - (periodHours * 3600_000L)
            BeaconDatabase.getInstance(context).scanHistoryDao().getLeaderboard(since).collect {
                entries = it
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = "${periodHours}h",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h", 72 to "3d", 168 to "7d").forEach { (hours, label) ->
                    FilterChip(
                        selected = periodHours == hours,
                        onClick = { periodHours = hours },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scan history yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn {
                    itemsIndexed(entries) { index, entry ->
                        val medal = when (index) {
                            0 -> "\uD83E\uDD47"
                            1 -> "\uD83E\uDD48"
                            2 -> "\uD83E\uDD49"
                            else -> "#${index + 1}"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = medal.toString(),
                                    fontSize = 20.sp,
                                    modifier = Modifier.width(40.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.beaconName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${entry.protocol} · ${entry.beaconAddress}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${entry.seenCount}x",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val lastSeenSdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                    val lastSeenStr = remember(entry.lastSeen) { lastSeenSdf.format(Date(entry.lastSeen)) }
                                    Text(
                                        text = "Last: $lastSeenStr",
                                        fontSize = 10.sp,
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
}
