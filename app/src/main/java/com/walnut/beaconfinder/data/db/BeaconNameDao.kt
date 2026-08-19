package com.walnut.beaconfinder.data.db

import androidx.room.*

@Dao
interface BeaconNameDao {
    @Query("SELECT * FROM beacon_names")
    suspend fun getAll(): List<BeaconNameEntity>

    @Query("SELECT * FROM beacon_names WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): BeaconNameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BeaconNameEntity)

    @Delete
    suspend fun delete(entity: BeaconNameEntity)

    @Query("DELETE FROM beacon_names WHERE address = :address")
    suspend fun deleteByAddress(address: String)
}
