package com.podcast.app.api

import com.podcast.app.api.model.Episode
import com.podcast.app.api.model.EpisodesResponse
import com.podcast.app.api.model.PodcastDetail
import com.podcast.app.api.model.PodcastFeed
import com.podcast.app.api.model.PodcastResponse
import com.podcast.app.api.model.RecentEpisodesResponse
import com.podcast.app.api.model.SearchResponse
import com.podcast.app.api.model.SingleEpisodeResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Podcast Index API operations.
 *
 * Provides:
 * - Clean API for UI/domain layer
 * - Input validation for MCP commands (SECURITY REQUIREMENT)
 * - Error handling with [ApiResult]
 * - Caching coordination
 *
 * SECURITY: All public methods validate inputs before making API calls.
 * This is critical for MCP integration where external input comes from
 * voice/text commands.
 */
@Singleton
class PodcastIndexRepository @Inject constructor(
    private val api: PodcastIndexApi,
    private val credentialManager: CredentialManager
) {
    companion object {
        // Validation limits (prevent abuse)
        private const val MAX_QUERY_LENGTH = 500
        private const val MAX_RESULTS_LIMIT = 100
        private const val MIN_PODCAST_ID = 1L
        private const val MIN_EPISODE_ID = 1L
        private const val MAX_URL_LENGTH = 2000
    }

    /**
     * Check if API is configured and ready.
     */
    fun isConfigured(): Boolean = credentialManager.hasCredentials()

    // ========== Search Operations ==========

    /**
     * Search for podcasts by term.
     *
     * @param query Search query (validated)
     * @param maxResults Maximum results (clamped to safe range)
     * @return Search results or error
     */
    suspend fun searchPodcasts(
        query: String,
        maxResults: Int = PodcastIndexApi.DEFAULT_MAX_RESULTS
    ): ApiResult<List<PodcastFeed>> {
        // Validate input
        val sanitizedQuery = validateAndSanitizeQuery(query)
            ?: return ApiResult.Error(message = "Invalid search query")

        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        return safeApiCall { api.searchByTerm(sanitizedQuery, safeMax) }
            .map { it.feeds }
    }

    /**
     * Search for podcasts by person (host, guest).
     *
     * @param person Person name (validated)
     * @param maxResults Maximum results
     * @return Search results or error
     */
    suspend fun searchByPerson(
        person: String,
        maxResults: Int = PodcastIndexApi.DEFAULT_MAX_RESULTS
    ): ApiResult<List<PodcastFeed>> {
        val sanitizedPerson = validateAndSanitizeQuery(person)
            ?: return ApiResult.Error(message = "Invalid person name")

        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        return safeApiCall { api.searchByPerson(sanitizedPerson, safeMax) }
            .map { it.feeds }
    }

    // ========== Podcast Metadata ==========

    /**
     * Get podcast details by ID.
     *
     * @param id Podcast Index feed ID (validated)
     * @return Podcast details or error
     */
    suspend fun getPodcastById(id: Long): ApiResult<PodcastDetail> {
        if (!validatePodcastId(id)) {
            return ApiResult.Error(message = "Invalid podcast ID")
        }

        return safeApiCall { api.getPodcastById(id) }
            .let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val feed = result.data.feed
                        if (feed != null) {
                            ApiResult.Success(feed)
                        } else {
                            ApiResult.Error(message = "Podcast not found")
                        }
                    }
                    is ApiResult.Error -> result
                }
            }
    }

    /**
     * Get podcast details by feed URL.
     *
     * @param feedUrl Feed URL (validated)
     * @return Podcast details or error
     */
    suspend fun getPodcastByFeedUrl(feedUrl: String): ApiResult<PodcastDetail> {
        if (!validateUrl(feedUrl)) {
            return ApiResult.Error(message = "Invalid feed URL")
        }

        return safeApiCall { api.getPodcastByFeedUrl(feedUrl) }
            .let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val feed = result.data.feed
                        if (feed != null) {
                            ApiResult.Success(feed)
                        } else {
                            ApiResult.Error(message = "Podcast not found")
                        }
                    }
                    is ApiResult.Error -> result
                }
            }
    }

    /**
     * Get podcast details by iTunes ID.
     *
     * @param itunesId iTunes/Apple Podcasts ID (validated)
     * @return Podcast details or error
     */
    suspend fun getPodcastByItunesId(itunesId: Long): ApiResult<PodcastDetail> {
        if (!validatePodcastId(itunesId)) {
            return ApiResult.Error(message = "Invalid iTunes ID")
        }

        return safeApiCall { api.getPodcastByItunesId(itunesId) }
            .let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val feed = result.data.feed
                        if (feed != null) {
                            ApiResult.Success(feed)
                        } else {
                            ApiResult.Error(message = "Podcast not found")
                        }
                    }
                    is ApiResult.Error -> result
                }
            }
    }

    // ========== Episode Operations ==========

    /**
     * Get episodes for a podcast.
     *
     * @param feedId Podcast Index feed ID (validated)
     * @param maxResults Maximum episodes to return
     * @param since Only return episodes after this Unix timestamp
     * @return Episodes or error
     */
    suspend fun getEpisodesByFeedId(
        feedId: Long,
        maxResults: Int = PodcastIndexApi.MAX_EPISODES_PER_REQUEST,
        since: Long? = null
    ): ApiResult<List<Episode>> {
        if (!validatePodcastId(feedId)) {
            return ApiResult.Error(message = "Invalid feed ID")
        }

        if (since != null && since < 0) {
            return ApiResult.Error(message = "Invalid timestamp")
        }

        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        return safeApiCall {
            api.getEpisodesByFeedId(feedId, safeMax, since, fulltext = true)
        }.map { it.items }
    }

    /**
     * Get a single episode by ID.
     *
     * @param episodeId Episode ID (validated)
     * @return Episode details or error
     */
    suspend fun getEpisodeById(episodeId: Long): ApiResult<Episode> {
        if (!validateEpisodeId(episodeId)) {
            return ApiResult.Error(message = "Invalid episode ID")
        }

        return safeApiCall { api.getEpisodeById(episodeId, fulltext = true) }
            .let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val episode = result.data.episode
                        if (episode != null) {
                            ApiResult.Success(episode)
                        } else {
                            ApiResult.Error(message = "Episode not found")
                        }
                    }
                    is ApiResult.Error -> result
                }
            }
    }

    /**
     * Get recent episodes across all podcasts.
     *
     * @param maxResults Maximum episodes to return
     * @return Recent episodes or error
     */
    suspend fun getRecentEpisodes(
        maxResults: Int = PodcastIndexApi.DEFAULT_MAX_RESULTS
    ): ApiResult<List<Episode>> {
        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        return safeApiCall { api.getRecentEpisodes(safeMax, fulltext = true) }
            .map { it.items }
    }

    /**
     * Get random episodes for discovery.
     *
     * @param maxResults Maximum episodes to return
     * @param language Optional language filter (e.g., "en")
     * @return Random episodes or error
     */
    suspend fun getRandomEpisodes(
        maxResults: Int = PodcastIndexApi.DEFAULT_MAX_RESULTS,
        language: String? = null
    ): ApiResult<List<Episode>> {
        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        // Validate language if provided
        val safeLang = language?.let {
            if (validateLanguageCode(it)) it else null
        }

        return safeApiCall {
            api.getRandomEpisodes(safeMax, safeLang, fulltext = true)
        }.map { it.items }
    }

    /**
     * Get currently live episodes.
     *
     * @param maxResults Maximum episodes to return
     * @return Live episodes or error
     */
    suspend fun getLiveEpisodes(
        maxResults: Int = PodcastIndexApi.DEFAULT_MAX_RESULTS
    ): ApiResult<List<Episode>> {
        val safeMax = maxResults.coerceIn(1, MAX_RESULTS_LIMIT)

        return safeApiCall { api.getLiveEpisodes(safeMax) }
            .map { it.items }
    }

    // ========== Input Validation (SECURITY CRITICAL) ==========

    /**
     * Validates and sanitizes search query input.
     *
     * SECURITY: This prevents injection attacks and ensures reasonable input.
     *
     * @param query Raw query input (possibly from MCP/voice)
     * @return Sanitized query or null if invalid
     */
    private fun validateAndSanitizeQuery(query: String): String? {
        // Trim whitespace
        val trimmed = query.trim()

        // Check length
        if (trimmed.isEmpty() || trimmed.length > MAX_QUERY_LENGTH) {
            return null
        }

        // Remove potentially dangerous characters
        // Allow: letters, numbers, spaces, common punctuation
        val sanitized = trimmed
            .replace(Regex("[<>\"';&|`\\\\]"), "") // Remove injection chars
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()

        // Final validation
        if (sanitized.isEmpty()) {
            return null
        }

        return sanitized
    }

    /**
     * Validates podcast ID.
     *
     * @param id Podcast ID to validate
     * @return true if valid
     */
    private fun validatePodcastId(id: Long): Boolean {
        return id >= MIN_PODCAST_ID
    }

    /**
     * Validates episode ID.
     *
     * @param id Episode ID to validate
     * @return true if valid
     */
    private fun validateEpisodeId(id: Long): Boolean {
        return id >= MIN_EPISODE_ID
    }

    /**
     * Validates URL format.
     *
     * @param url URL to validate
     * @return true if valid
     */
    private fun validateUrl(url: String): Boolean {
        if (url.isBlank() || url.length > MAX_URL_LENGTH) {
            return false
        }

        // Must start with http:// or https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false
        }

        // Basic URL pattern check
        val urlPattern = Regex("^https?://[\\w.-]+(?:/[\\w./-]*)?$")
        return urlPattern.matches(url)
    }

    /**
     * Validates language code format (ISO 639-1).
     *
     * @param code Language code to validate
     * @return true if valid
     */
    private fun validateLanguageCode(code: String): Boolean {
        // ISO 639-1: 2 lowercase letters
        // Extended: 2-letter code optionally followed by region (e.g., "en-US")
        val langPattern = Regex("^[a-z]{2}(-[A-Z]{2})?$")
        return langPattern.matches(code)
    }

    /**
     * Validates a playback offset/seek position.
     *
     * @param offsetSeconds Offset in seconds
     * @param maxDuration Maximum allowed duration
     * @return true if valid
     */
    fun validatePlaybackOffset(offsetSeconds: Int, maxDuration: Int): Boolean {
        return offsetSeconds in 0..maxDuration
    }

    /**
     * Validates and parses a podcast/episode ID from string (MCP input).
     *
     * @param idString ID string from MCP command
     * @return Parsed ID or null if invalid
     */
    fun parseAndValidateId(idString: String): Long? {
        return try {
            val id = idString.trim().toLong()
            if (id >= MIN_PODCAST_ID) id else null
        } catch (e: NumberFormatException) {
            null
        }
    }
}
