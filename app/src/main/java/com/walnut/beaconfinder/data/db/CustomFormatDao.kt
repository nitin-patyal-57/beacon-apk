package com.walnut.beaconfinder.data.db

import androidx.room.*

@Dao
interface CustomFormatDao {
    @Query("SELECT * FROM custom_formats ORDER BY name ASC")
    suspend fun getAll(): List<CustomFormatEntity>

    @Query("SELECT * FROM custom_formats WHERE id = :id")
    suspend fun getById(id: Long): CustomFormatEntity?

    @Insert
    suspend fun insert(entity: CustomFormatEntity): Long

    @Update
    suspend fun update(entity: CustomFormatEntity)

    @Delete
    suspend fun delete(entity: CustomFormatEntity)

    @Query("DELETE FROM custom_formats WHERE id = :id")
    suspend fun deleteById(id: Long)
}
