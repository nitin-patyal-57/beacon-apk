package com.walnut.beaconfinder.di

import android.content.Context
import com.walnut.beaconfinder.data.db.BeaconDatabase
import com.walnut.beaconfinder.data.db.CustomFormatDao
import com.walnut.beaconfinder.data.db.FavoriteDao
import com.walnut.beaconfinder.data.db.KnownBeaconDao
import com.walnut.beaconfinder.data.db.BeaconNameDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeaconDatabase {
        return BeaconDatabase.getInstance(context)
    }

    @Provides
    fun provideKnownBeaconDao(db: BeaconDatabase): KnownBeaconDao = db.knownBeaconDao()

    @Provides
    fun provideCustomFormatDao(db: BeaconDatabase): CustomFormatDao = db.customFormatDao()

    @Provides
    fun provideFavoriteDao(db: BeaconDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideBeaconNameDao(db: BeaconDatabase): BeaconNameDao = db.beaconNameDao()
}
