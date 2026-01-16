package com.podcast.app.mcp.models

import kotlinx.serialization.Serializable

@Serializable
data class MCPRequest(
    val id: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val authToken: String? = null
)

@Serializable
data class MCPResponse(
    val id: String,
    val status: MCPStatus,
    val action: String,
    val data: Map<String, String> = emptyMap(),
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MCPStatus {
    SUCCESS,
    ERROR,
    INVALID_REQUEST,
    UNAUTHORIZED,
    NOT_FOUND,
    INTERNAL_ERROR
}

@Serializable
data class PlaybackStatusResponse(
    val isPlaying: Boolean,
    val currentEpisodeId: Long?,
    val currentEpisodeTitle: String?,
    val podcastTitle: String?,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val playbackSpeed: Float
)

@Serializable
data class EpisodeInfo(
    val id: Long,
    val title: String,
    val podcastTitle: String?,
    val duration: Int?,
    val publishedAt: Long?
)

@Serializable
data class PodcastInfo(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    val episodeCount: Int
)
