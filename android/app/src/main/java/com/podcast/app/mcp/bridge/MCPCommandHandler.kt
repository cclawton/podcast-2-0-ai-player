package com.podcast.app.mcp.bridge

import com.podcast.app.mcp.models.MCPRequest
import com.podcast.app.mcp.models.MCPResponse

/**
 * Interface for handling MCP commands.
 *
 * Implementations should validate inputs, execute actions,
 * and return appropriate responses.
 */
interface MCPCommandHandler {
    /**
     * Handle an MCP request and return a response.
     */
    suspend fun handleCommand(request: MCPRequest): MCPResponse

    /**
     * Get the list of supported actions.
     */
    fun getSupportedActions(): List<String>
}

/**
 * Supported MCP actions.
 */
object MCPActions {
    // Playback control
    const val PLAY_EPISODE = "playEpisode"
    const val PAUSE = "pausePlayback"
    const val RESUME = "resumePlayback"
    const val SKIP_FORWARD = "skipForward"
    const val SKIP_BACKWARD = "skipBackward"
    const val SET_SPEED = "setPlaybackSpeed"
    const val SEEK_TO = "seekTo"

    // Status
    const val GET_PLAYBACK_STATUS = "getPlaybackStatus"
    const val GET_QUEUE = "getPlaybackQueue"

    // Library
    const val SEARCH_PODCASTS = "searchPodcasts"
    const val ADD_PODCAST = "addPodcast"
    const val REMOVE_PODCAST = "removePodcast"
    const val GET_SUBSCRIBED = "getSubscribedPodcasts"
    const val GET_EPISODES = "getEpisodes"

    // Episode
    const val GET_NEXT_UNPLAYED = "getNextUnplayedEpisode"
    const val MARK_AS_PLAYED = "markAsPlayed"
    const val MARK_AS_UNPLAYED = "markAsUnplayed"
    const val GET_TRANSCRIPT = "getTranscript"
    const val GET_CHAPTERS = "getChapters"

    val ALL_ACTIONS = listOf(
        PLAY_EPISODE, PAUSE, RESUME, SKIP_FORWARD, SKIP_BACKWARD,
        SET_SPEED, SEEK_TO, GET_PLAYBACK_STATUS, GET_QUEUE,
        SEARCH_PODCASTS, ADD_PODCAST, REMOVE_PODCAST, GET_SUBSCRIBED,
        GET_EPISODES, GET_NEXT_UNPLAYED, MARK_AS_PLAYED, MARK_AS_UNPLAYED,
        GET_TRANSCRIPT, GET_CHAPTERS
    )
}
