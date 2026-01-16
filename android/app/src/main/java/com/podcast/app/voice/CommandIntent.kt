package com.podcast.app.voice

/**
 * Represents a parsed voice command intent.
 *
 * This sealed class hierarchy provides type-safe command representation
 * for offline voice control without requiring an LLM.
 *
 * Each intent carries:
 * - Structured data extracted from the voice command
 * - Confidence score (0.0 to 1.0) indicating parsing certainty
 * - Original raw text for debugging/logging
 */
sealed class CommandIntent(
    open val confidence: Float,
    open val rawText: String
) {
    /**
     * Returns true if this intent was parsed with high confidence.
     * High confidence means the command can be executed without confirmation.
     */
    fun isHighConfidence(): Boolean = confidence >= 0.85f

    /**
     * Returns true if this intent was parsed with sufficient confidence
     * to be executed (potentially with confirmation for medium confidence).
     */
    fun isSufficientConfidence(): Boolean = confidence >= 0.6f
}

// =============================================================================
// Playback Control Intents
// =============================================================================

/**
 * Intent for playback control commands: play, pause, resume, stop.
 */
sealed class PlaybackIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Play a specific podcast or episode.
 */
data class PlayIntent(
    val target: PlayTarget,
    override val confidence: Float,
    override val rawText: String
) : PlaybackIntent(confidence, rawText)

/**
 * What to play - can be a podcast name, episode name/number, or resume current.
 */
sealed class PlayTarget {
    /** Play specific podcast by name */
    data class Podcast(val name: String) : PlayTarget()

    /** Play specific episode by name */
    data class EpisodeByName(val name: String, val podcastName: String? = null) : PlayTarget()

    /** Play specific episode by number */
    data class EpisodeByNumber(val number: Int, val podcastName: String? = null) : PlayTarget()

    /** Resume current or last played */
    data object ResumeLast : PlayTarget()

    /** Play the newest/latest episode */
    data class Latest(val podcastName: String? = null) : PlayTarget()
}

/**
 * Pause playback.
 */
data class PauseIntent(
    override val confidence: Float,
    override val rawText: String
) : PlaybackIntent(confidence, rawText)

/**
 * Resume playback.
 */
data class ResumeIntent(
    override val confidence: Float,
    override val rawText: String
) : PlaybackIntent(confidence, rawText)

/**
 * Stop playback completely.
 */
data class StopIntent(
    override val confidence: Float,
    override val rawText: String
) : PlaybackIntent(confidence, rawText)

// =============================================================================
// Navigation Intents
// =============================================================================

/**
 * Intent for navigation commands: skip, seek, next/previous episode.
 */
sealed class NavigationIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Skip forward by a duration.
 */
