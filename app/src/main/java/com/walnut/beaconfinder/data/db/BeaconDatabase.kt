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
        BeaconNameEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BeaconDatabase : RoomDatabase() {
    abstract fun knownBeaconDao(): KnownBeaconDao
    abstract fun customFormatDao(): CustomFormatDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun beaconNameDao(): BeaconNameDao

    companion object {
        @Volatile
        private var INSTANCE: BeaconDatabase? = null

        fun getInstance(context: Context): BeaconDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BeaconDatabase::class.java,
                    "beacon_finder.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
