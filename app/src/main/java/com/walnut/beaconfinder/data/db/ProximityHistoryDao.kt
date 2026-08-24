package com.walnut.beaconfinder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProximityHistoryDao {
    @Insert
    suspend fun insert(entry: ProximityHistoryEntity): Long

    @Update
    suspend fun update(entry: ProximityHistoryEntity)

    @Query("SELECT * FROM proximity_history ORDER BY enterTime DESC LIMIT 200")
    fun getRecent(): Flow<List<ProximityHistoryEntity>>

    @Query("SELECT * FROM proximity_history WHERE beaconAddress = :address ORDER BY enterTime DESC LIMIT 50")
    fun getByAddress(address: String): Flow<List<ProximityHistoryEntity>>

    @Query("SELECT beaconAddress, beaconName, protocol, SUM(totalTimeMs) as totalTime FROM proximity_history GROUP BY beaconAddress ORDER BY totalTime DESC")
    fun getTimeSummary(): Flow<List<TimeSummaryEntry>>

    @Query("DELETE FROM proximity_history")
    suspend fun clearAll()
}

data class TimeSummaryEntry(
    val beaconAddress: String,
    val beaconName: String,
    val protocol: String,
    val totalTime: Long
)
