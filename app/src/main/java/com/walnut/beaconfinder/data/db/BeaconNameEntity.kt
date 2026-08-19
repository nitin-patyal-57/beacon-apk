package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beacon_names")
data class BeaconNameEntity(
    @PrimaryKey val address: String,
    val customName: String
)
