package com.walnut.beaconfinder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.walnut.beaconfinder.ui.scanner.ScannerScreen
import com.walnut.beaconfinder.ui.detail.DetailScreen
import com.walnut.beaconfinder.ui.detail.RawScreen
import com.walnut.beaconfinder.ui.detail.RssiScreen
import com.walnut.beaconfinder.ui.detail.HistoryScreen
import com.walnut.beaconfinder.ui.detail.GattScreen
import com.walnut.beaconfinder.ui.knownbeacons.KnownBeaconsScreen
import com.walnut.beaconfinder.ui.customformats.CustomFormatsScreen
import com.walnut.beaconfinder.ui.settings.SettingsScreen
import com.walnut.beaconfinder.ui.alerts.AlertHistoryScreen
import com.walnut.beaconfinder.ui.zones.ZoneScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Scanner.route
    ) {
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onDeviceClick = { address ->
                    navController.navigate(Screen.Detail.createRoute(address))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onRawClick = { address ->
                    navController.navigate(Screen.Raw.createRoute(address))
                },
                onRssiClick = { address ->
                    navController.navigate(Screen.Rssi.createRoute(address))
                },
                onHistoryClick = { address ->
                    navController.navigate(Screen.History.createRoute(address))
                },
                onGattClick = { address ->
                    navController.navigate(Screen.Gatt.createRoute(address))
                }
            )
        }

        composable(
            route = Screen.Raw.route,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) {
            RawScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Rssi.route,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) {
            RssiScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.History.route,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Gatt.route,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) {
            GattScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.KnownBeacons.route) {
            KnownBeaconsScreen()
        }

        composable(Screen.CustomFormats.route) {
            CustomFormatsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.AlertHistory.route) {
            AlertHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Zones.route) {
            ZoneScreen()
        }
    }
}
