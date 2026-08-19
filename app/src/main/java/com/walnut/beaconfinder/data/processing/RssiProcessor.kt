package com.walnut.beaconfinder.data.processing

import com.walnut.beaconfinder.data.model.RssiSample
import java.util.concurrent.ConcurrentLinkedDeque

class RssiProcessor(private val maxSamples: Int = 100) {
    private val samples = ConcurrentLinkedDeque<RssiSample>()

    fun addSample(rssi: Int, timestamp: Long = System.currentTimeMillis()) {
        samples.addLast(RssiSample(rssi, timestamp))
        while (samples.size > maxSamples) {
            samples.pollFirst()
        }
    }

    fun getMovingAverage(windowSize: Int = 10): Double? {
        if (samples.size < windowSize) return null
        val recent = samples.toList().takeLast(windowSize)
        return recent.map { it.rssi }.average()
    }

    fun getLatest(): Int? = samples.lastOrNull()?.rssi

    fun getSamples(): List<RssiSample> = samples.toList()

    fun getSamplesWithinTimeWindow(windowMs: Long): List<RssiSample> {
        val cutoff = System.currentTimeMillis() - windowMs
        return samples.filter { it.timestamp >= cutoff }
    }

    fun clear() = samples.clear()
}
