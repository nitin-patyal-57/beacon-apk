package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_history")
data class AlertHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beaconName: String,
    val beaconAddress: String,
    val protocol: String,
    val alertType: String,
    val rssi: Int,
    val distanceMeters: Double,
    val timestamp: Long = System.currentTimeMillis()
)
