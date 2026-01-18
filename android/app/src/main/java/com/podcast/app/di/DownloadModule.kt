package com.podcast.app.di

import android.content.Context
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.download.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for download-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        downloadDao: DownloadDao,
        episodeDao: EpisodeDao
    ): DownloadManager {
        return DownloadManager(context, downloadDao, episodeDao)
    }
}
