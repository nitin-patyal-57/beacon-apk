package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proximity_history")
data class ProximityHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beaconAddress: String,
    val beaconName: String,
    val protocol: String,
    val enterTime: Long = System.currentTimeMillis(),
    val exitTime: Long = 0L,
    val totalTimeMs: Long = 0L
)
