package com.walnut.beaconfinder.data.model

data class RssiSample(
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
