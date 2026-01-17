package com.podcast.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.PlaybackProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls podcast playback using Media3 ExoPlayer.
 *
 * Features:
 * - Play/pause/seek/skip controls
 * - Playback speed adjustment
 * - Progress tracking and persistence
 * - Chapter support
 * - Queue management
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val playbackProgressDao: PlaybackProgressDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    val queue: StateFlow<List<Episode>> = _queue.asStateFlow()

    // Listener must be defined before player initialization
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val playerState = when (state) {
                Player.STATE_IDLE -> PlayerState.IDLE
                Player.STATE_BUFFERING -> PlayerState.BUFFERING
                Player.STATE_READY -> PlayerState.READY
                Player.STATE_ENDED -> PlayerState.ENDED
                else -> PlayerState.IDLE
            }
            updatePlaybackState { it.copy(playerState = playerState) }

            if (state == Player.STATE_ENDED) {
                onPlaybackEnded()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePosition()
        }
    }

    // Lazy initialization with nullable backing field for proper release
    private var _exoPlayer: ExoPlayer? = null
    private val exoPlayer: ExoPlayer
        get() {
            if (_exoPlayer == null) {
                _exoPlayer = ExoPlayer.Builder(context)
                    .setHandleAudioBecomingNoisy(true)
                    .build()
                    .apply {
                        addListener(playerListener)
                    }
            }
            return _exoPlayer!!
        }

    /**
     * Play a specific episode.
     */
    suspend fun playEpisode(episodeId: Long, startPositionMs: Int = 0) {
        val episode = episodeDao.getEpisodeById(episodeId) ?: return

        _currentEpisode.value = episode

        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id.toString())
            .setUri(episode.audioUrl)
            .build()

        exoPlayer.apply {
            setMediaItem(mediaItem)
            prepare()

            // Restore position if available
            val savedProgress = playbackProgressDao.getProgress(episodeId)
            val position = if (startPositionMs > 0) {
                startPositionMs.toLong()
            } else {
                (savedProgress?.positionSeconds ?: 0) * 1000L
            }

            if (position > 0) {
                seekTo(position)
            }

            // Restore playback speed
            val speed = savedProgress?.playbackSpeed ?: 1.0f
            playbackParameters = PlaybackParameters(speed)

            play()
        }

        startProgressTracking()
    }

    /**
     * Pause playback.
     */
    fun pause() {
        _exoPlayer?.pause()
        saveCurrentProgress()
    }

    /**
     * Resume playback.
     */
    fun resume() {
        _exoPlayer?.play()
    }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        val player = _exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Seek to a specific position.
     */
    fun seekTo(positionMs: Long) {
        _exoPlayer?.seekTo(positionMs)
        updatePosition()
    }

    /**
     * Skip forward by specified seconds.
     */
    fun skipForward(seconds: Int = 15) {
        val player = _exoPlayer ?: return
        val newPosition = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration)
        seekTo(newPosition)
    }

    /**
     * Skip backward by specified seconds.
     */
    fun skipBackward(seconds: Int = 15) {
        val player = _exoPlayer ?: return
        val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
        seekTo(newPosition)
    }

    /**
     * Set playback speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.5f, 3.0f)
        _exoPlayer?.playbackParameters = PlaybackParameters(validSpeed)
        updatePlaybackState { it.copy(playbackSpeed = validSpeed) }
    }

    /**
     * Stop playback and release resources.
     */
    fun stop() {
        saveCurrentProgress()
        _exoPlayer?.stop()
        _currentEpisode.value = null
        progressJob?.cancel()
    }

    /**
     * Add episode to queue.
     */
    fun addToQueue(episode: Episode) {
        _queue.value = _queue.value + episode
    }

    /**
     * Remove episode from queue.
     */
    fun removeFromQueue(episode: Episode) {
        _queue.value = _queue.value.filter { it.id != episode.id }
    }

    /**
     * Clear the queue.
     */
    fun clearQueue() {
        _queue.value = emptyList()
    }

    /**
     * Play next episode in queue.
     */
    suspend fun playNext() {
        val nextEpisode = _queue.value.firstOrNull()
        if (nextEpisode != null) {
            _queue.value = _queue.value.drop(1)
            playEpisode(nextEpisode.id)
        }
    }

    /**
     * Get current playback status for MCP.
     */
    fun getPlaybackStatus(): PlaybackState = _playbackState.value

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                updatePosition()
                saveCurrentProgress()
                delay(5000) // Save progress every 5 seconds
            }
        }
    }

    private fun updatePosition() {
        val player = _exoPlayer ?: return
        updatePlaybackState {
            it.copy(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0)
            )
        }
    }

    private fun saveCurrentProgress() {
        val episode = _currentEpisode.value ?: return
        val player = _exoPlayer ?: return

        // Capture player values on main thread (ExoPlayer requirement)
        val positionMs = player.currentPosition
        val durationMs = player.duration
        val speed = _playbackState.value.playbackSpeed

        scope.launch(Dispatchers.IO) {
            val progress = PlaybackProgress(
                episodeId = episode.id,
                positionSeconds = (positionMs / 1000).toInt(),
                durationSeconds = (durationMs / 1000).toInt(),
                lastPlayedAt = System.currentTimeMillis(),
                playbackSpeed = speed
            )
            playbackProgressDao.insertOrUpdate(progress)
        }
    }

    private fun onPlaybackEnded() {
        val episode = _currentEpisode.value ?: return

        scope.launch(Dispatchers.IO) {
            playbackProgressDao.markAsCompleted(episode.id)
        }

        // Auto-play next in queue
        scope.launch {
            playNext()
        }
    }

    private inline fun updatePlaybackState(update: (PlaybackState) -> PlaybackState) {
        _playbackState.value = update(_playbackState.value)
    }

    fun release() {
        progressJob?.cancel()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val playerState: PlayerState = PlayerState.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1.0f
) {
    val positionSeconds: Int get() = (positionMs / 1000).toInt()
    val durationSeconds: Int get() = (durationMs / 1000).toInt()
    val progressPercent: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}
