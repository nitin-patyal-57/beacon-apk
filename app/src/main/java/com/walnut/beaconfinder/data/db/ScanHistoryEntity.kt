package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beaconAddress: String,
    val beaconName: String,
    val protocol: String,
    val rssi: Int,
    val distanceMeters: Double,
    val timestamp: Long = System.currentTimeMillis()
)
