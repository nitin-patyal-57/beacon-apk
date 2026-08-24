package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_beacons")
data class KnownBeaconEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val identifierKey: String,
    val uuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val namespace: String? = null,
    val instance: String? = null,
    val url: String? = null,
    val address: String? = null,
    val notificationEnabled: Boolean = true,
    val autoConnectEnabled: Boolean = false,
    val presenceTimeoutMs: Long = 30_000L,
    val minRssi: Int = -80,
    val gattServiceUuid: String? = null,
    val maxRetries: Int = 3,
    val soundUri: String? = null
)
