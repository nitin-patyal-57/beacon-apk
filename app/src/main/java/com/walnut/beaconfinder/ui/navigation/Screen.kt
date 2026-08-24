package com.walnut.beaconfinder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Scanner : Screen("scanner", "Scanner", Icons.Default.BluetoothSearching)
    object KnownBeacons : Screen("known_beacons", "Known Beacons", Icons.Default.Star)
    object CustomFormats : Screen("custom_formats", "Custom Formats", Icons.Default.Tune)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AlertHistory : Screen("alert_history", "Alerts", Icons.Default.History)
    object Zones : Screen("zones", "Zones", Icons.Default.Place)
    object Leaderboard : Screen("leaderboard", "Leaderboard", Icons.Default.EmojiEvents)
    object ProximityHistory : Screen("proximity_history", "Time Near", Icons.Default.Timer)
    object ScanHistory : Screen("scan_history", "All Seen", Icons.Default.Visibility)
    object Detail : Screen("detail/{address}", "Detail", Icons.Default.Info) {
        fun createRoute(address: String) = "detail/$address"
    }
    object Raw : Screen("raw/{address}", "Raw", Icons.Default.Code) {
        fun createRoute(address: String) = "raw/$address"
    }
    object Gatt : Screen("gatt/{address}", "GATT", Icons.Default.BluetoothConnected) {
        fun createRoute(address: String) = "gatt/$address"
    }
    object Rssi : Screen("rssi/{address}", "RSSI", Icons.Default.SignalCellularAlt) {
        fun createRoute(address: String) = "rssi/$address"
    }
    object History : Screen("history/{address}", "History", Icons.Default.History) {
        fun createRoute(address: String) = "history/$address"
    }
}

val bottomNavItems = listOf(
    Screen.Scanner,
    Screen.KnownBeacons,
    Screen.Zones,
    Screen.Settings
)
