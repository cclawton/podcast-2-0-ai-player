package com.podcast.app.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.util.DiagnosticLogger
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
 * Controls podcast playback by delegating to PlaybackService.
 *
 * All playback goes through the foreground service so that Android
 * keeps the process alive when the app is backgrounded. This class
 * is a thin proxy: it starts the service, then forwards controls
 * and observes state from the service's ExoPlayer instance.
 *
 * GH#28: Previously this class created its own ExoPlayer without a
 * foreground service, causing Android to kill playback after ~1 minute.
 */
@UnstableApi
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context
) : IPlaybackController {

    companion object {
        private const val TAG = "PlaybackController"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    override val currentEpisode: StateFlow<Episode?>
        get() = service?.currentEpisode ?: _currentEpisode.asStateFlow()

    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    override val queue: StateFlow<List<Episode>>
        get() = service?.queue ?: _queue.asStateFlow()

    /** Reference to the running service, if available. */
    private val service: PlaybackService?
        get() = PlaybackService.getInstance()

    /**
     * Starts PlaybackService and tells it to play the given episode.
     *
     * The service runs as a foreground service with a persistent notification,
     * which keeps Android from killing the process in the background.
     */
    override suspend fun playEpisode(episodeId: Long, startPositionMs: Int) {
        DiagnosticLogger.i(TAG, "playEpisode: starting PlaybackService for episode=$episodeId, startPos=$startPositionMs")

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_EPISODE
            putExtra(PlaybackService.EXTRA_EPISODE_ID, episodeId)
            putExtra(PlaybackService.EXTRA_START_POSITION_MS, startPositionMs.toLong())
        }

        ContextCompat.startForegroundService(context, intent)

        // Start observing the service's player state
        startStateObserver()
    }

    override fun pause() {
        DiagnosticLogger.d(TAG, "pause")
        service?.pause() ?: DiagnosticLogger.w(TAG, "pause: service not running")
    }

    override fun resume() {
        DiagnosticLogger.d(TAG, "resume")
        service?.resume() ?: DiagnosticLogger.w(TAG, "resume: service not running")
    }

    override fun togglePlayPause() {
        service?.togglePlayPause() ?: DiagnosticLogger.w(TAG, "togglePlayPause: service not running")
    }

    override fun seekTo(positionMs: Long) {
        service?.seekTo(positionMs) ?: DiagnosticLogger.w(TAG, "seekTo: service not running")
    }

    override fun skipForward(seconds: Int) {
        service?.skipForward(seconds) ?: DiagnosticLogger.w(TAG, "skipForward: service not running")
    }

    override fun skipBackward(seconds: Int) {
        service?.skipBackward(seconds) ?: DiagnosticLogger.w(TAG, "skipBackward: service not running")
    }

    override fun setPlaybackSpeed(speed: Float) {
        service?.setPlaybackSpeed(speed) ?: DiagnosticLogger.w(TAG, "setPlaybackSpeed: service not running")
    }

    override fun stop() {
        DiagnosticLogger.i(TAG, "stop")
        service?.stop()
        progressJob?.cancel()
    }

    override fun addToQueue(episode: Episode) {
        service?.addToQueue(episode) ?: run {
            _queue.value = _queue.value + episode
        }
    }

    override fun removeFromQueue(episode: Episode) {
        service?.removeFromQueue(episode) ?: run {
            _queue.value = _queue.value.filter { it.id != episode.id }
        }
    }

    override fun clearQueue() {
        service?.clearQueue() ?: run {
            _queue.value = emptyList()
        }
    }

    override suspend fun playNext() {
        service?.playNext() ?: DiagnosticLogger.w(TAG, "playNext: service not running")
    }

    override fun getPlaybackStatus(): PlaybackState {
        return service?.getPlaybackStatus() ?: _playbackState.value
    }

    override fun release() {
        progressJob?.cancel()
    }

    /**
     * Periodically polls the service's player for position and state updates,
     * and publishes them to [playbackState] so UI components stay in sync.
     */
    private fun startStateObserver() {
        progressJob?.cancel()
        progressJob = scope.launch {
            DiagnosticLogger.d(TAG, "startStateObserver: polling service state every 1s")
            while (true) {
                val svc = service
                if (svc != null) {
                    val player = svc.getPlayer()
                    if (player != null) {
                        _playbackState.value = PlaybackState(
                            isPlaying = player.isPlaying,
                            playerState = when (player.playbackState) {
                                Player.STATE_IDLE -> PlayerState.IDLE
                                Player.STATE_BUFFERING -> PlayerState.BUFFERING
                                Player.STATE_READY -> PlayerState.READY
                                Player.STATE_ENDED -> PlayerState.ENDED
                                else -> PlayerState.IDLE
                            },
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0),
                            playbackSpeed = player.playbackParameters.speed
                        )
                    }
                    // Also sync currentEpisode and queue for callers using our local flows
                    _currentEpisode.value = svc.currentEpisode.value
                    _queue.value = svc.queue.value
                }
                delay(1000)
            }
        }
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
