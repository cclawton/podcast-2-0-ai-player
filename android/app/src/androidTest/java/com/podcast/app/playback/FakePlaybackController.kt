package com.podcast.app.playback

import com.podcast.app.data.local.entities.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake PlaybackController for UI tests.
 *
 * Provides a test episode and simulates playback state without
 * requiring real ExoPlayer or database access.
 */
class FakePlaybackController : IPlaybackController {

    private val _playbackState = MutableStateFlow(
        PlaybackState(
            isPlaying = false,
            playerState = PlayerState.READY,
            positionMs = 30_000L, // 30 seconds in
            durationMs = 3_600_000L, // 1 hour duration
            playbackSpeed = 1.0f
        )
    )
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(createTestEpisode())
    override val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    override val queue: StateFlow<List<Episode>> = _queue.asStateFlow()

    override suspend fun playEpisode(episodeId: Long, startPositionMs: Int) {
        _currentEpisode.value = createTestEpisode(id = episodeId)
        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            playerState = PlayerState.READY,
            positionMs = startPositionMs.toLong()
        )
    }

    override fun pause() {
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    override fun resume() {
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
    }

    override fun togglePlayPause() {
        val currentState = _playbackState.value
        _playbackState.value = currentState.copy(isPlaying = !currentState.isPlaying)
    }

    override fun seekTo(positionMs: Long) {
        val duration = _playbackState.value.durationMs
        val clampedPosition = positionMs.coerceIn(0, duration)
        _playbackState.value = _playbackState.value.copy(positionMs = clampedPosition)
    }

    override fun skipForward(seconds: Int) {
        val currentPosition = _playbackState.value.positionMs
        val duration = _playbackState.value.durationMs
        val newPosition = (currentPosition + seconds * 1000).coerceAtMost(duration)
        _playbackState.value = _playbackState.value.copy(positionMs = newPosition)
    }

    override fun skipBackward(seconds: Int) {
        val currentPosition = _playbackState.value.positionMs
        val newPosition = (currentPosition - seconds * 1000).coerceAtLeast(0)
        _playbackState.value = _playbackState.value.copy(positionMs = newPosition)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.5f, 3.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = validSpeed)
    }

    override fun stop() {
        _currentEpisode.value = null
        _playbackState.value = PlaybackState()
    }

    override fun addToQueue(episode: Episode) {
        _queue.value = _queue.value + episode
    }

    override fun removeFromQueue(episode: Episode) {
        _queue.value = _queue.value.filter { it.id != episode.id }
    }

    override fun clearQueue() {
        _queue.value = emptyList()
    }

    override suspend fun playNext() {
        val nextEpisode = _queue.value.firstOrNull()
        if (nextEpisode != null) {
            _queue.value = _queue.value.drop(1)
            playEpisode(nextEpisode.id)
        }
    }

    override fun getPlaybackStatus(): PlaybackState = _playbackState.value

    override fun release() {
        // No-op for test
    }

    /**
     * Set a specific episode for testing.
     */
    fun setTestEpisode(episode: Episode?) {
        _currentEpisode.value = episode
    }

    /**
     * Set specific playback state for testing.
     */
    fun setTestPlaybackState(state: PlaybackState) {
        _playbackState.value = state
    }

    /**
     * Set whether playback is active (playing) for testing.
     */
    fun setPlaying(playing: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = playing)
    }

    companion object {
        /**
         * Creates a test episode with sensible defaults.
         */
        fun createTestEpisode(
            id: Long = 1L,
            podcastId: Long = 1L,
            title: String = "Test Episode: Introduction to AI",
            description: String = "This is a test episode for UI testing purposes.",
            audioUrl: String = "https://example.com/test-episode.mp3",
            audioDuration: Int = 3600, // 1 hour
            imageUrl: String = "https://example.com/test-image.jpg"
        ): Episode {
            return Episode(
                id = id,
                episodeIndexId = id * 1000,
                podcastId = podcastId,
                title = title,
                description = description,
                audioUrl = audioUrl,
                audioDuration = audioDuration,
                imageUrl = imageUrl,
                publishedAt = System.currentTimeMillis() - 86400000L // 1 day ago
            )
        }
    }
}
