package com.walnut.beaconfinder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        KnownBeaconEntity::class,
        CustomFormatEntity::class,
        FavoriteEntity::class,
        BeaconNameEntity::class,
        AlertHistoryEntity::class,
        ZoneEntity::class,
        ScanHistoryEntity::class,
        ProximityHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class BeaconDatabase : RoomDatabase() {
    abstract fun knownBeaconDao(): KnownBeaconDao
    abstract fun customFormatDao(): CustomFormatDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun beaconNameDao(): BeaconNameDao
    abstract fun alertHistoryDao(): AlertHistoryDao
    abstract fun zoneDao(): ZoneDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun proximityHistoryDao(): ProximityHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: BeaconDatabase? = null

        fun getInstance(context: Context): BeaconDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BeaconDatabase::class.java,
                    "beacon_finder.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
