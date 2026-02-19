package com.podcast.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.podcast.app.R
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.PlaybackProgress
import com.podcast.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Media playback service that enables background audio playback.
 *
 * This service:
 * - Runs as a foreground service with a persistent notification
 * - Maintains playback when the app is backgrounded
 * - Uses proper wake locks for downloaded content
 * - Integrates with Android's media controls (lock screen, Bluetooth, etc.)
 * - Owns the ExoPlayer instance and provides it to PlaybackController
 *
 * GH#28: Fix for playback stopping after ~1 minute when app is backgrounded.
 * Uses ThrottledMediaNotificationProvider to prevent notification rate limiting.
 *
 * Architecture:
 * - PlaybackService owns the ExoPlayer for proper lifecycle management
 * - UI components connect via MediaController for playback control
 * - PlaybackController provides a singleton interface for app-wide access
 * - Progress is persisted to database for cross-session resume
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "podcast_playback_channel"
        const val NOTIFICATION_ID = 1001

        // Intent actions for direct service control
        const val ACTION_PLAY_EPISODE = "com.podcast.app.action.PLAY_EPISODE"
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_START_POSITION_MS = "start_position_ms"

        // Singleton for service access (set when service is created)
        @Volatile
        private var instance: PlaybackService? = null

        /**
         * Gets the current service instance, if running.
         * Returns null if service is not started.
         */
        fun getInstance(): PlaybackService? = instance

        /**
         * Gets a MediaController connected to this service.
         * Use this for UI components to control playback.
         */
        fun getController(context: Context): ListenableFuture<MediaController> {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            return MediaController.Builder(context, sessionToken).buildAsync()
        }
    }

    @Inject
    lateinit var episodeDao: EpisodeDao

    @Inject
    lateinit var podcastDao: PodcastDao

    @Inject
    lateinit var playbackProgressDao: PlaybackProgressDao

    @Inject
    lateinit var downloadDao: DownloadDao

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    // Playback state exposed for observation
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _currentPodcastTitle = MutableStateFlow<String?>(null)
    val currentPodcastTitle: StateFlow<String?> = _currentPodcastTitle.asStateFlow()

    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    val queue: StateFlow<List<Episode>> = _queue.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        configureNotificationProvider()
        initializePlayer()
    }

    /**
     * Configures the custom notification provider to prevent rate limiting.
     *
     * GH#28: Android enforces a 5.0 updates/second rate limit on notifications.
     * The default provider updates on every position change, exceeding this limit.
     * Our ThrottledMediaNotificationProvider limits updates to once per second.
     */
    private fun configureNotificationProvider() {
        setMediaNotificationProvider(ThrottledMediaNotificationProvider(this))
    }

    /**
     * Creates the notification channel for Android O+.
     * Uses LOW importance to avoid sound/vibration for media notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_playback_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Initializes ExoPlayer with proper audio attributes and wake mode.
     *
     * Key settings for background playback:
     * - WAKE_MODE_LOCAL: Keeps CPU awake for downloaded content playback
     * - handleAudioBecomingNoisy: Auto-pause on headphone disconnect
     * - AUDIO_CONTENT_TYPE_SPEECH: Optimized for podcast content
     */
    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                mediaSession = MediaSession.Builder(this, player)
                    .setSessionActivity(createMainActivityPendingIntent())
                    .build()

                player.addListener(playerListener)
            }
    }

    /**
     * Player listener to track state changes and update UI.
     */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startForegroundWithNotification()
            }
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
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePosition()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            updatePlaybackState { it.copy(playbackSpeed = playbackParameters.speed) }
        }
    }

    /**
     * Creates a PendingIntent to launch MainActivity when notification is tapped.
     */
    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Starts the service in foreground mode with a persistent notification.
     * This is required to prevent Android from killing the service.
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the playback notification.
     *
     * Note: MediaSessionService handles media notification automatically,
     * but we provide a fallback notification for edge cases.
     */
    private fun buildNotification(): Notification {
        val episode = _currentEpisode.value
        val title = episode?.title ?: getString(R.string.app_name)
        val text = _currentPodcastTitle.value ?: getString(R.string.notification_playing)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentIntent(createMainActivityPendingIntent())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Handle start commands to play specific episodes.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_EPISODE) {
            val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, -1)
            val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0)
            if (episodeId > 0) {
                playEpisode(episodeId, startPositionMs)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Plays an episode by ID.
     *
     * If the episode has been downloaded, plays from local storage (works offline).
     * Otherwise, streams from the network URL.
     */
    fun playEpisode(episodeId: Long, startPositionMs: Long = 0) {
        serviceScope.launch {
            val episode = withContext(Dispatchers.IO) {
                episodeDao.getEpisodeById(episodeId)
            } ?: return@launch

            // Fetch podcast title for notification
            val podcastTitle = withContext(Dispatchers.IO) {
                podcastDao.getPodcastById(episode.podcastId)?.title
            }

            _currentEpisode.value = episode
            _currentPodcastTitle.value = podcastTitle

            // Check for completed download to enable offline playback
            val audioUri = getPlayableUri(episodeId, episode.audioUrl)

            // Build media item with metadata for notification
            val mediaItem = MediaItem.Builder()
                .setMediaId(episode.id.toString())
                .setUri(audioUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(podcastTitle)
                        .setArtworkUri(episode.imageUrl?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()

            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()

                // Restore position if available
                val savedProgress = withContext(Dispatchers.IO) {
                    playbackProgressDao.getProgress(episodeId)
                }
                val position = if (startPositionMs > 0) {
                    startPositionMs
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
    }

    /**
     * Gets the playable URI for an episode.
     * Returns local file path if downloaded, otherwise network URL.
     */
    private suspend fun getPlayableUri(episodeId: Long, fallbackUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val download = downloadDao.getDownload(episodeId)
                if (download != null &&
                    download.status == DownloadStatus.COMPLETED &&
                    download.filePath.isNotBlank()
                ) {
                    val file = File(download.filePath)
                    if (file.exists() && file.canRead()) {
                        "file://${file.absolutePath}"
                    } else {
                        fallbackUrl
                    }
                } else {
                    fallbackUrl
                }
            } catch (e: Exception) {
                fallbackUrl
            }
        }
    }

    /**
     * Pauses playback and saves progress.
     */
    fun pause() {
        exoPlayer?.pause()
        saveCurrentProgress()
    }

    /**
     * Resumes playback.
     */
    fun resume() {
        exoPlayer?.play()
    }

    /**
     * Toggles play/pause state.
     */
    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Seeks to a specific position.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updatePosition()
    }

    /**
     * Skips forward by specified seconds.
     */
    fun skipForward(seconds: Int = 15) {
        val player = exoPlayer ?: return
        val newPosition = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration)
        seekTo(newPosition)
    }

    /**
     * Skips backward by specified seconds.
     */
    fun skipBackward(seconds: Int = 15) {
        val player = exoPlayer ?: return
        val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
        seekTo(newPosition)
    }

    /**
     * Sets playback speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.5f, 3.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(validSpeed)
        updatePlaybackState { it.copy(playbackSpeed = validSpeed) }
    }

    /**
     * Stops playback and clears current episode.
     */
    fun stop() {
        saveCurrentProgress()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentEpisode.value = null
        _currentPodcastTitle.value = null
        progressJob?.cancel()
    }

    /**
     * Adds an episode to the queue.
     */
    fun addToQueue(episode: Episode) {
        _queue.value = _queue.value + episode
    }

    /**
     * Removes an episode from the queue.
     */
    fun removeFromQueue(episode: Episode) {
        _queue.value = _queue.value.filter { it.id != episode.id }
    }

    /**
     * Clears the queue.
     */
    fun clearQueue() {
        _queue.value = emptyList()
    }

    /**
     * Plays the next episode in the queue.
     */
    fun playNext() {
        val nextEpisode = _queue.value.firstOrNull()
        if (nextEpisode != null) {
            _queue.value = _queue.value.drop(1)
            playEpisode(nextEpisode.id)
        }
    }

    /**
     * Gets the current playback status.
     */
    fun getPlaybackStatus(): PlaybackState = _playbackState.value

    /**
     * Provides direct access to the ExoPlayer for advanced use cases.
     * Prefer using MediaController for UI components.
     */
    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * Starts periodic progress tracking and persistence.
     */
    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (true) {
                updatePosition()
                saveCurrentProgress()
                delay(5000) // Save progress every 5 seconds
            }
        }
    }

    /**
     * Updates the current playback position state.
     */
    private fun updatePosition() {
        val player = exoPlayer ?: return
        updatePlaybackState {
            it.copy(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0)
            )
        }
    }

    /**
     * Saves current playback progress to database.
     */
    private fun saveCurrentProgress() {
        val episode = _currentEpisode.value ?: return
        val player = exoPlayer ?: return

        val positionMs = player.currentPosition
        val durationMs = player.duration
        val speed = _playbackState.value.playbackSpeed

        serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = PlaybackProgress(
                    episodeId = episode.id,
                    positionSeconds = (positionMs / 1000).toInt(),
                    durationSeconds = (durationMs / 1000).toInt(),
                    lastPlayedAt = System.currentTimeMillis(),
                    playbackSpeed = speed
                )
                playbackProgressDao.insertOrUpdate(progress)
            } catch (e: Exception) {
                // Ignore FK constraint failures
            }
        }
    }

    /**
     * Called when playback ends. Marks episode complete and auto-plays next.
     */
    private fun onPlaybackEnded() {
        val episode = _currentEpisode.value ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                playbackProgressDao.markAsCompleted(episode.id)
            } catch (e: Exception) {
                // Ignore FK constraint failures
            }
        }

        // Auto-play next in queue
        serviceScope.launch {
            playNext()
        }
    }

    /**
     * Helper to update playback state atomically.
     */
    private inline fun updatePlaybackState(update: (PlaybackState) -> PlaybackState) {
        _playbackState.value = update(_playbackState.value)
    }

    /**
     * Returns the MediaSession for this service.
     * Called by the system when a controller wants to connect.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Called when the app is removed from recent apps.
     * Only stops the service if nothing is playing.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /**
     * Releases all resources when the service is destroyed.
     */
    override fun onDestroy() {
        instance = null

        // Save final progress before destroying
        saveCurrentProgress()

        progressJob?.cancel()
        serviceScope.cancel()

        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
