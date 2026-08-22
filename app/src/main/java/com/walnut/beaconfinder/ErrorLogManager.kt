package com.walnut.beaconfinder

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object ErrorLogManager {

    private const val TAG = "ErrorLogManager"
    private const val ERROR_LOG_FILE = "app_error_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 100_000L
    private const val MAX_LOG_ENTRIES = 200

    private val recentErrors = ConcurrentLinkedQueue<LogEntry>()

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String,
        val tag: String,
        val message: String,
        val stackTrace: String? = null
    ) {
        fun formatted(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val time = sdf.format(Date(timestamp))
            val sb = StringBuilder("[$time] $level/$tag: $message")
            if (!stackTrace.isNullOrBlank()) {
                sb.append("\n$stackTrace")
            }
            return sb.toString()
        }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val sw = StringWriter()
        throwable?.printStackTrace(PrintWriter(sw))
        val entry = LogEntry(
            level = "ERROR",
            tag = tag,
            message = message,
            stackTrace = sw.toString().takeIf { throwable != null }
        )
        recentErrors.add(entry)
        trimRecentErrors()
        appendToFile(entry)
        Log.e(tag, message, throwable)
    }

    fun logWarning(tag: String, message: String) {
        val entry = LogEntry(level = "WARN", tag = tag, message = message)
        recentErrors.add(entry)
        trimRecentErrors()
        appendToFile(entry)
        Log.w(tag, message)
    }

    fun logInfo(tag: String, message: String) {
        val entry = LogEntry(level = "INFO", tag = tag, message = message)
        recentErrors.add(entry)
        trimRecentErrors()
        appendToFile(entry)
        Log.i(tag, message)
    }

    private fun trimRecentErrors() {
        while (recentErrors.size > MAX_LOG_ENTRIES) {
            recentErrors.poll()
        }
    }

    private fun appendToFile(entry: LogEntry) {
        try {
            val file = File(appContext?.filesDir, ERROR_LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                file.writeText("")
            }
            file.appendText(entry.formatted() + "\n\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write error log", e)
        }
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Suppress("UNUSED_PARAMETER")
    fun getLogFileContent(context: Context): String {
        return try {
            val file = File(context.filesDir, ERROR_LOG_FILE)
            if (file.exists()) file.readText() else "No error log found."
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun getLogcatErrors(context: Context, lines: Int = 300): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-v", "time",
                "*:S",
                "BackgroundScanService:E",
                "BluetoothStateReceiver:E",
                "BluetoothStateObserver:E",
                "ScanWatchdog:E",
                "BootReceiver:E",
                "BleScannerManager:E",
                "ScannerViewModel:E",
                "TtsManager:E",
                "BeaconFinderApp:E",
                "AndroidRuntime:E",
                "System.err:W",
                "-t", "$lines"
            ))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            reader.close()
            process.destroy()
            val result = sb.toString()
            if (result.isBlank()) "No app errors found." else result
        } catch (e: Exception) {
            "Failed to read logcat: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            val file = File(context.filesDir, ERROR_LOG_FILE)
            if (file.exists()) file.delete()
            recentErrors.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear error log", e)
        }
    }

    fun setupGlobalHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val crashText = "[$timestamp] CRASH on ${thread.name}: ${throwable.message}\n$sw\n\n"
                val file = File(appContext?.filesDir, BeaconFinderApp.CRASH_LOG_FILE)
                file.appendText(crashText)
                logError("CrashHandler", "Uncaught exception on ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
