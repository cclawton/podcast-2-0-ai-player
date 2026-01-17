package com.podcast.app.playback

import com.podcast.app.data.local.entities.Episode
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for playback control, enabling test substitution.
 */
interface IPlaybackController {
    val playbackState: StateFlow<PlaybackState>
    val currentEpisode: StateFlow<Episode?>
    val queue: StateFlow<List<Episode>>

    suspend fun playEpisode(episodeId: Long, startPositionMs: Int = 0)
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipForward(seconds: Int = 15)
    fun skipBackward(seconds: Int = 15)
    fun setPlaybackSpeed(speed: Float)
    fun stop()
    fun addToQueue(episode: Episode)
    fun removeFromQueue(episode: Episode)
    fun clearQueue()
    suspend fun playNext()
    fun getPlaybackStatus(): PlaybackState
    fun release()
}
