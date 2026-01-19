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
    const val SEARCH_RESULT_ITEM = "search_result_item"
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

    // Subscription Confirmation Dialog
    const val SUBSCRIBE_CONFIRMATION_DIALOG = "subscribe_confirmation_dialog"
    const val SUBSCRIBE_CONFIRMATION_TITLE = "subscribe_confirmation_title"
    const val SUBSCRIBE_CONFIRMATION_IMAGE = "subscribe_confirmation_image"
    const val SUBSCRIBE_CONFIRMATION_NAME = "subscribe_confirmation_name"
    const val SUBSCRIBE_CONFIRMATION_DESCRIPTION = "subscribe_confirmation_description"
    const val SUBSCRIBE_CONFIRM_BUTTON = "subscribe_confirm_button"
    const val SUBSCRIBE_CANCEL_BUTTON = "subscribe_cancel_button"

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

    // Downloads Screen
    const val DOWNLOAD_MANAGER_ITEM = "download_manager_item"
    const val DOWNLOADS_SCREEN = "downloads_screen"
    const val DOWNLOADS_LIST = "downloads_list"
    const val DOWNLOAD_ITEM = "download_item"
    const val DOWNLOAD_PROGRESS = "download_progress"
    const val DOWNLOAD_CANCEL = "download_cancel"
    const val DOWNLOAD_DELETE = "download_delete"
    const val DOWNLOAD_RETRY = "download_retry"
    const val CLEAR_DOWNLOADS_BUTTON = "clear_downloads_button"

    // Episode Info Bottom Sheet
    const val EPISODE_INFO_BUTTON = "episode_info_button"
    const val EPISODE_INFO_SHEET = "episode_info_sheet"
    const val EPISODE_INFO_TITLE = "episode_info_title"
    const val EPISODE_INFO_DATE = "episode_info_date"
    const val EPISODE_INFO_DURATION = "episode_info_duration"
    const val EPISODE_INFO_EPISODE_NUMBER = "episode_info_episode_number"
    const val EPISODE_INFO_EXPLICIT = "episode_info_explicit"
    const val EPISODE_INFO_LINK = "episode_info_link"
    const val EPISODE_INFO_TRANSCRIPT = "episode_info_transcript"
    const val EPISODE_INFO_CHAPTERS_HEADER = "episode_info_chapters_header"
    const val EPISODE_INFO_CHAPTER_ITEM = "episode_info_chapter_item"
    const val EPISODE_INFO_SHOW_NOTES_HEADER = "episode_info_show_notes_header"
    const val EPISODE_INFO_DESCRIPTION = "episode_info_description"

    // General
    const val LOADING_INDICATOR = "loading_indicator"
    const val ERROR_MESSAGE = "error_message"
    const val RETRY_BUTTON = "retry_button"
    const val CONFIRMATION_DIALOG = "confirmation_dialog"
    const val CONFIRM_BUTTON = "confirm_button"
    const val CANCEL_BUTTON = "cancel_button"

    // Auto-download (GH#23)
    const val AUTO_DOWNLOAD_TOGGLE = "auto_download_toggle"

    // Storage/Auto-delete settings (GH#24)
    const val AUTO_DELETE_TOGGLE = "auto_delete_toggle"
    const val RETENTION_PERIOD_SELECTOR = "retention_period_selector"
    const val DELETE_ONLY_PLAYED_TOGGLE = "delete_only_played_toggle"

    // Claude API settings (GH#26)
    const val CLAUDE_API_KEY_INPUT = "claude_api_key_input"
    const val CLAUDE_API_KEY_VISIBILITY = "claude_api_key_visibility"
    const val TEST_CLAUDE_CONNECTION = "test_claude_connection"
    const val CLAUDE_CONNECTION_STATUS = "claude_connection_status"

    // LLM Test (GH#31)
    const val LLM_TEST_BUTTON = "test_llm_button"
    const val LLM_TEST_RESULTS = "llm_test_results"

    // Claude API Save (GH#34)
    const val CLAUDE_API_SAVE_BUTTON = "claude_api_save_button"
    const val CLAUDE_API_SAVED_INDICATOR = "claude_api_saved_indicator"
    const val CLAUDE_API_CHANGE_BUTTON = "claude_api_change_button"

    // Onboarding (GH#27)
    const val ONBOARDING_SCREEN = "onboarding_screen"
    const val ENABLE_MICROPHONE_BUTTON = "enable_microphone_button"
    const val SKIP_ONBOARDING_BUTTON = "skip_onboarding_button"

    // Podcast 2.0 features (GH#10)
    const val CHAPTER_LIST = "chapter_list"
    const val CHAPTER_ITEM = "chapter_item"
    const val TRANSCRIPT_VIEW = "transcript_view"
    const val VALUE_INFO_CARD = "value_info_card"
    const val PLAYER_TAB_INFO = "player_tab_info"
    const val PLAYER_TAB_CHAPTERS = "player_tab_chapters"
    const val PLAYER_TAB_TRANSCRIPT = "player_tab_transcript"

    // AI Search (GH#30)
    const val AI_SEARCH_BUTTON = "ai_search_button"
    const val AI_SEARCH_INPUT = "ai_search_input"
    const val AI_SEARCH_SUBMIT = "ai_search_submit"
    const val AI_SEARCH_LOADING = "ai_search_loading"
    const val AI_SEARCH_RESULTS = "ai_search_results"
    const val AI_SEARCH_ERROR = "ai_search_error"
    const val AI_SEARCH_CONFIGURE_API = "ai_search_configure_api"

    // AI Search Enhanced (GH#35)
    const val AI_SEARCH_NL_RESPONSE = "ai_search_nl_response"
    const val AI_SEARCH_EPISODES = "ai_search_episodes"
    const val AI_SEARCH_PODCASTS = "ai_search_podcasts"
    const val AI_SEARCH_CLEAR = "ai_search_clear"

    // Podcast Feed Screen (GH#32)
    const val PODCAST_FEED_SCREEN = "podcast_feed_screen"
    const val PODCAST_FEED_TITLE = "podcast_feed_title"
    const val PODCAST_FEED_DESCRIPTION = "podcast_feed_description"
    const val PODCAST_FEED_EPISODES = "podcast_feed_episodes"
    const val PODCAST_FEED_SUBSCRIBE_BUTTON = "podcast_feed_subscribe_button"
    const val PODCAST_FEED_EPISODE_ITEM = "podcast_feed_episode_item"
    const val PODCAST_FEED_DOWNLOAD_BUTTON = "podcast_feed_download_button"
}
