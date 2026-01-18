package com.podcast.app.privacy

import kotlinx.serialization.Serializable

/**
 * Privacy settings for GrapheneOS optimization.
 *
 * Design principles:
 * - Offline-first: App must work without network
 * - Minimal permissions: Only request what's needed
 * - User control: Granular toggles for all network features
 * - Transparency: Clear disclosure of data usage
 */
@Serializable
data class PrivacySettings(
    /**
     * Master network toggle. When disabled, NO network calls are made.
     * App operates in fully offline mode.
     */
    val networkEnabled: Boolean = true,

    /**
     * Allow network for podcast search/discovery.
     * When disabled, only local library is available.
     */
    val allowPodcastSearch: Boolean = true,

    /**
     * Allow network for episode feed updates.
     * When disabled, only cached episodes are shown.
     */
    val allowFeedUpdates: Boolean = true,

    /**
     * Allow streaming audio over network.
     * When disabled, only downloaded episodes can play.
     */
    val allowAudioStreaming: Boolean = true,

    /**
     * Allow image loading over network.
     * When disabled, only cached images are shown.
     */
    val allowImageLoading: Boolean = true,

    /**
     * Allow background sync operations.
     * When disabled, sync only happens when app is open.
     */
    val allowBackgroundSync: Boolean = false,

    /**
     * Allow Ollama (Termux) local LLM integration.
     * Tier 2 LLM - runs locally on device.
     */
    val allowOllamaIntegration: Boolean = false,

    /**
     * Allow Claude API for advanced features.
     * Tier 3 LLM - requires explicit opt-in.
     */
    val allowClaudeApi: Boolean = false,

    /**
     * Allow chapter image fetching.
     */
    val allowChapterImages: Boolean = true,

    /**
     * Allow transcript fetching.
     */
    val allowTranscripts: Boolean = true,

    /**
     * Auto-download episodes on Wi-Fi only.
     * Respects user's data preferences.
     */
    val autoDownloadOnWifiOnly: Boolean = true,

    /**
     * Maximum auto-download size in bytes (0 = disabled).
     * Default: 100MB
     */
    val maxAutoDownloadSize: Long = 100 * 1024 * 1024,

    /**
     * Keep downloaded episodes for N days (0 = forever).
     */
    val downloadRetentionDays: Int = 30,

    /**
     * Enable automatic deletion of old episodes.
     */
    val autoDeleteEnabled: Boolean = false,

    /**
     * Only delete episodes that have been played (>90% progress).
     */
    val autoDeleteOnlyPlayed: Boolean = true,

    /**
     * Store search history locally.
     */
    val storeSearchHistory: Boolean = true,

    /**
     * Store playback history locally.
     */
    val storePlaybackHistory: Boolean = true
) {
    companion object {
        /**
         * Maximum privacy mode - no network at all.
         * For users who want complete offline operation.
         */
        val MAXIMUM_PRIVACY = PrivacySettings(
            networkEnabled = false,
            allowPodcastSearch = false,
            allowFeedUpdates = false,
            allowAudioStreaming = false,
            allowImageLoading = false,
            allowBackgroundSync = false,
            allowOllamaIntegration = false,
            allowClaudeApi = false,
            allowChapterImages = false,
            allowTranscripts = false,
            autoDownloadOnWifiOnly = true,
            maxAutoDownloadSize = 0,
            autoDeleteEnabled = false,
            autoDeleteOnlyPlayed = true,
            storeSearchHistory = false,
            storePlaybackHistory = true
        )

        /**
         * Balanced mode - network for core features only.
         */
        val BALANCED = PrivacySettings(
            networkEnabled = true,
            allowPodcastSearch = true,
            allowFeedUpdates = true,
            allowAudioStreaming = true,
            allowImageLoading = true,
            allowBackgroundSync = false,
            allowOllamaIntegration = false,
            allowClaudeApi = false,
            allowChapterImages = true,
            allowTranscripts = true,
            autoDownloadOnWifiOnly = true,
            maxAutoDownloadSize = 100 * 1024 * 1024,
            autoDeleteEnabled = false,
            autoDeleteOnlyPlayed = true,
            storeSearchHistory = true,
            storePlaybackHistory = true
        )

        /**
         * Default settings - reasonable defaults for most users.
         */
        val DEFAULT = BALANCED
    }

    /**
     * Check if any network operation is allowed.
     */
    fun isAnyNetworkAllowed(): Boolean {
        return networkEnabled && (
            allowPodcastSearch ||
            allowFeedUpdates ||
            allowAudioStreaming ||
            allowImageLoading ||
            allowBackgroundSync
        )
    }

    /**
     * Check if LLM features are enabled.
     */
    fun isLlmEnabled(): Boolean {
        return allowOllamaIntegration || allowClaudeApi
    }
}

/**
 * Permission requirement with rationale.
 */
data class PermissionRequirement(
    val permission: String,
    val isRequired: Boolean,
    val rationale: String,
    val features: List<String>
)

/**
 * Feature that requires network access.
 */
enum class NetworkFeature(val displayName: String, val description: String) {
    PODCAST_SEARCH("Podcast Search", "Search and discover new podcasts"),
    FEED_UPDATES("Feed Updates", "Get new episodes from subscribed podcasts"),
    AUDIO_STREAMING("Audio Streaming", "Stream episodes without downloading"),
    IMAGE_LOADING("Image Loading", "Load podcast artwork and episode images"),
    BACKGROUND_SYNC("Background Sync", "Update feeds while app is closed"),
    TRANSCRIPT_FETCH("Transcripts", "Download episode transcripts"),
    CHAPTER_IMAGES("Chapter Images", "Load chapter artwork"),
    OLLAMA_LLM("Local AI (Ollama)", "Use local Ollama for voice commands"),
    CLAUDE_API("Claude API", "Use cloud AI for recommendations")
}
