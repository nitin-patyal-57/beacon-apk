package com.walnut.beaconfinder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insert(entry: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 500")
    fun getRecent(): Flow<List<ScanHistoryEntity>>

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()

    @Query("DELETE FROM scan_history WHERE timestamp < :before")
    suspend fun clearOlderThan(before: Long)

    @Query("SELECT beaconAddress, beaconName, protocol, COUNT(*) as seenCount, MAX(timestamp) as lastSeen FROM scan_history WHERE timestamp > :since GROUP BY beaconAddress ORDER BY seenCount DESC")
    fun getLeaderboard(since: Long): Flow<List<LeaderboardEntry>>
}
