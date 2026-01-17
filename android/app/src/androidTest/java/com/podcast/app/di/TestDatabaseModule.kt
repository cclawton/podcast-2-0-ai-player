package com.podcast.app.di

import android.content.Context
import androidx.room.Room
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.dao.SearchHistoryDao
import com.podcast.app.data.local.database.PodcastDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test database module that provides an in-memory database for UI tests.
 * Replaces the production DatabaseModule during instrumented tests.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): PodcastDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            PodcastDatabase::class.java
        )
            .allowMainThreadQueries() // Allow for test simplicity
            .build()
    }

    @Provides
    fun providePodcastDao(database: PodcastDatabase): PodcastDao {
        return database.podcastDao()
    }

    @Provides
    fun provideEpisodeDao(database: PodcastDatabase): EpisodeDao {
        return database.episodeDao()
    }

    @Provides
    fun providePlaybackProgressDao(database: PodcastDatabase): PlaybackProgressDao {
        return database.playbackProgressDao()
    }

    @Provides
    fun provideDownloadDao(database: PodcastDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: PodcastDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }
}
