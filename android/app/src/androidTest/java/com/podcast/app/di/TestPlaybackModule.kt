package com.podcast.app.di

import com.podcast.app.playback.FakePlaybackController
import com.podcast.app.playback.IPlaybackController
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that replaces PlaybackModule with FakePlaybackController.
 *
 * This enables UI tests to run without requiring real ExoPlayer or
 * database access, and ensures playback controls are visible by
 * providing a test episode.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PlaybackModule::class]
)
object TestPlaybackModule {

    @Provides
    @Singleton
    fun provideFakePlaybackController(): FakePlaybackController {
        return FakePlaybackController()
    }

    @Provides
    @Singleton
    fun providePlaybackController(fake: FakePlaybackController): IPlaybackController {
        return fake
    }
}
