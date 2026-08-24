package com.walnut.beaconfinder.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name ASC")
    fun getAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY name ASC")
    suspend fun getAllSync(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: Long): ZoneEntity?

    @Insert
    suspend fun insert(entity: ZoneEntity): Long

    @Update
    suspend fun update(entity: ZoneEntity)

    @Delete
    suspend fun delete(entity: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteById(id: Long)
}
