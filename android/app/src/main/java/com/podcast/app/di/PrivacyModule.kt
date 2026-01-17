package com.podcast.app.di

import android.content.Context
import com.podcast.app.privacy.NetworkStateMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module that provides privacy-related dependencies.
 *
 * This module explicitly provides NetworkStateMonitor so it can be replaced
 * in tests with a fake that doesn't register network callbacks (to avoid
 * TooManyRequestsException during instrumented tests).
 */
@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {

    @Provides
    @Singleton
    fun provideNetworkStateMonitor(
        @ApplicationContext context: Context
    ): NetworkStateMonitor {
        return NetworkStateMonitor(context)
    }
}
