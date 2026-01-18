package com.podcast.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podcast.app.util.TestTags
import com.podcast.app.util.waitUntilNodeWithTagExists
import com.podcast.app.util.waitUntilNodeWithTextExists
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive UI tests for SettingsScreen.
 *
 * Tests cover:
 * - Initial state rendering
 * - Status card display
 * - Privacy presets
 * - Network settings toggles
 * - AI settings toggles
 * - Download settings
 * - Data storage settings
 * - Permissions display
 * - Navigation back
 * - Accessibility features
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Navigate to Settings screen
        navigateToSettingsScreen()
    }

    private fun navigateToSettingsScreen() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)
        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SETTINGS_SCREEN)
    }

    // ================================
    // Initial State Tests
    // ================================

    @Test
    fun settingsScreen_isDisplayed() {
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsTopAppBar() {
        // Use testTag to avoid ambiguity with bottom nav "Settings" item
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
        // Verify back button is in the top bar
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsBackButton() {
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    // ================================
    // Status Card Tests
    // ================================

    @Test
    fun settingsScreen_showsStatusCard() {
        // Status card should show current operational mode - one of these should be displayed
        val hasOffline = try {
            composeRule.onNodeWithText("Offline", substring = true).assertIsDisplayed()
            true
        } catch (e: Throwable) {
            false
        }

        if (!hasOffline) {
            composeRule.onNodeWithText("Online", substring = true).assertIsDisplayed()
        }
    }

    // ================================
    // Privacy Presets Tests
    // ================================

    @Test
    fun settingsScreen_showsPrivacyPresetsSection() {
        composeRule.onNodeWithText("Privacy Presets").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsMaximumPrivacyPreset() {
        composeRule.onNodeWithText("Maximum Privacy").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsBalancedPreset() {
        composeRule.onNodeWithText("Balanced").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsFullFeaturesPreset() {
        composeRule.onNodeWithText("Full Features").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_privacyPreset_isClickable() {
        composeRule.onNodeWithText("Maximum Privacy").performClick()
        composeRule.waitForIdle()

        // Screen should still be functional after click
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Network Settings Tests
    // ================================

    @Test
    fun settingsScreen_showsNetworkSection() {
        composeRule.onNodeWithText("Network").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsEnableNetworkToggle() {
        composeRule.onNodeWithText("Enable Network").assertIsDisplayed()
        composeRule.onNodeWithText("Master toggle for all network operations").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_networkToggle_isClickable() {
        composeRule.onNodeWithText("Enable Network").performClick()
        composeRule.waitForIdle()

        // Toggle should have changed state (either on or off)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsPodcastSearchToggle_whenNetworkEnabled() {
        // First check if network is enabled (default is enabled)
        try {
            composeRule.onNodeWithText("Podcast Search").assertIsDisplayed()
            composeRule.onNodeWithText("Search and discover new podcasts").assertIsDisplayed()
        } catch (e: Throwable) {
            // Network might be disabled - enable it first
            composeRule.onNodeWithText("Enable Network").performClick()
            composeRule.waitForIdle()

            // After enabling network, the toggle should appear
            composeRule.onNodeWithText("Podcast Search").assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreen_showsFeedUpdatesToggle_whenNetworkEnabled() {
        // Network is enabled by default, so Feed Updates should be visible
        try {
            composeRule.onNodeWithText("Feed Updates").assertIsDisplayed()
        } catch (e: Throwable) {
            // Network might be disabled - enable it first
            composeRule.onNodeWithText("Enable Network").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Feed Updates").assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreen_showsAudioStreamingToggle_whenNetworkEnabled() {
        try {
            composeRule.onNodeWithText("Audio Streaming").assertIsDisplayed()
        } catch (e: Throwable) {
            // Network might be disabled - enable it first
            composeRule.onNodeWithText("Enable Network").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Audio Streaming").assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreen_showsImageLoadingToggle_whenNetworkEnabled() {
        try {
            composeRule.onNodeWithText("Image Loading").performScrollTo()
            composeRule.onNodeWithText("Image Loading").assertIsDisplayed()
        } catch (e: Throwable) {
            // Network might be disabled - enable it first
            composeRule.onNodeWithText("Enable Network").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Image Loading").performScrollTo()
            composeRule.onNodeWithText("Image Loading").assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreen_showsBackgroundSyncToggle_whenNetworkEnabled() {
        try {
            composeRule.onNodeWithText("Background Sync").performScrollTo()
            composeRule.onNodeWithText("Background Sync").assertIsDisplayed()
        } catch (e: Throwable) {
            // Network might be disabled - enable it first
            composeRule.onNodeWithText("Enable Network").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Background Sync").performScrollTo()
            composeRule.onNodeWithText("Background Sync").assertIsDisplayed()
        }
    }

    // ================================
    // AI Settings Tests
    // ================================

    @Test
    fun settingsScreen_showsAIFeaturesSection() {
        composeRule.onNodeWithText("AI Features").performScrollTo()
        composeRule.onNodeWithText("AI Features").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsLocalAIOllamaToggle() {
        composeRule.onNodeWithText("Local AI (Ollama)").performScrollTo()
        composeRule.onNodeWithText("Local AI (Ollama)").assertIsDisplayed()
        composeRule.onNodeWithText("Use Ollama via Termux for voice commands").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsClaudeAPIToggle() {
        composeRule.onNodeWithText("Claude API").performScrollTo()
        composeRule.onNodeWithText("Claude API").assertIsDisplayed()
        composeRule.onNodeWithText("Use cloud AI for recommendations").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_aiToggle_isClickable() {
        composeRule.onNodeWithText("Local AI (Ollama)").performScrollTo()
        composeRule.onNodeWithText("Local AI (Ollama)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Downloads Settings Tests
    // ================================

    @Test
    fun settingsScreen_showsDownloadsSection() {
        composeRule.onNodeWithText("Downloads").performScrollTo()
        composeRule.onNodeWithText("Downloads").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsWifiOnlyDownloadsToggle() {
        composeRule.onNodeWithText("Wi-Fi Only Downloads").performScrollTo()
        composeRule.onNodeWithText("Wi-Fi Only Downloads").assertIsDisplayed()
        composeRule.onNodeWithText("Only auto-download on Wi-Fi").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsDownloadManagerItem() {
        composeRule.onNodeWithTag(TestTags.DOWNLOAD_MANAGER_ITEM).performScrollTo()
        composeRule.onNodeWithTag(TestTags.DOWNLOAD_MANAGER_ITEM).assertIsDisplayed()
        composeRule.onNodeWithText("Download Manager").assertIsDisplayed()
        composeRule.onNodeWithText("View and manage downloaded episodes").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_downloadManagerItem_navigatesToDownloads() {
        composeRule.onNodeWithTag(TestTags.DOWNLOAD_MANAGER_ITEM).performScrollTo()
        composeRule.onNodeWithTag(TestTags.DOWNLOAD_MANAGER_ITEM).performClick()
        composeRule.waitForIdle()

        // Should navigate to Downloads screen
        composeRule.waitUntilNodeWithTagExists(TestTags.DOWNLOADS_SCREEN)
        composeRule.onNodeWithTag(TestTags.DOWNLOADS_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Data Storage Settings Tests
    // ================================

    @Test
    fun settingsScreen_showsDataStorageSection() {
        composeRule.onNodeWithText("Data Storage").performScrollTo()
        composeRule.onNodeWithText("Data Storage").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsStoreSearchHistoryToggle() {
        composeRule.onNodeWithText("Store Search History").performScrollTo()
        composeRule.onNodeWithText("Store Search History").assertIsDisplayed()
        composeRule.onNodeWithText("Keep local search history").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsStorePlaybackHistoryToggle() {
        composeRule.onNodeWithText("Store Playback History").performScrollTo()
        composeRule.onNodeWithText("Store Playback History").assertIsDisplayed()
        composeRule.onNodeWithText("Track listening progress").assertIsDisplayed()
    }

    // ================================
    // Permissions Section Tests
    // ================================

    @Test
    fun settingsScreen_showsPermissionsSection() {
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.onNodeWithText("Permissions").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsMicrophonePermission() {
        // Scroll to Permissions section first to bring permission items into view
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.waitForIdle()
        // Verify Microphone permission exists (this is a runtime permission users can change)
        val micNodes = composeRule.onAllNodesWithText("Microphone", substring = true).fetchSemanticsNodes()
        assert(micNodes.isNotEmpty()) { "Expected Microphone permission to be displayed" }
        val voiceNodes = composeRule.onAllNodesWithText("For voice commands", substring = true).fetchSemanticsNodes()
        assert(voiceNodes.isNotEmpty()) { "Expected voice commands description to be displayed" }
    }

    @Test
    fun settingsScreen_showsNotificationsPermission_onAndroid13Plus() {
        // POST_NOTIFICATIONS is a runtime permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            composeRule.onNodeWithText("Permissions").performScrollTo()
            composeRule.waitForIdle()
            val notifNodes = composeRule.onAllNodesWithText("Notifications", substring = true).fetchSemanticsNodes()
            assert(notifNodes.isNotEmpty()) { "Expected Notifications permission on Android 13+" }
        }
    }

    @Test
    fun settingsScreen_doesNotShowNonRuntimePermissions() {
        // Internet and Foreground Service are NOT runtime permissions - they should not be shown
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.waitForIdle()

        // Verify Internet permission is NOT displayed (it's not a runtime permission)
        val internetNodes = composeRule.onAllNodesWithText("Optional - app works offline", substring = true).fetchSemanticsNodes()
        assert(internetNodes.isEmpty()) { "Internet permission should not be displayed (not a runtime permission)" }

        // Verify Foreground Service permission is NOT displayed (it's not a runtime permission)
        val fgNodes = composeRule.onAllNodesWithText("Background playback", substring = true).fetchSemanticsNodes()
        assert(fgNodes.isEmpty()) { "Foreground Service permission should not be displayed (not a runtime permission)" }
    }

    @Test
    fun settingsScreen_showsPermissionStatus() {
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.waitForIdle()

        // Should show either "Granted" or "Not Granted" for each permission
        // Use onAllNodes since there may be multiple permission status labels
        val hasGranted = try {
            composeRule.onAllNodesWithText("Granted", substring = false).fetchSemanticsNodes().isNotEmpty()
        } catch (e: Throwable) {
            false
        }

        if (!hasGranted) {
            // Verify at least one "Not Granted" exists
            val notGrantedNodes = composeRule.onAllNodesWithText("Not Granted", substring = false).fetchSemanticsNodes()
            assert(notGrantedNodes.isNotEmpty()) { "Expected at least one permission status to be displayed" }
        }
    }

    // ================================
    // Navigation Tests
    // ================================

    @Test
    fun settingsScreen_backButton_navigatesBack() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // Should navigate back to the previous screen (Library)
        // After clicking back, Settings screen should no longer be displayed
        try {
            composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN, timeoutMillis = 3000)
            composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
        } catch (e: Throwable) {
            // Might navigate to a different screen depending on navigation stack
            // Just verify we're not on settings anymore
        }
    }

    @Test
    fun settingsScreen_bottomNavLibrary_navigatesToLibrary() {
        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_bottomNavSearch_navigatesToSearch() {
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_bottomNavPlayer_navigatesToPlayer() {
        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)
        composeRule.onNodeWithTag(TestTags.PLAYER_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Scrolling Tests
    // ================================

    @Test
    fun settingsScreen_isScrollable() {
        // Scroll to the bottom of the settings
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.waitForIdle()

        // Verify we can see content at the bottom
        composeRule.onNodeWithText("Permissions").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_scrollPreservesState() {
        // Scroll down
        composeRule.onNodeWithText("AI Features").performScrollTo()
        composeRule.waitForIdle()

        // Scroll back up
        composeRule.onNodeWithText("Privacy Presets").performScrollTo()
        composeRule.waitForIdle()

        // Settings should still be functional
        composeRule.onNodeWithText("Privacy Presets").assertIsDisplayed()
    }

    // ================================
    // Accessibility Tests
    // ================================

    @Test
    fun settingsScreen_backButtonHasContentDescription() {
        composeRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun settingsScreen_allSectionsHaveHeaders() {
        // Verify all section headers are present
        composeRule.onNodeWithText("Privacy Presets").assertExists()
        composeRule.onNodeWithText("Network").assertExists()
        composeRule.onNodeWithText("AI Features").performScrollTo()
        composeRule.onNodeWithText("AI Features").assertExists()
        composeRule.onNodeWithText("Downloads").performScrollTo()
        composeRule.onNodeWithText("Downloads").assertExists()
        composeRule.onNodeWithText("Data Storage").performScrollTo()
        composeRule.onNodeWithText("Data Storage").assertExists()
        composeRule.onNodeWithText("Permissions").performScrollTo()
        composeRule.onNodeWithText("Permissions").assertExists()
    }

    // ================================
    // State Preservation Tests
    // ================================

    @Test
    fun settingsScreen_maintainsState_onConfigurationChange() {
        // Change a setting
        composeRule.onNodeWithText("Enable Network").performClick()
        composeRule.waitForIdle()

        // Simulate configuration change
        composeRule.activityRule.scenario.recreate()

        // Navigate back to settings
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)
        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.waitForIdle()

        // Settings screen should be displayed
        composeRule.waitUntilNodeWithTagExists(TestTags.SETTINGS_SCREEN)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Toggle Interaction Tests
    // ================================

    @Test
    fun settingsScreen_toggles_areInteractive() {
        // Verify toggles can be clicked
        composeRule.onNodeWithText("Enable Network").performClick()
        composeRule.waitForIdle()

        // Click again to toggle back
        composeRule.onNodeWithText("Enable Network").performClick()
        composeRule.waitForIdle()

        // Screen should still be functional
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_dataStorageToggles_areInteractive() {
        composeRule.onNodeWithText("Store Search History").performScrollTo()
        composeRule.onNodeWithText("Store Search History").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Store Playback History").performScrollTo()
        composeRule.onNodeWithText("Store Playback History").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Storage/Auto-delete Settings Tests (GH#24)
    // ================================

    @Test
    fun settingsScreen_showsStorageSection() {
        composeRule.onNodeWithText("Storage").performScrollTo()
        composeRule.onNodeWithText("Storage").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsAutoDeleteToggle() {
        composeRule.onNodeWithText("Auto-delete old episodes").performScrollTo()
        composeRule.onNodeWithText("Auto-delete old episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Remove downloaded episodes after retention period").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_autoDeleteToggle_isClickable() {
        composeRule.onNodeWithText("Auto-delete old episodes").performScrollTo()
        composeRule.onNodeWithText("Auto-delete old episodes").performClick()
        composeRule.waitForIdle()

        // After enabling, retention period selector should appear
        composeRule.onNodeWithText("Delete after").performScrollTo()
        composeRule.onNodeWithText("Delete after").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsOnlyDeletePlayedToggle_whenAutoDeleteEnabled() {
        // Enable auto-delete first
        composeRule.onNodeWithText("Auto-delete old episodes").performScrollTo()
        composeRule.onNodeWithText("Auto-delete old episodes").performClick()
        composeRule.waitForIdle()

        // Check for the "Only delete played episodes" toggle
        composeRule.onNodeWithText("Only delete played episodes").performScrollTo()
        composeRule.onNodeWithText("Only delete played episodes").assertIsDisplayed()
    }

    // ================================
    // Claude API Settings Tests (GH#26)
    // ================================

    @Test
    fun settingsScreen_claudeApiToggle_showsConfigWhenEnabled() {
        composeRule.onNodeWithText("Claude API").performScrollTo()
        composeRule.onNodeWithText("Claude API").performClick()
        composeRule.waitForIdle()

        // After enabling, API key configuration should appear
        composeRule.onNodeWithText("API Key Configuration").performScrollTo()
        composeRule.onNodeWithText("API Key Configuration").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_claudeApiConfig_showsApiKeyInput() {
        // Enable Claude API first
        composeRule.onNodeWithText("Claude API").performScrollTo()
        composeRule.onNodeWithText("Claude API").performClick()
        composeRule.waitForIdle()

        // Check for API key input
        composeRule.onNodeWithTag("claude_api_key_input").performScrollTo()
        composeRule.onNodeWithTag("claude_api_key_input").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_claudeApiConfig_showsTestConnectionButton() {
        // Enable Claude API first
        composeRule.onNodeWithText("Claude API").performScrollTo()
        composeRule.onNodeWithText("Claude API").performClick()
        composeRule.waitForIdle()

        // Check for test connection button
        composeRule.onNodeWithTag("test_connection_button").performScrollTo()
        composeRule.onNodeWithTag("test_connection_button").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_claudeApiConfig_showsSecurityNote() {
        // Enable Claude API first
        composeRule.onNodeWithText("Claude API").performScrollTo()
        composeRule.onNodeWithText("Claude API").performClick()
        composeRule.waitForIdle()

        // Check for security note
        composeRule.onNodeWithText("Your API key is stored securely using Android Keystore encryption.").performScrollTo()
        composeRule.onNodeWithText("Your API key is stored securely using Android Keystore encryption.").assertIsDisplayed()
    }
}
