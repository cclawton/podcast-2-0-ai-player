package com.podcast.app.di

import com.podcast.app.data.remote.api.PodcastIndexCredentialsProvider
import com.podcast.app.data.remote.api.SecureCredentialsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialsModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsProvider(
        impl: SecureCredentialsProvider
    ): PodcastIndexCredentialsProvider
}
