package com.podcast.app.di

import android.content.Context
import com.podcast.app.privacy.NetworkState
import com.podcast.app.privacy.NetworkStateMonitor
import com.podcast.app.privacy.NetworkType
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test privacy module that provides a NetworkStateMonitor that doesn't register
 * network callbacks.
 *
 * This prevents TooManyRequestsException during instrumented tests, which occurs
 * when many test classes each create a new NetworkStateMonitor instance that
 * registers callbacks with ConnectivityManager.
 *
 * The test NetworkStateMonitor reports network as available by default,
 * which is the expected state for most UI tests.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PrivacyModule::class]
)
object TestPrivacyModule {

    @Provides
    @Singleton
    fun provideNetworkStateMonitor(
        @ApplicationContext context: Context
    ): NetworkStateMonitor {
        // Create a test-mode NetworkStateMonitor that doesn't register callbacks
        // and reports network as available (typical test scenario)
        return NetworkStateMonitor(
            context = context,
            skipCallbackRegistration = true,
            initialState = NetworkState(
                isAvailable = true,
                isUnmetered = true,
                type = NetworkType.WIFI
            )
        )
    }
}
