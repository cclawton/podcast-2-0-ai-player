package com.podcast.app.util

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.podcast.app.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule

/**
 * Base class for Compose UI tests with Hilt dependency injection.
 *
 * Provides:
 * - HiltAndroidRule for dependency injection
 * - ComposeTestRule for Compose UI testing
 * - Common setup patterns
 */
abstract class BaseComposeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    open fun setUp() {
        hiltRule.inject()
    }

    /**
     * Wait for the UI to be idle.
     */
    protected fun waitForIdle() {
        composeRule.waitForIdle()
    }

    /**
     * Wait for a specific duration.
     */
    protected fun waitFor(timeoutMillis: Long) {
        composeRule.mainClock.advanceTimeBy(timeoutMillis)
    }

    /**
     * Assert screen is displayed by checking for a specific test tag.
     */
    protected fun assertScreenDisplayed(screenTag: String) {
        composeRule.waitUntilNodeWithTagExists(screenTag)
    }
}

/**
 * Base class for Compose tests that don't require the full Activity.
 * Useful for testing individual composables in isolation.
 */
abstract class BaseComposableTest {

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    /**
     * Wait for the UI to be idle.
     */
    protected fun waitForIdle() {
        composeRule.waitForIdle()
    }
}
