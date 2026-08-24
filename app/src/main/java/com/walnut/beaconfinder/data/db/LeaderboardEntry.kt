package com.walnut.beaconfinder.data.db

data class LeaderboardEntry(
    val beaconAddress: String,
    val beaconName: String,
    val protocol: String,
    val seenCount: Int,
    val lastSeen: Long
)
