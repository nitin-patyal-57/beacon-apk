package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val address: String,
    val name: String? = null,
    val protocol: String? = null,
    val notes: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
