package com.walnut.beaconfinder.data.processing

import kotlin.math.pow

object DistanceCalculator {

    fun estimateDistance(rssi: Int, txPower: Int, environmentalFactor: Double = 2.0): Double {
        if (rssi == 0) return -1.0
        val ratio = (txPower - rssi).toDouble() / (10.0 * environmentalFactor)
        return if (ratio < 1.0) {
            10.0.pow(ratio)
        } else {
            10.0.pow(ratio) * 0.89976
        }
    }

    fun rssiToDistance(rssi: Int, measuredPower: Int = -59, n: Double = 2.0): Double {
        return 10.0.pow((measuredPower - rssi).toDouble() / (10.0 * n))
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters < 0 -> "Unknown"
            meters < 1.0 -> String.format("%.0f cm", meters * 100)
            meters < 10.0 -> String.format("%.1f m", meters)
            else -> String.format("%.0f m", meters)
        }
    }
}
