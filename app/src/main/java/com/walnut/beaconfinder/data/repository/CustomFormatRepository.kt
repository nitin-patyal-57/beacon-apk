package com.walnut.beaconfinder.data.repository

import com.walnut.beaconfinder.data.db.CustomFormatDao
import com.walnut.beaconfinder.data.db.CustomFormatEntity
import com.walnut.beaconfinder.data.parser.CustomBeaconParser
import android.os.ParcelUuid
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFormatRepository @Inject constructor(
    private val dao: CustomFormatDao
) {
    suspend fun getAll(): List<CustomFormatEntity> = dao.getAll()

    suspend fun getById(id: Long): CustomFormatEntity? = dao.getById(id)

    suspend fun insert(entity: CustomFormatEntity): Long = dao.insert(entity)

    suspend fun update(entity: CustomFormatEntity) = dao.update(entity)

    suspend fun delete(entity: CustomFormatEntity) = dao.delete(entity)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun toParserFormats(): List<CustomBeaconParser.CustomFormat> {
        return dao.getAll().map { entity ->
            CustomBeaconParser.CustomFormat(
                name = entity.name,
                manufacturerId = entity.manufacturerId,
                frameSignature = entity.frameSignatureHex?.let { hexToBytes(it) },
                identifierOffset = entity.identifierOffset,
                identifierLength = entity.identifierLength,
                serviceUuid = entity.serviceUuid?.let { ParcelUuid.fromString(it) },
                serviceDataPrefix = entity.serviceDataPrefixHex?.let { hexToBytes(it) }
            )
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
