package com.walnut.beaconfinder.data.repository

import com.walnut.beaconfinder.data.db.KnownBeaconDao
import com.walnut.beaconfinder.data.db.KnownBeaconEntity
import com.walnut.beaconfinder.data.model.BeaconDevice
import com.walnut.beaconfinder.data.model.BeaconProtocol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownBeaconRepository @Inject constructor(
    private val dao: KnownBeaconDao
) {
    suspend fun getAll(): List<KnownBeaconEntity> = dao.getAll()

    suspend fun getById(id: Long): KnownBeaconEntity? = dao.getById(id)

    suspend fun getByIdentityKey(identityKey: String): KnownBeaconEntity? = dao.getByIdentityKey(identityKey)

    suspend fun insert(entity: KnownBeaconEntity): Long = dao.insert(entity)

    suspend fun update(entity: KnownBeaconEntity) = dao.update(entity)

    suspend fun delete(entity: KnownBeaconEntity) = dao.delete(entity)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun findMatchingBeacon(beacon: BeaconDevice): KnownBeaconEntity? {
        // Try exact identity key match first
        val exact = dao.getByIdentityKey(beacon.identityKey)
        if (exact != null) return exact

        // Try protocol-specific matching
        return when (beacon.protocol) {
            BeaconProtocol.IBEACON -> {
                dao.getAll().find {
                    it.protocol == "IBEACON" &&
                    it.uuid == beacon.iBeaconUuid &&
                    (it.major == null || it.major == beacon.iBeaconMajor) &&
                    (it.minor == null || it.minor == beacon.iBeaconMinor)
                }
            }
            BeaconProtocol.EDDYSTONE_UID -> {
                dao.getAll().find {
                    it.protocol == "EDDYSTONE_UID" &&
                    it.namespace == beacon.eddystoneNamespace &&
                    (it.instance == null || it.instance == beacon.eddystoneInstance)
                }
            }
            BeaconProtocol.EDDYSTONE_URL -> {
                dao.getAll().find {
                    it.protocol == "EDDYSTONE_URL" && it.url == beacon.eddystoneUrl
                }
            }
            else -> null
        }
    }
}
