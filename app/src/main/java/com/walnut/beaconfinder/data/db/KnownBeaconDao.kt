package com.walnut.beaconfinder.data.db

import androidx.room.*

@Dao
interface KnownBeaconDao {
    @Query("SELECT * FROM known_beacons ORDER BY name ASC")
    suspend fun getAll(): List<KnownBeaconEntity>

    @Query("SELECT * FROM known_beacons WHERE id = :id")
    suspend fun getById(id: Long): KnownBeaconEntity?

    @Query("SELECT * FROM known_beacons WHERE identifierKey = :identityKey LIMIT 1")
    suspend fun getByIdentityKey(identityKey: String): KnownBeaconEntity?

    @Insert
    suspend fun insert(entity: KnownBeaconEntity): Long

    @Update
    suspend fun update(entity: KnownBeaconEntity)

    @Delete
    suspend fun delete(entity: KnownBeaconEntity)

    @Query("DELETE FROM known_beacons WHERE id = :id")
    suspend fun deleteById(id: Long)
}
