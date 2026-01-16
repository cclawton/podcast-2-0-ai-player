package com.podcast.app.mcp.bridge

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.mcp.models.MCPRequest
import com.podcast.app.mcp.models.MCPResponse
import com.podcast.app.mcp.models.MCPStatus
import com.podcast.app.playback.PlaybackController
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MCPCommandHandler.
 *
 * Handles all MCP commands with proper input validation
 * and error handling.
 */
@Singleton
class MCPCommandHandlerImpl @Inject constructor(
    private val playbackController: PlaybackController,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playbackProgressDao: PlaybackProgressDao,
    private val podcastIndexApi: PodcastIndexApi,
    private val inputValidator: InputValidator
) : MCPCommandHandler {

    override suspend fun handleCommand(request: MCPRequest): MCPResponse {
        return try {
            when (request.action) {
                MCPActions.PLAY_EPISODE -> handlePlayEpisode(request)
                MCPActions.PAUSE -> handlePause(request)
                MCPActions.RESUME -> handleResume(request)
                MCPActions.SKIP_FORWARD -> handleSkipForward(request)
                MCPActions.SKIP_BACKWARD -> handleSkipBackward(request)
                MCPActions.SET_SPEED -> handleSetSpeed(request)
                MCPActions.GET_PLAYBACK_STATUS -> handleGetPlaybackStatus(request)
                MCPActions.SEARCH_PODCASTS -> handleSearchPodcasts(request)
                MCPActions.GET_SUBSCRIBED -> handleGetSubscribed(request)
                MCPActions.ADD_PODCAST -> handleAddPodcast(request)
                MCPActions.REMOVE_PODCAST -> handleRemovePodcast(request)
                MCPActions.GET_NEXT_UNPLAYED -> handleGetNextUnplayed(request)
                MCPActions.MARK_AS_PLAYED -> handleMarkAsPlayed(request)
                MCPActions.GET_TRANSCRIPT -> handleGetTranscript(request)
                else -> MCPResponse(
                    id = request.id,
                    status = MCPStatus.INVALID_REQUEST,
                    action = request.action,
                    error = "Unknown action: ${request.action}"
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                status = MCPStatus.INTERNAL_ERROR,
                action = request.action,
                error = e.message ?: "Unknown error"
            )
        }
    }

    override fun getSupportedActions(): List<String> = MCPActions.ALL_ACTIONS

    private suspend fun handlePlayEpisode(request: MCPRequest): MCPResponse {
        val episodeIdStr = request.params["episodeId"]
        val startPosition = request.params["startPosition"]?.toIntOrNull() ?: 0

        val validation = inputValidator.validateId(episodeIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val episodeId = episodeIdStr!!.toLong()
        playbackController.playEpisode(episodeId, startPosition)

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("episodeId" to episodeId.toString())
        )
    }

    private fun handlePause(request: MCPRequest): MCPResponse {
        playbackController.pause()
        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action
        )
    }

    private fun handleResume(request: MCPRequest): MCPResponse {
        playbackController.resume()
        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action
        )
    }

    private fun handleSkipForward(request: MCPRequest): MCPResponse {
        val seconds = request.params["seconds"]?.toIntOrNull() ?: 15
        val validation = inputValidator.validateSkipSeconds(seconds)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        playbackController.skipForward(seconds)
        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("skippedSeconds" to seconds.toString())
        )
    }

    private fun handleSkipBackward(request: MCPRequest): MCPResponse {
        val seconds = request.params["seconds"]?.toIntOrNull() ?: 15
        val validation = inputValidator.validateSkipSeconds(seconds)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        playbackController.skipBackward(seconds)
        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("skippedSeconds" to seconds.toString())
        )
    }

    private fun handleSetSpeed(request: MCPRequest): MCPResponse {
        val speed = request.params["speed"]?.toFloatOrNull() ?: 1.0f
        val validation = inputValidator.validateSpeed(speed)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        playbackController.setPlaybackSpeed(speed)
        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("speed" to speed.toString())
        )
    }

    private fun handleGetPlaybackStatus(request: MCPRequest): MCPResponse {
        val state = playbackController.getPlaybackStatus()
        val episode = playbackController.currentEpisode.value

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf(
                "isPlaying" to state.isPlaying.toString(),
                "positionSeconds" to state.positionSeconds.toString(),
                "durationSeconds" to state.durationSeconds.toString(),
                "playbackSpeed" to state.playbackSpeed.toString(),
                "currentEpisodeId" to (episode?.id?.toString() ?: ""),
                "currentEpisodeTitle" to (episode?.title ?: "")
            )
        )
    }

    private suspend fun handleSearchPodcasts(request: MCPRequest): MCPResponse {
        val query = request.params["query"]
        val validation = inputValidator.validateSearchQuery(query)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val sanitizedQuery = inputValidator.sanitize(query!!)
        val limit = request.params["limit"]?.toIntOrNull() ?: 10

        return try {
            val response = podcastIndexApi.searchByTerm(sanitizedQuery, limit)
            val results = response.feeds.map { feed ->
                "${feed.id}|${feed.title}|${feed.description?.take(100) ?: ""}"
            }

            MCPResponse(
                id = request.id,
                status = MCPStatus.SUCCESS,
                action = request.action,
                data = mapOf(
                    "count" to response.feeds.size.toString(),
                    "results" to results.joinToString(";;")
                )
            )
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                status = MCPStatus.ERROR,
                action = request.action,
                error = "Search failed: ${e.message}"
            )
        }
    }

    private suspend fun handleGetSubscribed(request: MCPRequest): MCPResponse {
        val podcasts = podcastDao.getSubscribedPodcasts().first()
        val results = podcasts.map { podcast ->
            "${podcast.id}|${podcast.title}|${podcast.episodeCount}"
        }

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf(
                "count" to podcasts.size.toString(),
                "podcasts" to results.joinToString(";;")
            )
        )
    }

    private suspend fun handleAddPodcast(request: MCPRequest): MCPResponse {
        val podcastIdStr = request.params["podcastId"]
        val validation = inputValidator.validateId(podcastIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val podcastId = podcastIdStr!!.toLong()

        // Check if already subscribed
        val existing = podcastDao.getPodcastByIndexId(podcastId)
        if (existing != null) {
            if (existing.isSubscribed) {
                return MCPResponse(
                    id = request.id,
                    status = MCPStatus.SUCCESS,
                    action = request.action,
                    data = mapOf("message" to "Already subscribed")
                )
            }
            // Re-subscribe
            podcastDao.updateSubscription(existing.id, true)
        } else {
            // Fetch from API and add
            val response = podcastIndexApi.getPodcastById(podcastId)
            val feed = response.feed ?: return MCPResponse(
                id = request.id,
                status = MCPStatus.NOT_FOUND,
                action = request.action,
                error = "Podcast not found"
            )

            val podcast = com.podcast.app.data.local.entities.Podcast(
                podcastIndexId = feed.id,
                title = feed.title,
                feedUrl = feed.url,
                imageUrl = feed.artwork ?: feed.image,
                description = feed.description,
                language = feed.language ?: "en",
                explicit = feed.explicit,
                author = feed.author,
                episodeCount = feed.episodeCount
            )
            podcastDao.insertPodcast(podcast)
        }

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("podcastId" to podcastId.toString())
        )
    }

    private suspend fun handleRemovePodcast(request: MCPRequest): MCPResponse {
        val podcastIdStr = request.params["podcastId"]
        val validation = inputValidator.validateId(podcastIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val podcastId = podcastIdStr!!.toLong()
        val podcast = podcastDao.getPodcastById(podcastId)

        if (podcast == null) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.NOT_FOUND,
                action = request.action,
                error = "Podcast not found"
            )
        }

        podcastDao.updateSubscription(podcastId, false)

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("podcastId" to podcastId.toString())
        )
    }

    private suspend fun handleGetNextUnplayed(request: MCPRequest): MCPResponse {
        val podcastIdStr = request.params["podcastId"]
        val validation = inputValidator.validateId(podcastIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val podcastId = podcastIdStr!!.toLong()
        val episode = episodeDao.getNextUnplayedEpisode(podcastId)

        if (episode == null) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.NOT_FOUND,
                action = request.action,
                error = "No unplayed episodes found"
            )
        }

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf(
                "episodeId" to episode.id.toString(),
                "title" to episode.title,
                "duration" to (episode.audioDuration?.toString() ?: "")
            )
        )
    }

    private suspend fun handleMarkAsPlayed(request: MCPRequest): MCPResponse {
        val episodeIdStr = request.params["episodeId"]
        val validation = inputValidator.validateId(episodeIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val episodeId = episodeIdStr!!.toLong()
        playbackProgressDao.markAsCompleted(episodeId)

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf("episodeId" to episodeId.toString())
        )
    }

    private suspend fun handleGetTranscript(request: MCPRequest): MCPResponse {
        val episodeIdStr = request.params["episodeId"]
        val validation = inputValidator.validateId(episodeIdStr)
        if (!validation.isValid()) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.INVALID_REQUEST,
                action = request.action,
                error = validation.getErrorOrNull()
            )
        }

        val episodeId = episodeIdStr!!.toLong()
        val episode = episodeDao.getEpisodeById(episodeId)

        if (episode == null) {
            return MCPResponse(
                id = request.id,
                status = MCPStatus.NOT_FOUND,
                action = request.action,
                error = "Episode not found"
            )
        }

        // Return cached transcript if available
        val transcript = episode.transcriptCached ?: episode.transcriptUrl ?: ""

        return MCPResponse(
            id = request.id,
            status = MCPStatus.SUCCESS,
            action = request.action,
            data = mapOf(
                "episodeId" to episodeId.toString(),
                "transcriptUrl" to (episode.transcriptUrl ?: ""),
                "transcriptCached" to (episode.transcriptCached ?: "")
            )
        )
    }
}
