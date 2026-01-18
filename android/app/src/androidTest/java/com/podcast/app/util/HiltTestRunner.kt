package com.podcast.app.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for Hilt instrumented tests.
 *
 * This runner:
 * - Uses HiltTestApplication for dependency injection in tests
 * - Initializes WorkManager with a test configuration to avoid crashes
 *   when components depend on WorkManager (e.g., DownloadManager)
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

        super.onStart()
    }
}
