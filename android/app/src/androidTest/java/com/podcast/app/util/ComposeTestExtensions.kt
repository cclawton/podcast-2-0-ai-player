package com.podcast.app.util

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

/**
 * Extension functions for Compose UI testing.
 * Provides cleaner syntax and common test operations.
 */

// ================================
// Waiting Extensions
// ================================

/**
 * Wait until a node with the given test tag exists.
 */
fun ComposeTestRule.waitUntilNodeWithTagExists(
    tag: String,
    timeoutMillis: Long = 5000
) {
    this.waitUntil(timeoutMillis) {
        this.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Wait until a node with the given text exists.
 */
fun ComposeTestRule.waitUntilNodeWithTextExists(
    text: String,
    timeoutMillis: Long = 5000
) {
    this.waitUntil(timeoutMillis) {
        this.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Wait until a node with the given content description exists.
 */
fun ComposeTestRule.waitUntilNodeWithContentDescriptionExists(
    description: String,
    timeoutMillis: Long = 5000
) {
    this.waitUntil(timeoutMillis) {
        this.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Wait until no nodes with the given tag exist.
 */
fun ComposeTestRule.waitUntilNodeWithTagDoesNotExist(
    tag: String,
    timeoutMillis: Long = 5000
) {
    this.waitUntil(timeoutMillis) {
        this.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
}

// ================================
// Node Finders
// ================================

/**
 * Find a node with the given test tag, scrolling to it if necessary.
 */
fun ComposeTestRule.onNodeWithTagScrollable(tag: String): SemanticsNodeInteraction {
    return onNodeWithTag(tag).performScrollTo()
}

/**
 * Find a node with the given text, scrolling to it if necessary.
 */
fun ComposeTestRule.onNodeWithTextScrollable(text: String): SemanticsNodeInteraction {
    return onNodeWithText(text).performScrollTo()
}

// ================================
// Action Extensions
// ================================

/**
 * Type text into a text field, clearing it first.
 */
fun SemanticsNodeInteraction.typeText(text: String): SemanticsNodeInteraction {
    performTextClearance()
    performTextInput(text)
    return this
}

/**
 * Click on a node and wait for the main thread to be idle.
 */
fun SemanticsNodeInteraction.clickAndWait(rule: ComposeTestRule): SemanticsNodeInteraction {
    performClick()
    rule.waitForIdle()
    return this
}

// ================================
// Assertion Extensions
// ================================

/**
 * Assert that a node is displayed and enabled.
 */
fun SemanticsNodeInteraction.assertIsDisplayedAndEnabled(): SemanticsNodeInteraction {
    assertIsDisplayed()
    assertIsEnabled()
    return this
}

/**
 * Assert that a node is displayed but disabled.
 */
fun SemanticsNodeInteraction.assertIsDisplayedAndDisabled(): SemanticsNodeInteraction {
    assertIsDisplayed()
    assertIsNotEnabled()
    return this
}

/**
 * Assert that exactly n nodes exist.
 */
fun SemanticsNodeInteractionCollection.assertCountEquals(count: Int): SemanticsNodeInteractionCollection {
    val nodes = fetchSemanticsNodes()
    if (nodes.size != count) {
        throw AssertionError("Expected $count nodes but found ${nodes.size}")
    }
    return this
}

/**
 * Assert that at least n nodes exist.
 */
fun SemanticsNodeInteractionCollection.assertCountAtLeast(count: Int): SemanticsNodeInteractionCollection {
    val nodes = fetchSemanticsNodes()
    if (nodes.size < count) {
        throw AssertionError("Expected at least $count nodes but found ${nodes.size}")
    }
    return this
}

// ================================
// Custom Semantic Matchers
// ================================

/**
 * Matcher for nodes that contain text.
 */
fun hasTextContaining(substring: String, ignoreCase: Boolean = false): SemanticsMatcher {
    return SemanticsMatcher("text contains '$substring'") { node ->
        if (node.config.contains(SemanticsProperties.Text)) {
            node.config[SemanticsProperties.Text].any { it.text.contains(substring, ignoreCase) }
        } else {
            false
        }
    }
}

/**
 * Matcher for nodes with a specific progress value.
 */
fun hasProgressValue(value: Float): SemanticsMatcher {
    return SemanticsMatcher("progress value is $value") { node ->
        node.config.contains(SemanticsProperties.ProgressBarRangeInfo) &&
            node.config[SemanticsProperties.ProgressBarRangeInfo].current == value
    }
}

/**
 * Matcher for nodes that are toggleable and checked.
 */
fun isToggleableAndChecked(): SemanticsMatcher {
    return SemanticsMatcher("toggleable and checked") { node ->
        node.config.contains(SemanticsProperties.ToggleableState) &&
            node.config[SemanticsProperties.ToggleableState].toString() == "On"
    }
}

/**
 * Matcher for nodes that are toggleable and unchecked.
 */
fun isToggleableAndUnchecked(): SemanticsMatcher {
    return SemanticsMatcher("toggleable and unchecked") { node ->
        node.config.contains(SemanticsProperties.ToggleableState) &&
            node.config[SemanticsProperties.ToggleableState].toString() == "Off"
    }
}

/**
 * Matcher for enabled state.
 */
fun isDisabled(): SemanticsMatcher {
    return SemanticsMatcher("is disabled") { node ->
        node.config.contains(SemanticsProperties.Disabled)
    }
}

// ================================
// Composite Matchers
// ================================

/**
 * Match a list item containing specific text.
 */
fun isListItemWithText(text: String): SemanticsMatcher {
    return hasAnyChild(hasText(text))
}

/**
 * Match a clickable item with specific content description.
 */
fun isClickableWithDescription(description: String): SemanticsMatcher {
    return hasContentDescription(description)
}

// ================================
// Test Tags Constants
// ================================

object TestTags {
    // Navigation
    const val BOTTOM_NAV = "bottom_nav"
    const val NAV_LIBRARY = "nav_library"
    const val NAV_SEARCH = "nav_search"
    const val NAV_PLAYER = "nav_player"
    const val NAV_SETTINGS = "nav_settings"

    // Library Screen
    const val LIBRARY_SCREEN = "library_screen"
    const val LIBRARY_LIST = "library_list"
    const val LIBRARY_EMPTY = "library_empty"
    const val PODCAST_ITEM = "podcast_item"
    const val PODCAST_TITLE = "podcast_title"
    const val PODCAST_IMAGE = "podcast_image"
    const val UNSUBSCRIBE_BUTTON = "unsubscribe_button"

    // Search Screen
    const val SEARCH_SCREEN = "search_screen"
    const val SEARCH_INPUT = "search_input"
    const val SEARCH_BUTTON = "search_button"
    const val SEARCH_RESULTS = "search_results"
    const val SEARCH_EMPTY = "search_empty"
    const val SEARCH_ERROR = "search_error"
    const val SEARCH_LOADING = "search_loading"
    const val SUBSCRIBE_BUTTON = "subscribe_button"

    // Player Screen
    const val PLAYER_SCREEN = "player_screen"
    const val PLAY_PAUSE_BUTTON = "play_pause_button"
    const val SKIP_FORWARD_BUTTON = "skip_forward_button"
    const val SKIP_BACKWARD_BUTTON = "skip_backward_button"
    const val PROGRESS_BAR = "progress_bar"
    const val SPEED_CONTROL = "speed_control"
    const val EPISODE_TITLE = "episode_title"
    const val EPISODE_INFO = "episode_info"
    const val CURRENT_TIME = "current_time"
    const val TOTAL_TIME = "total_time"

    // Episodes Screen
    const val EPISODES_SCREEN = "episodes_screen"
    const val EPISODES_LIST = "episodes_list"
    const val EPISODE_ITEM = "episode_item"

    // Settings Screen
    const val SETTINGS_SCREEN = "settings_screen"
    const val API_KEY_INPUT = "api_key_input"
    const val API_SECRET_INPUT = "api_secret_input"
    const val SAVE_API_BUTTON = "save_api_button"
    const val LLM_TIER_SELECTOR = "llm_tier_selector"
    const val PRIVACY_TOGGLE = "privacy_toggle"
    const val OFFLINE_MODE_TOGGLE = "offline_mode_toggle"

    // General
    const val LOADING_INDICATOR = "loading_indicator"
    const val ERROR_MESSAGE = "error_message"
    const val RETRY_BUTTON = "retry_button"
    const val CONFIRMATION_DIALOG = "confirmation_dialog"
    const val CONFIRM_BUTTON = "confirm_button"
    const val CANCEL_BUTTON = "cancel_button"
}
