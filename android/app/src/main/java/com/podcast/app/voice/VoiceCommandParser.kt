package com.podcast.app.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast-path voice command parser for offline operation.
 *
 * This parser enables Tier 1 voice control without requiring an LLM.
 * It uses regex patterns to recognize common podcast commands with
 * high accuracy, falling back to LLM tiers only for complex queries.
 *
 * Design principles:
 * - Works completely offline
 * - High accuracy for common commands (>95% recognition)
 * - Clear confidence scoring for ambiguous commands
 * - Graceful fallback for unrecognized commands
 */
@Singleton
class VoiceCommandParser @Inject constructor() {

    companion object {
        // Time unit multipliers
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 3600

        // Confidence thresholds
        private const val HIGH_CONFIDENCE = 0.95f
        private const val MEDIUM_CONFIDENCE = 0.75f
        private const val LOW_CONFIDENCE = 0.6f

        // Number word mappings
        private val NUMBER_WORDS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
            "thirty" to 30, "forty" to 40, "forty-five" to 45, "fifty" to 50,
            "sixty" to 60, "ninety" to 90, "hundred" to 100,
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10
        )

        // Speed value mappings
        private val SPEED_WORDS = mapOf(
            "half" to 0.5f, "normal" to 1.0f, "one" to 1.0f,
            "one point five" to 1.5f, "one and a half" to 1.5f,
            "double" to 2.0f, "two" to 2.0f, "twice" to 2.0f,
            "two and a half" to 2.5f, "triple" to 3.0f, "three" to 3.0f
        )
    }

    // ==========================================================================
    // Main Parse Entry Point
    // ==========================================================================

    /**
     * Parse a voice command text and return the corresponding intent.
     *
     * @param text The raw voice command text
     * @return CommandIntent representing the parsed command or UnrecognizedIntent
     */
    fun parse(text: String): CommandIntent {
        val normalized = normalizeText(text)

        // Try each parser in order of priority
        return tryParsePlayback(normalized, text)
            ?: tryParseNavigation(normalized, text)
            ?: tryParseSpeed(normalized, text)
            ?: tryParseSearch(normalized, text)
            ?: tryParseLibrary(normalized, text)
            ?: tryParseStatus(normalized, text)
            ?: tryParseQueue(normalized, text)
            ?: UnrecognizedIntent(text, "No matching command pattern found")
    }

    /**
     * Normalize text for pattern matching.
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .trim()
            .replace(Regex("[.,!?;:]"), "")
            .replace(Regex("\\s+"), " ")
    }

    // ==========================================================================
    // Playback Intent Parsing
    // ==========================================================================

    // Play patterns
    private val playPodcastPattern = Regex(
        "^(?:please\\s+)?(?:play|start|listen to|put on)\\s+(?:the\\s+)?(?:podcast\\s+)?(.+?)(?:\\s+podcast)?$"
    )
    private val playEpisodeByNamePattern = Regex(
        "^(?:please\\s+)?(?:play|start|listen to)\\s+(?:the\\s+)?(?:episode\\s+)?(?:called\\s+|titled\\s+|named\\s+)?[\"']?(.+?)[\"']?(?:\\s+from\\s+(.+))?$"
    )
    private val playEpisodeByNumberPattern = Regex(
        "^(?:please\\s+)?(?:play|start|listen to)\\s+(?:episode\\s+)?(?:number\\s+)?([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})(?:\\s+(?:of|from)\\s+(.+))?$"
    )
    private val playLatestPattern = Regex(
        "^(?:please\\s+)?(?:play|start|listen to)\\s+(?:the\\s+)?(?:latest|newest|most recent|new)(?:\\s+episode)?(?:\\s+(?:of|from)\\s+(.+))?$"
    )
    private val resumePlayPattern = Regex(
        "^(?:please\\s+)?(?:play|resume|continue|start)(?:\\s+playback)?(?:\\s+again)?$"
    )

    // Pause patterns
    private val pausePattern = Regex(
        "^(?:please\\s+)?(?:pause|hold|wait|stop playing)(?:\\s+(?:playback|it|that|this))?$"
    )

    // Resume patterns
    private val resumePattern = Regex(
        "^(?:please\\s+)?(?:resume|continue|unpause|go on|keep playing)(?:\\s+(?:playback|playing|it|that))?$"
    )

    // Stop patterns
    private val stopPattern = Regex(
        "^(?:please\\s+)?(?:stop|end|quit|exit|terminate|halt)(?:\\s+(?:playback|playing|it|that|the podcast|everything))?$"
    )

    private fun tryParsePlayback(normalized: String, raw: String): PlaybackIntent? {
        // Check resume/play without target first
        resumePlayPattern.find(normalized)?.let {
            return PlayIntent(
                target = PlayTarget.ResumeLast,
                confidence = HIGH_CONFIDENCE,
                rawText = raw
            )
        }

        // Check pause
        pausePattern.find(normalized)?.let {
            return PauseIntent(confidence = HIGH_CONFIDENCE, rawText = raw)
        }

        // Check resume
        resumePattern.find(normalized)?.let {
            return ResumeIntent(confidence = HIGH_CONFIDENCE, rawText = raw)
        }

        // Check stop
        stopPattern.find(normalized)?.let {
            return StopIntent(confidence = HIGH_CONFIDENCE, rawText = raw)
        }

        // Check play latest
        playLatestPattern.find(normalized)?.let { match ->
            val podcastName = match.groupValues[1].takeIf { it.isNotBlank() }
            return PlayIntent(
                target = PlayTarget.Latest(podcastName),
                confidence = HIGH_CONFIDENCE,
                rawText = raw
            )
        }

        // Check play episode by number
        playEpisodeByNumberPattern.find(normalized)?.let { match ->
            val numberStr = match.groupValues[1]
            val number = parseNumber(numberStr) ?: return null
            val podcastName = match.groupValues[2].takeIf { it.isNotBlank() }
            return PlayIntent(
                target = PlayTarget.EpisodeByNumber(number, podcastName),
                confidence = HIGH_CONFIDENCE,
                rawText = raw
            )
        }

        // Check play episode by name (requires "episode" keyword for disambiguation)
        if (normalized.contains("episode")) {
            playEpisodeByNamePattern.find(normalized)?.let { match ->
                val episodeName = match.groupValues[1].trim()
                val podcastName = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (episodeName.isNotBlank()) {
                    return PlayIntent(
                        target = PlayTarget.EpisodeByName(episodeName, podcastName),
                        confidence = MEDIUM_CONFIDENCE, // Lower confidence for name matching
                        rawText = raw
                    )
                }
            }
        }

        // Check play podcast
        playPodcastPattern.find(normalized)?.let { match ->
            val podcastName = match.groupValues[1].trim()
            if (podcastName.isNotBlank() && !isPauseStopKeyword(podcastName)) {
                return PlayIntent(
                    target = PlayTarget.Podcast(podcastName),
                    confidence = MEDIUM_CONFIDENCE, // Lower confidence for name matching
                    rawText = raw
                )
            }
        }

        return null
    }

    private fun isPauseStopKeyword(text: String): Boolean {
        return text in listOf("pause", "stop", "it", "that", "this")
    }

    // ==========================================================================
    // Navigation Intent Parsing
    // ==========================================================================

    // Skip forward patterns
    private val skipForwardPattern = Regex(
        "^(?:please\\s+)?(?:skip|jump|go|fast)?\\s*forward\\s*(?:by\\s+)?([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})?\\s*(seconds?|minutes?|mins?)?$"
    )
    private val skipAheadPattern = Regex(
        "^(?:please\\s+)?skip\\s+(?:ahead\\s+)?(?:by\\s+)?([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})?\\s*(seconds?|minutes?|mins?)?$"
    )

    // Skip backward patterns
    private val skipBackwardPattern = Regex(
        "^(?:please\\s+)?(?:skip|jump|go|rewind)?\\s*(?:back(?:ward)?|behind)\\s*(?:by\\s+)?([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})?\\s*(seconds?|minutes?|mins?)?$"
    )
    private val rewindPattern = Regex(
        "^(?:please\\s+)?rewind\\s*(?:by\\s+)?([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})?\\s*(seconds?|minutes?|mins?)?$"
    )

    // Seek patterns
    private val seekToPattern = Regex(
        "^(?:please\\s+)?(?:seek|go|jump|skip)\\s+to\\s+([\\d:]+|${NUMBER_WORDS.keys.joinToString("|")})(?:\\s*(seconds?|minutes?|mins?|hours?))?$"
    )

    // Next/previous patterns
    private val nextEpisodePattern = Regex(
        "^(?:please\\s+)?(?:next|skip to next|play next|go to next)(?:\\s+episode)?$"
    )
    private val previousEpisodePattern = Regex(
        "^(?:please\\s+)?(?:previous|last|prior|go back|back to)(?:\\s+episode)?$"
    )

    // Chapter navigation
    private val nextChapterPattern = Regex(
        "^(?:please\\s+)?(?:next|skip to next)\\s+chapter$"
    )
    private val previousChapterPattern = Regex(
        "^(?:please\\s+)?(?:previous|last|prior)\\s+chapter$"
    )
    private val jumpToChapterPattern = Regex(
        "^(?:please\\s+)?(?:go to|jump to|skip to)\\s+chapter\\s+([\\d]+|${NUMBER_WORDS.keys.joinToString("|")})$"
    )

    private fun tryParseNavigation(normalized: String, raw: String): NavigationIntent? {
        // Skip forward
        skipForwardPattern.find(normalized)?.let { match ->
            val seconds = parseTimeValue(match.groupValues[1], match.groupValues[2])
            return SkipForwardIntent(seconds, HIGH_CONFIDENCE, raw)
        }
        skipAheadPattern.find(normalized)?.let { match ->
            val seconds = parseTimeValue(match.groupValues[1], match.groupValues[2])
            return SkipForwardIntent(seconds, HIGH_CONFIDENCE, raw)
        }

        // Skip backward
        skipBackwardPattern.find(normalized)?.let { match ->
            val seconds = parseTimeValue(match.groupValues[1], match.groupValues[2])
            return SkipBackwardIntent(seconds, HIGH_CONFIDENCE, raw)
        }
        rewindPattern.find(normalized)?.let { match ->
            val seconds = parseTimeValue(match.groupValues[1], match.groupValues[2])
            return SkipBackwardIntent(seconds, HIGH_CONFIDENCE, raw)
        }

        // Seek to position
        seekToPattern.find(normalized)?.let { match ->
            val positionStr = match.groupValues[1]
            val unit = match.groupValues[2]
            val seconds = parseSeekPosition(positionStr, unit)
            if (seconds != null) {
                return SeekToIntent(seconds, HIGH_CONFIDENCE, raw)
            }
        }

        // Next episode
        nextEpisodePattern.find(normalized)?.let {
            return NextEpisodeIntent(HIGH_CONFIDENCE, raw)
        }

        // Previous episode
        previousEpisodePattern.find(normalized)?.let {
            return PreviousEpisodeIntent(HIGH_CONFIDENCE, raw)
        }

        // Chapter navigation
        nextChapterPattern.find(normalized)?.let {
            return JumpToChapterIntent(chapterNumber = null, chapterName = "next", HIGH_CONFIDENCE, raw)
        }
        previousChapterPattern.find(normalized)?.let {
            return JumpToChapterIntent(chapterNumber = null, chapterName = "previous", HIGH_CONFIDENCE, raw)
        }
        jumpToChapterPattern.find(normalized)?.let { match ->
            val chapterNum = parseNumber(match.groupValues[1])
            return JumpToChapterIntent(chapterNumber = chapterNum, chapterName = null, confidence = HIGH_CONFIDENCE, rawText = raw)
        }

        return null
    }

    // ==========================================================================
    // Speed Intent Parsing
    // ==========================================================================

    // Set speed patterns
    private val setSpeedPattern = Regex(
        "^(?:please\\s+)?(?:set\\s+)?(?:playback\\s+)?speed\\s+(?:to\\s+)?([\\d.]+|${SPEED_WORDS.keys.joinToString("|")})(?:\\s*x)?$"
    )
    private val speedAtPattern = Regex(
        "^(?:please\\s+)?(?:play\\s+)?(?:at\\s+)?([\\d.]+|${SPEED_WORDS.keys.joinToString("|")})(?:\\s*x)?\\s*(?:speed)?$"
    )

    // Speed up/down patterns
    private val speedUpPattern = Regex(
        "^(?:please\\s+)?(?:speed\\s+up|faster|increase\\s+speed|go\\s+faster)$"
    )
    private val slowDownPattern = Regex(
        "^(?:please\\s+)?(?:slow\\s+down|slower|decrease\\s+speed|go\\s+slower)$"
    )
    private val normalSpeedPattern = Regex(
        "^(?:please\\s+)?(?:normal\\s+speed|reset\\s+speed|regular\\s+speed|one\\s*x\\s*speed)$"
    )

    private fun tryParseSpeed(normalized: String, raw: String): SpeedIntent? {
        // Set specific speed
        setSpeedPattern.find(normalized)?.let { match ->
            val speed = parseSpeed(match.groupValues[1])
            if (speed != null) {
                return SetSpeedIntent(speed, HIGH_CONFIDENCE, raw)
            }
        }
        speedAtPattern.find(normalized)?.let { match ->
            val speed = parseSpeed(match.groupValues[1])
            if (speed != null) {
                return SetSpeedIntent(speed, HIGH_CONFIDENCE, raw)
            }
        }

        // Speed up
        speedUpPattern.find(normalized)?.let {
            return SpeedUpIntent(HIGH_CONFIDENCE, raw)
        }

        // Slow down
        slowDownPattern.find(normalized)?.let {
            return SlowDownIntent(HIGH_CONFIDENCE, raw)
        }

        // Normal speed
        normalSpeedPattern.find(normalized)?.let {
            return NormalSpeedIntent(HIGH_CONFIDENCE, raw)
        }

        return null
    }

    // ==========================================================================
    // Search Intent Parsing
    // ==========================================================================

    private val searchPattern = Regex(
        "^(?:please\\s+)?(?:search|find|look)\\s+(?:for\\s+)?(.+)$"
    )
    private val searchPodcastsPattern = Regex(
        "^(?:please\\s+)?(?:search|find|look)\\s+(?:for\\s+)?(?:podcast[s]?\\s+(?:about|called|named|for)?\\s+)?(.+)$"
    )

    private fun tryParseSearch(normalized: String, raw: String): SearchIntent? {
        // Check if it's a search command
        if (!normalized.startsWith("search") &&
            !normalized.startsWith("find") &&
            !normalized.startsWith("look for")
        ) {
            return null
        }

        searchPattern.find(normalized)?.let { match ->
            val query = match.groupValues[1].trim()
            if (query.isNotBlank()) {
                val searchType = when {
                    query.contains("podcast") -> SearchType.PODCASTS
                    query.contains("episode") -> SearchType.EPISODES
                    else -> SearchType.ALL
                }
                val cleanQuery = query
                    .replace(Regex("podcasts?\\s+(?:about|called|named|for)?\\s*"), "")
                    .replace(Regex("episodes?\\s+(?:about|called|named|for)?\\s*"), "")
                    .trim()

                return SearchQueryIntent(
                    query = cleanQuery.ifBlank { query },
                    searchType = searchType,
                    confidence = HIGH_CONFIDENCE,
                    rawText = raw
                )
            }
        }

        return null
    }

    // ==========================================================================
    // Library Intent Parsing
    // ==========================================================================

    private val subscribePattern = Regex(
        "^(?:please\\s+)?(?:subscribe\\s+to|follow|add)\\s+(?:the\\s+)?(?:podcast\\s+)?(.+?)(?:\\s+podcast)?$"
    )
    private val unsubscribePattern = Regex(
        "^(?:please\\s+)?(?:unsubscribe\\s+from|unfollow|remove|delete)\\s+(?:the\\s+)?(?:podcast\\s+)?(.+?)(?:\\s+podcast)?$"
    )
    private val markPlayedPattern = Regex(
        "^(?:please\\s+)?mark\\s+(?:(?:this|current|it)\\s+)?(?:episode\\s+)?(?:as\\s+)?played$"
    )
    private val markUnplayedPattern = Regex(
        "^(?:please\\s+)?mark\\s+(?:(?:this|current|it)\\s+)?(?:episode\\s+)?(?:as\\s+)?(?:unplayed|not\\s+played|new)$"
    )
    private val listSubscriptionsPattern = Regex(
        "^(?:please\\s+)?(?:list|show|what are)?\\s*(?:my\\s+)?(?:subscriptions?|subscribed\\s+podcasts?|podcasts?)$"
    )

    private fun tryParseLibrary(normalized: String, raw: String): LibraryIntent? {
        // Subscribe
        subscribePattern.find(normalized)?.let { match ->
            val podcastName = match.groupValues[1].trim()
            if (podcastName.isNotBlank()) {
                return SubscribeIntent(podcastName, MEDIUM_CONFIDENCE, raw)
            }
        }

        // Unsubscribe
        unsubscribePattern.find(normalized)?.let { match ->
            val podcastName = match.groupValues[1].trim()
            if (podcastName.isNotBlank()) {
                return UnsubscribeIntent(podcastName, MEDIUM_CONFIDENCE, raw)
            }
        }

        // Mark as played
        markPlayedPattern.find(normalized)?.let {
            return MarkPlayedIntent(MarkTarget.Current, HIGH_CONFIDENCE, raw)
        }

        // Mark as unplayed
        markUnplayedPattern.find(normalized)?.let {
            return MarkUnplayedIntent(MarkTarget.Current, HIGH_CONFIDENCE, raw)
        }

        // List subscriptions
        listSubscriptionsPattern.find(normalized)?.let {
            return ListSubscriptionsIntent(HIGH_CONFIDENCE, raw)
        }

        return null
    }

    // ==========================================================================
    // Status Intent Parsing
    // ==========================================================================

    private val whatsPlayingPattern = Regex(
        "^(?:what(?:'s|\\s+is)\\s+(?:playing|this|on)|what\\s+(?:podcast|episode)\\s+is\\s+(?:playing|this)|" +
                "what\\s+am\\s+i\\s+listening\\s+to|current\\s+(?:episode|podcast))$"
    )
    private val playbackStatusPattern = Regex(
        "^(?:playback\\s+)?status|(?:what(?:'s|\\s+is)\\s+the\\s+)?(?:current\\s+)?(?:position|time|progress)$"
    )
    private val queueStatusPattern = Regex(
        "^(?:what(?:'s|\\s+is)\\s+(?:in\\s+)?(?:my\\s+)?)?(?:the\\s+)?queue|show\\s+(?:my\\s+)?queue$"
    )

    private fun tryParseStatus(normalized: String, raw: String): StatusIntent? {
        // What's playing
        whatsPlayingPattern.find(normalized)?.let {
            return WhatsPlayingIntent(HIGH_CONFIDENCE, raw)
        }

        // Playback status
        playbackStatusPattern.find(normalized)?.let {
            return PlaybackStatusIntent(HIGH_CONFIDENCE, raw)
        }

        // Queue status
        queueStatusPattern.find(normalized)?.let {
            return QueueStatusIntent(HIGH_CONFIDENCE, raw)
        }

        return null
    }

    // ==========================================================================
    // Queue Intent Parsing
    // ==========================================================================

    private val addToQueuePattern = Regex(
        "^(?:please\\s+)?(?:add|queue|put)\\s+(?:this|it|episode)?\\s*(?:to\\s+(?:the\\s+)?queue|next|up\\s+next)$"
    )
    private val clearQueuePattern = Regex(
        "^(?:please\\s+)?(?:clear|empty|remove\\s+all\\s+from)\\s+(?:the\\s+)?queue$"
    )

    private fun tryParseQueue(normalized: String, raw: String): QueueIntent? {
        // Add to queue
        addToQueuePattern.find(normalized)?.let {
            return AddToQueueIntent(null, HIGH_CONFIDENCE, raw)
        }

        // Clear queue
        clearQueuePattern.find(normalized)?.let {
            return ClearQueueIntent(HIGH_CONFIDENCE, raw)
        }

        return null
    }

    // ==========================================================================
    // Utility Functions
    // ==========================================================================

    /**
     * Parse a number from text (supports both digits and words).
     */
    private fun parseNumber(text: String): Int? {
        val trimmed = text.trim().lowercase()

        // Try parsing as integer first
        trimmed.toIntOrNull()?.let { return it }

        // Try parsing as word
        NUMBER_WORDS[trimmed]?.let { return it }

        return null
    }

    /**
     * Parse a time value with optional unit.
     * Returns seconds. Default is 15 seconds if no value provided.
     */
    private fun parseTimeValue(valueStr: String, unitStr: String): Int {
        val value = if (valueStr.isBlank()) {
            15 // Default skip value
        } else {
            parseNumber(valueStr) ?: 15
        }

        val unit = unitStr.lowercase().trim()
        return when {
            unit.startsWith("min") -> value * SECONDS_PER_MINUTE
            unit.startsWith("hour") -> value * SECONDS_PER_HOUR
            else -> value // Default to seconds
        }
    }

    /**
     * Parse a seek position from various formats.
     * Supports: "1:30", "90", "one thirty", etc.
     */
    private fun parseSeekPosition(positionStr: String, unitStr: String): Int? {
        // Check for timestamp format (e.g., "1:30", "01:30:00")
        if (positionStr.contains(":")) {
            val parts = positionStr.split(":")
            return when (parts.size) {
                2 -> {
                    val minutes = parts[0].toIntOrNull() ?: return null
                    val seconds = parts[1].toIntOrNull() ?: return null
                    minutes * SECONDS_PER_MINUTE + seconds
                }
                3 -> {
                    val hours = parts[0].toIntOrNull() ?: return null
                    val minutes = parts[1].toIntOrNull() ?: return null
                    val seconds = parts[2].toIntOrNull() ?: return null
                    hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
                }
                else -> null
            }
        }

        // Parse as number with optional unit
        val value = parseNumber(positionStr) ?: return null
        val unit = unitStr.lowercase().trim()
        return when {
            unit.startsWith("min") -> value * SECONDS_PER_MINUTE
            unit.startsWith("hour") -> value * SECONDS_PER_HOUR
            else -> value
        }
    }

    /**
     * Parse a playback speed value.
     */
    private fun parseSpeed(speedStr: String): Float? {
        val trimmed = speedStr.trim().lowercase()

        // Try parsing as float
        trimmed.replace("x", "").toFloatOrNull()?.let { speed ->
            // Validate speed range
            return if (speed in 0.25f..4.0f) speed else null
        }

        // Try parsing as word
        SPEED_WORDS[trimmed]?.let { return it }

        return null
    }
}
