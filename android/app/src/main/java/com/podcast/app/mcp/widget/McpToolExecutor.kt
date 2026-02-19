package com.podcast.app.mcp.widget

import android.util.Log
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.mcp.bridge.InputValidator
import com.podcast.app.playback.IPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes MCP tool calls from the widget by routing them to the appropriate services.
 *
 * Supported tools:
 * - search_byterm: Search podcasts via Podcast Index API
 * - episodes_byfeedid: Get episodes for a podcast by feed ID
 * - podcasts_byfeedid: Get podcast details by feed ID
 * - get_subscribed: Get locally subscribed podcasts
 * - play_episode: Start playback of an episode
 * - pause_playback: Pause current playback
 * - resume_playback: Resume playback
 *
 * All inputs are validated before execution.
 */
@Singleton
class McpToolExecutor @Inject constructor(
    private val podcastIndexApi: PodcastIndexApi,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playbackController: IPlaybackController,
    private val inputValidator: InputValidator
) {
    companion object {
        private const val TAG = "McpToolExecutor"
        private const val DEFAULT_MAX_RESULTS = 20
        private const val MAX_ALLOWED_RESULTS = 100
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Result of a tool execution.
     */
    sealed class ToolResult {
        data class Podcasts(val items: List<PodcastResultItem>) : ToolResult()
        data class Episodes(val items: List<EpisodeResultItem>) : ToolResult()
        data class Success(val message: String) : ToolResult()
        data class Error(val message: String) : ToolResult()
    }

    /**
     * Execute a tool call.
     *
     * @param toolName The tool name
     * @param argsJson JSON string of arguments
     * @return The tool result
     */
    suspend fun execute(toolName: String, argsJson: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val args = if (argsJson.isNotBlank()) {
                json.decodeFromString<JsonObject>(argsJson)
            } else {
                JsonObject(emptyMap())
            }

            when (toolName) {
                "search_byterm" -> executeSearchByTerm(args)
                "episodes_byfeedid" -> executeGetEpisodesByFeedId(args)
                "podcasts_byfeedid" -> executeGetPodcastByFeedId(args)
                "get_subscribed" -> executeGetSubscribed()
                "play_episode" -> executePlayEpisode(args)
                "pause_playback" -> executePausePlayback()
                "resume_playback" -> executeResumePlayback()
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $toolName", e)
            ToolResult.Error("Tool execution failed: ${e.message}")
        }
    }

    /**
     * Search podcasts by term via Podcast Index API.
     */
    private suspend fun executeSearchByTerm(args: JsonObject): ToolResult {
        val query = args["q"]?.jsonPrimitive?.content
            ?: args["query"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: q or query")

        val validation = inputValidator.validateSearchQuery(query)
        if (!validation.isValid()) {
            return ToolResult.Error(validation.getErrorOrNull() ?: "Invalid query")
        }

        val max = args["max"]?.jsonPrimitive?.intOrNull
            ?.coerceIn(1, MAX_ALLOWED_RESULTS)
            ?: DEFAULT_MAX_RESULTS

        val sanitizedQuery = inputValidator.sanitize(query)

        return try {
            val response = podcastIndexApi.searchByTerm(sanitizedQuery, max)
            val feeds = response.feeds

            val items = feeds.map { feed ->
                PodcastResultItem(
                    id = feed.id,
                    title = feed.title,
                    author = feed.author,
                    artwork = feed.artwork ?: feed.image,
                    episodeCount = feed.episodeCount,
                    language = feed.language
                )
            }

            ToolResult.Podcasts(items)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            ToolResult.Error("Search failed: ${e.message}")
        }
    }

    /**
     * Get episodes by feed ID.
     * First tries local database, then falls back to API.
     */
    private suspend fun executeGetEpisodesByFeedId(args: JsonObject): ToolResult {
        val feedId = args["id"]?.jsonPrimitive?.longOrNull
            ?: args["feedId"]?.jsonPrimitive?.longOrNull
            ?: return ToolResult.Error("Missing required parameter: id")

        val validation = inputValidator.validateId(feedId.toString())
        if (!validation.isValid()) {
            return ToolResult.Error(validation.getErrorOrNull() ?: "Invalid feed ID")
        }

        val max = args["max"]?.jsonPrimitive?.intOrNull
            ?.coerceIn(1, MAX_ALLOWED_RESULTS)
            ?: DEFAULT_MAX_RESULTS

        // Try to get from local database first (by podcastIndexId)
        val localPodcast = podcastDao.getPodcastByIndexId(feedId)
        if (localPodcast != null) {
            val localEpisodes = episodeDao.getEpisodesByPodcast(localPodcast.id, max).first()
            if (localEpisodes.isNotEmpty()) {
                return ToolResult.Episodes(localEpisodes.map { it.toResultItem() })
            }
        }

        // Fall back to API
        return try {
            val response = podcastIndexApi.getEpisodesByFeedId(feedId, max)
            val episodes = response.items

            val items = episodes.map { episode ->
                EpisodeResultItem(
                    id = episode.id,
                    title = episode.title,
                    feedTitle = null, // EpisodeItem doesn't have feedTitle
                    image = episode.image ?: episode.feedImage,
                    datePublished = episode.datePublished ?: 0,
                    duration = episode.duration ?: 0
                )
            }

            ToolResult.Episodes(items)
        } catch (e: Exception) {
            Log.e(TAG, "Get episodes failed", e)
            ToolResult.Error("Failed to get episodes: ${e.message}")
        }
    }

    /**
     * Get podcast details by feed ID.
     */
    private suspend fun executeGetPodcastByFeedId(args: JsonObject): ToolResult {
        val feedId = args["id"]?.jsonPrimitive?.longOrNull
            ?: return ToolResult.Error("Missing required parameter: id")

        val validation = inputValidator.validateId(feedId.toString())
        if (!validation.isValid()) {
            return ToolResult.Error(validation.getErrorOrNull() ?: "Invalid feed ID")
        }

        return try {
            val response = podcastIndexApi.getPodcastById(feedId)
            val feed = response.feed ?: return ToolResult.Error("Podcast not found")

            val item = PodcastResultItem(
                id = feed.id,
                title = feed.title,
                author = feed.author,
                artwork = feed.artwork ?: feed.image,
                episodeCount = feed.episodeCount,
                language = feed.language
            )

            ToolResult.Podcasts(listOf(item))
        } catch (e: Exception) {
            Log.e(TAG, "Get podcast failed", e)
            ToolResult.Error("Failed to get podcast: ${e.message}")
        }
    }

    /**
     * Get locally subscribed podcasts.
     */
    private suspend fun executeGetSubscribed(): ToolResult {
        return try {
            val podcasts = podcastDao.getSubscribedPodcasts().first()

            val items = podcasts.map { podcast ->
                PodcastResultItem(
                    id = podcast.podcastIndexId ?: podcast.id,
                    title = podcast.title,
                    author = podcast.author,
                    artwork = podcast.imageUrl,
                    episodeCount = podcast.episodeCount,
                    language = podcast.language
                )
            }

            ToolResult.Podcasts(items)
        } catch (e: Exception) {
            Log.e(TAG, "Get subscribed failed", e)
            ToolResult.Error("Failed to get subscribed podcasts: ${e.message}")
        }
    }

    /**
     * Play an episode.
     */
    private suspend fun executePlayEpisode(args: JsonObject): ToolResult {
        val episodeId = args["id"]?.jsonPrimitive?.longOrNull
            ?: args["episodeId"]?.jsonPrimitive?.longOrNull
            ?: return ToolResult.Error("Missing required parameter: id or episodeId")

        val validation = inputValidator.validateId(episodeId.toString())
        if (!validation.isValid()) {
            return ToolResult.Error(validation.getErrorOrNull() ?: "Invalid episode ID")
        }

        val startPosition = args["startPosition"]?.jsonPrimitive?.intOrNull ?: 0

        return try {
            playbackController.playEpisode(episodeId, startPosition)
            ToolResult.Success("Playback started for episode $episodeId")
        } catch (e: Exception) {
            Log.e(TAG, "Play episode failed", e)
            ToolResult.Error("Failed to play episode: ${e.message}")
        }
    }

    /**
     * Pause playback.
     */
    private fun executePausePlayback(): ToolResult {
        return try {
            playbackController.pause()
            ToolResult.Success("Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
            ToolResult.Error("Failed to pause: ${e.message}")
        }
    }

    /**
     * Resume playback.
     */
    private fun executeResumePlayback(): ToolResult {
        return try {
            playbackController.resume()
            ToolResult.Success("Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Resume failed", e)
            ToolResult.Error("Failed to resume: ${e.message}")
        }
    }

    /**
     * Subscribe to a podcast by Podcast Index ID.
     */
    suspend fun subscribeToPodcast(podcastIndexId: Long): ToolResult = withContext(Dispatchers.IO) {
        val validation = inputValidator.validateId(podcastIndexId.toString())
        if (!validation.isValid()) {
            return@withContext ToolResult.Error(validation.getErrorOrNull() ?: "Invalid podcast ID")
        }

        try {
            // Check if already subscribed
            val existing = podcastDao.getPodcastByIndexId(podcastIndexId)
            if (existing != null) {
                if (existing.isSubscribed) {
                    return@withContext ToolResult.Success("Already subscribed to ${existing.title}")
                }
                // Re-subscribe
                podcastDao.updateSubscription(existing.id, true)
                return@withContext ToolResult.Success("Subscribed to ${existing.title}")
            }

            // Fetch from API and add
            val response = podcastIndexApi.getPodcastById(podcastIndexId)
            val feed = response.feed
                ?: return@withContext ToolResult.Error("Podcast not found")

            val podcast = Podcast(
                podcastIndexId = feed.id,
                title = feed.title,
                feedUrl = feed.url,
                imageUrl = feed.artwork ?: feed.image,
                description = feed.description,
                language = feed.language ?: "en",
                explicit = feed.explicit,
                author = feed.author,
                episodeCount = feed.episodeCount,
                isSubscribed = true
            )

            podcastDao.insertPodcast(podcast)
            ToolResult.Success("Subscribed to ${feed.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Subscribe failed", e)
            ToolResult.Error("Failed to subscribe: ${e.message}")
        }
    }

    /**
     * Play an episode by ID.
     */
    suspend fun playEpisode(episodeId: Long): ToolResult = withContext(Dispatchers.IO) {
        val validation = inputValidator.validateId(episodeId.toString())
        if (!validation.isValid()) {
            return@withContext ToolResult.Error(validation.getErrorOrNull() ?: "Invalid episode ID")
        }

        try {
            playbackController.playEpisode(episodeId)
            ToolResult.Success("Playing episode $episodeId")
        } catch (e: Exception) {
            Log.e(TAG, "Play failed", e)
            ToolResult.Error("Failed to play: ${e.message}")
        }
    }
}

/**
 * Extension function to convert Episode entity to EpisodeResultItem.
 */
private fun Episode.toResultItem() = EpisodeResultItem(
    id = id,
    title = title,
    feedTitle = null, // Would need to join with podcast table
    image = imageUrl,
    datePublished = publishedAt ?: 0,
    duration = audioDuration ?: 0
)
