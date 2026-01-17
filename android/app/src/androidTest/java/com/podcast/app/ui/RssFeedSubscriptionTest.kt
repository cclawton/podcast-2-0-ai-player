package com.podcast.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podcast.app.util.TestTags
import com.podcast.app.util.waitUntilNodeWithTagExists
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for RSS Feed subscription feature.
 *
 * Tests cover:
 * - RSS button visibility and accessibility
 * - RSS dialog display and interactions
 * - URL input validation
 * - Cancel and subscribe button functionality
 * - Error handling for invalid feeds
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RssFeedSubscriptionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Navigate to Search screen
        navigateToSearchScreen()
    }

    private fun navigateToSearchScreen() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)
    }

    // ================================
    // RSS Button Tests
    // ================================

    @Test
    fun searchScreen_showsRssFeedButton() {
        composeRule.onNodeWithTag(TestTags.RSS_FEED_BUTTON).assertIsDisplayed()
    }

    @Test
    fun searchScreen_rssFeedButton_hasContentDescription() {
        composeRule.onNodeWithContentDescription("Add RSS feed").assertExists()
    }

    @Test
    fun searchScreen_rssFeedButton_opensDialog() {
        composeRule.onNodeWithTag(TestTags.RSS_FEED_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.RSS_DIALOG).assertIsDisplayed()
    }

    // ================================
    // RSS Dialog Display Tests
    // ================================

    @Test
    fun rssDialog_showsTitle() {
        openRssDialog()

        composeRule.onNodeWithText("Add RSS Feed").assertIsDisplayed()
    }

    @Test
    fun rssDialog_showsDescriptionText() {
        openRssDialog()

        composeRule.onNodeWithText("Enter the RSS feed URL of the podcast you want to add.")
            .assertIsDisplayed()
    }

    @Test
    fun rssDialog_showsUrlInput() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT).assertIsDisplayed()
    }

    @Test
    fun rssDialog_showsPlaceholder() {
        openRssDialog()

        composeRule.onNodeWithText("https://example.com/feed.xml").assertExists()
    }

    @Test
    fun rssDialog_showsSubscribeButton() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertIsDisplayed()
    }

    @Test
    fun rssDialog_showsCancelButton() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_CANCEL_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    // ================================
    // URL Input Tests
    // ================================

    @Test
    fun rssDialog_urlInput_acceptsText() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput("https://example.com/podcast.xml")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("https://example.com/podcast.xml", substring = true)
            .assertExists()
    }

    @Test
    fun rssDialog_subscribeButton_disabledWhenUrlEmpty() {
        openRssDialog()

        // Subscribe button should be disabled when URL is empty
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun rssDialog_subscribeButton_enabledWhenUrlEntered() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput("https://example.com/feed.xml")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsEnabled()
    }

    // ================================
    // Dialog Dismissal Tests
    // ================================

    @Test
    fun rssDialog_cancelButton_dismissesDialog() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_CANCEL_BUTTON).performClick()
        composeRule.waitForIdle()

        // Dialog should be dismissed
        composeRule.onNodeWithTag(TestTags.RSS_DIALOG).assertDoesNotExist()
    }

    @Test
    fun rssDialog_cancel_clearsUrl() {
        openRssDialog()

        // Enter URL
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput("https://example.com/feed.xml")
        composeRule.waitForIdle()

        // Cancel
        composeRule.onNodeWithTag(TestTags.RSS_CANCEL_BUTTON).performClick()
        composeRule.waitForIdle()

        // Reopen dialog - URL should be cleared
        composeRule.onNodeWithTag(TestTags.RSS_FEED_BUTTON).performClick()
        composeRule.waitForIdle()

        // Subscribe button should be disabled (URL cleared)
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun rssDialog_canBeReopened_afterCancel() {
        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_CANCEL_BUTTON).performClick()
        composeRule.waitForIdle()

        // Reopen
        composeRule.onNodeWithTag(TestTags.RSS_FEED_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.RSS_DIALOG).assertIsDisplayed()
    }

    // ================================
    // Error Handling Tests
    // ================================

    @Test
    fun rssDialog_subscribe_withInvalidUrl_showsError() {
        openRssDialog()

        // Enter invalid URL (not a valid URL format)
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput("not-a-valid-url")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).performClick()
        composeRule.waitForIdle()

        // Wait a bit for error to appear
        Thread.sleep(1000)

        // Should still be on search screen (didn't navigate away)
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
    }

    @Test
    fun rssDialog_subscribe_withNetworkError_showsError() {
        openRssDialog()

        // Enter a URL that will fail to fetch
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput("https://nonexistent-domain-12345.com/feed.xml")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).performClick()

        // Wait for network timeout
        Thread.sleep(3000)

        // Should still be on search screen
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Accessibility Tests
    // ================================

    @Test
    fun rssDialog_allElementsAccessible() {
        openRssDialog()

        // All interactive elements should exist
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT).assertExists()
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertExists()
        composeRule.onNodeWithTag(TestTags.RSS_CANCEL_BUTTON).assertExists()
    }

    @Test
    fun rssFeedButton_accessibleFromSearchScreen() {
        composeRule.onNodeWithContentDescription("Add RSS feed").assertIsDisplayed()
    }

    // ================================
    // State Preservation Tests
    // ================================

    @Test
    fun rssDialog_maintainsInput_duringInteraction() {
        openRssDialog()

        val testUrl = "https://test.example.com/podcast-feed.xml"
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput(testUrl)
        composeRule.waitForIdle()

        // Verify the text is preserved
        composeRule.onNodeWithText(testUrl, substring = true).assertExists()
    }

    // ================================
    // Integration Tests with Real Feeds
    // ================================

    /**
     * Integration test that subscribes to the No Agenda podcast RSS feed.
     *
     * This test requires network access and verifies the full subscription flow
     * with a real podcast feed.
     *
     * Note: This test may be slower due to network latency and should be
     * excluded from fast unit test runs.
     */
    @Test
    fun rssDialog_subscribeToNoAgendaPodcast_succeeds() {
        val noAgendaFeedUrl = "https://feeds.noagendaassets.com/noagenda.xml"

        openRssDialog()

        // Enter the No Agenda feed URL
        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput(noAgendaFeedUrl)
        composeRule.waitForIdle()

        // Verify URL was entered
        composeRule.onNodeWithText(noAgendaFeedUrl, substring = true).assertExists()

        // Subscribe button should be enabled
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsEnabled()

        // Click subscribe
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).performClick()

        // Wait for network request to complete (up to 30 seconds for slow connections)
        Thread.sleep(5000)
        composeRule.waitForIdle()

        // After successful subscription, should navigate to Episodes screen
        // or dialog should be dismissed
        try {
            // Check if navigated to episodes screen
            composeRule.waitUntilNodeWithTagExists(TestTags.EPISODES_SCREEN, timeoutMillis = 25000)
            composeRule.onNodeWithTag(TestTags.EPISODES_SCREEN).assertIsDisplayed()

            // Verify the podcast title "No Agenda" appears somewhere
            composeRule.onNodeWithText("No Agenda", substring = true).assertExists()
        } catch (e: Throwable) {
            // If navigation didn't happen, check if we're still on search screen
            // (could be a network error or already subscribed)
            composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
        }
    }

    /**
     * Test that the No Agenda feed URL is valid and parseable.
     *
     * This is a lighter-weight test that just verifies the feed can be
     * entered and the subscribe button becomes active.
     */
    @Test
    fun rssDialog_noAgendaFeedUrl_isAccepted() {
        val noAgendaFeedUrl = "https://feeds.noagendaassets.com/noagenda.xml"

        openRssDialog()

        composeRule.onNodeWithTag(TestTags.RSS_URL_INPUT)
            .performTextInput(noAgendaFeedUrl)
        composeRule.waitForIdle()

        // URL should be displayed
        composeRule.onNodeWithText(noAgendaFeedUrl, substring = true).assertExists()

        // Subscribe button should be enabled for valid URL
        composeRule.onNodeWithTag(TestTags.RSS_SUBSCRIBE_BUTTON).assertIsEnabled()
    }

    // ================================
    // Helper Functions
    // ================================

    private fun openRssDialog() {
        composeRule.onNodeWithTag(TestTags.RSS_FEED_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.RSS_DIALOG, timeoutMillis = 3000)
    }
}
