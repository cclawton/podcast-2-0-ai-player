package com.podcast.app.util

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.podcast.app.privacy.privacyDataStore
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking

/**
 * Custom test runner for Hilt instrumented tests.
 *
 * This runner:
 * - Uses HiltTestApplication for dependency injection in tests
 * - Initializes WorkManager with a test configuration to avoid crashes
 *   when components depend on WorkManager (e.g., DownloadManager)
 * - Completes onboarding so tests start on the Library screen
 *
 * Add to build.gradle.kts:
 * defaultConfig {
 *     testInstrumentationRunner = "com.podcast.app.util.HiltTestRunner"
 * }
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    override fun onStart() {
        val context = targetContext.applicationContext

        // Initialize WorkManager for testing before any tests run
        // This is required because:
        // 1. WorkManagerInitializer is disabled in AndroidManifest.xml
        // 2. HiltTestApplication doesn't implement Configuration.Provider
        // 3. DownloadManager calls WorkManager.getInstance() at construction time
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Complete onboarding so all UI tests start on the Library screen
        // instead of being stuck on the Onboarding screen.
        // Uses the SAME DataStore delegate from PrivacyRepository (internal visibility)
        // to avoid "multiple DataStores active for the same file" errors.
        runBlocking {
            context.privacyDataStore.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_completed")] = true
            }
        }

        super.onStart()
    }
}