data class SkipForwardIntent(
    val seconds: Int,
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

/**
 * Skip backward by a duration.
 */
data class SkipBackwardIntent(
    val seconds: Int,
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

/**
 * Seek to a specific position.
 */
data class SeekToIntent(
    val positionSeconds: Int,
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

/**
 * Go to the next episode.
 */
data class NextEpisodeIntent(
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

/**
 * Go to the previous episode.
 */
data class PreviousEpisodeIntent(
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

/**
 * Jump to a specific chapter.
 */
data class JumpToChapterIntent(
    val chapterNumber: Int? = null,
    val chapterName: String? = null,
    override val confidence: Float,
    override val rawText: String
) : NavigationIntent(confidence, rawText)

// =============================================================================
// Speed Control Intents
// =============================================================================

/**
 * Intent for playback speed commands.
 */
sealed class SpeedIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Set playback speed to a specific value.
 */
data class SetSpeedIntent(
    val speed: Float,
    override val confidence: Float,
    override val rawText: String
) : SpeedIntent(confidence, rawText)

/**
 * Increase playback speed.
 */
data class SpeedUpIntent(
    override val confidence: Float,
    override val rawText: String
) : SpeedIntent(confidence, rawText)

/**
 * Decrease playback speed.
 */
data class SlowDownIntent(
    override val confidence: Float,
    override val rawText: String
) : SpeedIntent(confidence, rawText)

/**
 * Reset speed to normal (1.0x).
 */
data class NormalSpeedIntent(
    override val confidence: Float,
    override val rawText: String
) : SpeedIntent(confidence, rawText)

// =============================================================================
// Search Intents
// =============================================================================

/**
 * Intent for search commands.
 */
sealed class SearchIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Search for podcasts or episodes.
 */
data class SearchQueryIntent(
    val query: String,
    val searchType: SearchType = SearchType.ALL,
    override val confidence: Float,
    override val rawText: String
) : SearchIntent(confidence, rawText)

enum class SearchType {
    ALL,
    PODCASTS,
    EPISODES
}

// =============================================================================
// Library Management Intents
// =============================================================================

/**
 * Intent for library management commands.
 */
sealed class LibraryIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Subscribe to a podcast.
 */
data class SubscribeIntent(
    val podcastName: String,
    override val confidence: Float,
    override val rawText: String
) : LibraryIntent(confidence, rawText)

/**
 * Unsubscribe from a podcast.
 */
data class UnsubscribeIntent(
    val podcastName: String,
    override val confidence: Float,
    override val rawText: String
) : LibraryIntent(confidence, rawText)

/**
 * Mark episode(s) as played.
 */
data class MarkPlayedIntent(
    val target: MarkTarget = MarkTarget.Current,
    override val confidence: Float,
    override val rawText: String
) : LibraryIntent(confidence, rawText)

/**
 * Mark episode(s) as unplayed.
 */
data class MarkUnplayedIntent(
    val target: MarkTarget = MarkTarget.Current,
    override val confidence: Float,
    override val rawText: String
) : LibraryIntent(confidence, rawText)

/**
 * Target for mark played/unplayed commands.
 */
sealed class MarkTarget {
    data object Current : MarkTarget()
    data class Episode(val name: String) : MarkTarget()
    data class AllInPodcast(val podcastName: String) : MarkTarget()
}

/**
 * List subscribed podcasts.
 */
data class ListSubscriptionsIntent(
    override val confidence: Float,
    override val rawText: String
) : LibraryIntent(confidence, rawText)

// =============================================================================
// Status/Query Intents
// =============================================================================

/**
 * Intent for status and information queries.
 */
sealed class StatusIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Ask what's currently playing.
 */
data class WhatsPlayingIntent(
    override val confidence: Float,
    override val rawText: String
) : StatusIntent(confidence, rawText)

/**
 * Get playback status (position, duration, etc.).
 */
data class PlaybackStatusIntent(
    override val confidence: Float,
    override val rawText: String
) : StatusIntent(confidence, rawText)

/**
 * Get queue information.
 */
data class QueueStatusIntent(
    override val confidence: Float,
    override val rawText: String
) : StatusIntent(confidence, rawText)

/**
 * Get information about a podcast or episode.
 */
data class GetInfoIntent(
    val targetName: String? = null,
    override val confidence: Float,
    override val rawText: String
) : StatusIntent(confidence, rawText)

// =============================================================================
// Queue Management Intents
// =============================================================================

/**
 * Intent for queue management commands.
 */
sealed class QueueIntent(
    override val confidence: Float,
    override val rawText: String
) : CommandIntent(confidence, rawText)

/**
 * Add episode to queue.
 */
data class AddToQueueIntent(
    val episodeName: String? = null,
    override val confidence: Float,
    override val rawText: String
) : QueueIntent(confidence, rawText)

/**
 * Clear the playback queue.
 */
data class ClearQueueIntent(
    override val confidence: Float,
    override val rawText: String
) : QueueIntent(confidence, rawText)

// =============================================================================
// Fallback Intents
// =============================================================================

/**
 * Intent when command could not be parsed locally.
 * This indicates the command should be sent to LLM tiers.
 */
data class UnrecognizedIntent(
    override val rawText: String,
    val reason: String = "Could not parse command"
) : CommandIntent(confidence = 0.0f, rawText = rawText)

/**
 * Intent representing an ambiguous command that needs clarification.
 */
data class AmbiguousIntent(
    val possibleIntents: List<CommandIntent>,
    override val rawText: String
) : CommandIntent(confidence = 0.5f, rawText = rawText)
