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
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PodcastDatabase {
        return Room.databaseBuilder(
            context,
            PodcastDatabase::class.java,
            PodcastDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
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
