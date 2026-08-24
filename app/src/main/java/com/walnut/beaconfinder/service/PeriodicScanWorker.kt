package com.walnut.beaconfinder.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PeriodicScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "PeriodicScanWorker fired — checking service")

        if (!BackgroundScanService.isServiceScanning) {
            Log.w(TAG, "Service not scanning, attempting restart")
            try {
                BackgroundScanService.forceRestart(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart service from worker", e)
            }
        } else {
            Log.d(TAG, "Service is alive and scanning")
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "PeriodicScanWorker"
        const val WORK_NAME = "beacon_scan_watchdog"
    }
}
