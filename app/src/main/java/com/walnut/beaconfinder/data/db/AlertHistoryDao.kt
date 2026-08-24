package com.walnut.beaconfinder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertHistoryDao {
    @Insert
    suspend fun insert(alert: AlertHistoryEntity)

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC LIMIT 200")
    fun getRecent(): Flow<List<AlertHistoryEntity>>

    @Query("DELETE FROM alert_history")
    suspend fun clearAll()

    @Query("DELETE FROM alert_history WHERE timestamp < :before")
    suspend fun clearOlderThan(before: Long)
}
