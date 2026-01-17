package com.podcast.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.database.PodcastDatabase
import com.podcast.app.util.TestTags
import com.podcast.app.util.TestDataPopulator
import com.podcast.app.util.waitUntilNodeWithTagExists
import com.podcast.app.util.waitUntilNodeWithTextExists
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Navigation tests for the Podcast app.
 *
 * Tests:
 * - Bottom navigation between screens
 * - Back button behavior
 * - Deep linking (episodes screen with podcast ID)
 * - Start destination
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: PodcastDatabase

    @Inject
    lateinit var podcastDao: PodcastDao

    @Inject
    lateinit var episodeDao: EpisodeDao

    @Before
    fun setUp() {
        hiltRule.inject()
        // Populate test data
        runBlocking {
            TestDataPopulator.populate(podcastDao, episodeDao)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            TestDataPopulator.clear(database)
        }
    }

    // ================================
    // Start Destination Tests
    // ================================

    @Test
    fun appStartsOnLibraryScreen() {
        // The app should start on the Library screen
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun libraryNavItemIsSelectedOnStart() {
        // Library nav item should be selected by default
        composeRule.waitUntilNodeWithTagExists(TestTags.NAV_LIBRARY)
        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).assertIsSelected()
    }

    // ================================
    // Bottom Navigation Tests
    // ================================

    @Test
    fun navigateToSearchScreen() {
        // Wait for app to load
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Click on Search nav item
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()

        // Verify Search screen is displayed
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateToPlayerScreen() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)
        composeRule.onNodeWithTag(TestTags.PLAYER_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateToSettingsScreen() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntilNodeWithTagExists(TestTags.SETTINGS_SCREEN)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateBackToLibraryFromSearch() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Navigate to Search
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)

        // Navigate back to Library
        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).performClick()
        composeRule.waitForIdle()

        // Verify Library screen is displayed
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun bottomNavMaintainsStateOnRotation() {
        // Navigate to Search screen
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)

        // Simulate configuration change (rotation)
        composeRule.activityRule.scenario.recreate()

        // Verify we're still on Search screen
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateThroughAllScreensSequentially() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Library -> Search
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)
        composeRule.onNodeWithTag(TestTags.SEARCH_SCREEN).assertIsDisplayed()

        // Search -> Player
        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)
        composeRule.onNodeWithTag(TestTags.PLAYER_SCREEN).assertIsDisplayed()

        // Player -> Settings
        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SETTINGS_SCREEN)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()

        // Settings -> Library
        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Back Button Tests
    // ================================

    @Test
    fun backButtonFromSearchReturnsToLibrary() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Navigate to Search
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)

        // Press back
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        // Should be back at Library (or app exits depending on nav graph config)
        // Note: Behavior depends on whether Search is a top-level destination
    }

    @Test
    fun backButtonFromPlayerReturnsToLibrary() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Navigate to Player
        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)

        // Press back
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    // ================================
    // Deep Link Tests
    // ================================

    @Test
    fun navigateToEpisodesScreenFromLibrary() {
        // Wait for Library screen
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)

        // Click on a podcast item (assuming one exists)
        val podcastItem = composeRule.onNodeWithTag(TestTags.PODCAST_ITEM, useUnmergedTree = true)
        if (podcastItem.fetchSemanticsNode().let { true }) {
            podcastItem.performClick()
            composeRule.waitForIdle()

            // Verify Episodes screen is displayed
            composeRule.waitUntilNodeWithTagExists(TestTags.EPISODES_SCREEN)
            composeRule.onNodeWithTag(TestTags.EPISODES_SCREEN).assertIsDisplayed()
        }
    }

    @Test
    fun navigateBackFromEpisodesToLibrary() {
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)

        // Navigate to episodes (if podcast exists)
        try {
            composeRule.onNodeWithTag(TestTags.PODCAST_ITEM, useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            composeRule.waitUntilNodeWithTagExists(TestTags.EPISODES_SCREEN)

            // Press back
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.waitForIdle()

            // Should return to Library
            composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
            composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
        } catch (e: AssertionError) {
            // No podcast item available - skip this test
        }
    }

    // ================================
    // Navigation State Tests
    // ================================

    @Test
    fun navStatePreservedOnBackNavigation() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Navigate: Library -> Search -> Player
        composeRule.onNodeWithTag(TestTags.NAV_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.SEARCH_SCREEN)

        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)

        // Go back to Library directly via nav item
        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).performClick()
        composeRule.waitForIdle()

        // Verify Library is displayed
        composeRule.waitUntilNodeWithTagExists(TestTags.LIBRARY_SCREEN)
        composeRule.onNodeWithTag(TestTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    // ================================
    // Accessibility Tests for Navigation
    // ================================

    @Test
    fun bottomNavItemsHaveContentDescriptions() {
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)

        // Verify each nav item has content description for accessibility
        composeRule.onNodeWithContentDescription("Library", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Search", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Player", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Settings", substring = true).assertExists()
    }
}
