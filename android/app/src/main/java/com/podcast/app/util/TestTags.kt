package com.podcast.app.util

/**
 * Test tags for UI testing with Compose.
 *
 * These tags are used in production code with testTag() modifier
 * and referenced in androidTest code for finding nodes.
 */
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

    // RSS Feed Subscription
    const val RSS_FEED_BUTTON = "rss_feed_button"
    const val RSS_DIALOG = "rss_dialog"
    const val RSS_DIALOG_TITLE = "rss_dialog_title"
    const val RSS_URL_INPUT = "rss_url_input"
    const val RSS_SUBSCRIBE_BUTTON = "rss_subscribe_button"
    const val RSS_CANCEL_BUTTON = "rss_cancel_button"

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
