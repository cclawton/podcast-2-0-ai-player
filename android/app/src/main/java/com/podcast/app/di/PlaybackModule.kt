package com.podcast.app.di

import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides PlaybackController as IPlaybackController.
 * This enables test substitution via TestPlaybackModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: PlaybackController): IPlaybackController
}
