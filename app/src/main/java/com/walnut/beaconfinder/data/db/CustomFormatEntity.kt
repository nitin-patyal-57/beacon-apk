package com.walnut.beaconfinder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_formats")
data class CustomFormatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val manufacturerId: Int,
    val frameSignatureHex: String? = null,
    val identifierOffset: Int = 0,
    val identifierLength: Int = 4,
    val serviceUuid: String? = null,
    val serviceDataPrefixHex: String? = null
)
