package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val beaconKeys: String = "[]",
    val notificationEnabled: Boolean = true,
    val soundUri: String? = null
)
