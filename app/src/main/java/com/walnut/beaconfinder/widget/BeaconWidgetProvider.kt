package com.walnut.beaconfinder.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.walnut.beaconfinder.R
import com.walnut.beaconfinder.service.BackgroundScanService
import java.util.Locale

class BeaconWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_beacon_info)

            val devices = BackgroundScanService.scannedDevices.value
            val nearest = devices.values.maxByOrNull { it.rssi }

            if (nearest != null) {
                val distance = calculateDistance(nearest.rssi)
                views.setTextViewText(R.id.widget_beacon_name, nearest.displayName)
                views.setTextViewText(
                    R.id.widget_beacon_info,
                    String.format(Locale.US, "%s \u00b7 %.1fm \u00b7 %d dBm",
                        nearest.protocol.name, distance, nearest.rssi)
                )
                views.setTextViewText(R.id.widget_status, "Monitoring")
            } else {
                views.setTextViewText(R.id.widget_beacon_name, "No beacon detected")
                views.setTextViewText(R.id.widget_beacon_info, "Waiting for beacon signal...")
                views.setTextViewText(R.id.widget_status, "Scanning...")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun calculateDistance(rssi: Int): Double {
            if (rssi == 0) return -1.0
            val ratio = (-59.0 - rssi) / (10 * 2.0)
            return Math.pow(10.0, ratio)
        }
    }
}
