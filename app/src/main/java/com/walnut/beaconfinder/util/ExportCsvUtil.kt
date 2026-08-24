package com.walnut.beaconfinder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.walnut.beaconfinder.data.db.AlertHistoryEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportCsvUtil {

    fun exportAlertHistory(context: Context, alerts: List<AlertHistoryEntity>): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "beaconfinder_alerts_$timestamp.csv")

        file.bufferedWriter().use { writer ->
            writer.appendLine("Timestamp,Beacon Name,Address,Protocol,Alert Type,RSSI,Distance (m)")
            for (a in alerts) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(a.timestamp))
                writer.appendLine("$date,\"${a.beaconName}\",${a.beaconAddress},${a.protocol},${a.alertType},${a.rssi},${a.distanceMeters}")
            }
        }

        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            val uri = Uri.fromFile(file)
            uri
        }
    }

    fun exportScanResults(context: Context, results: Map<String, com.walnut.beaconfinder.data.model.BeaconDevice>): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "beaconfinder_scan_$timestamp.csv")

        file.bufferedWriter().use { writer ->
            writer.appendLine("Address,Name,Protocol,RSSI,TxPower,Distance (m),First Seen,Last Seen,UUID,Major,Minor,Namespace,Instance")
            for ((_, device) in results) {
                val dist = device.distance?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                val first = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(device.firstSeen))
                val last = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(device.lastSeen))
                val uuid = device.iBeaconUuid ?: ""
                val major = device.iBeaconMajor?.toString() ?: ""
                val minor = device.iBeaconMinor?.toString() ?: ""
                val ns = device.eddystoneNamespace ?: ""
                val inst = device.eddystoneInstance ?: ""
                writer.appendLine("${device.address},\"${device.displayName}\",${device.protocol},${device.rssi},${device.txPower ?: ""},$dist,$first,$last,$uuid,$major,$minor,$ns,$inst")
            }
        }

        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String = "text/csv") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    }
}
