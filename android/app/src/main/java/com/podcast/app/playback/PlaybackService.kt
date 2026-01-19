package com.podcast.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.podcast.app.R
import com.podcast.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media playback service that enables background audio playback.
 *
 * This service:
 * - Runs as a foreground service with a persistent notification
 * - Maintains playback when the app is backgrounded
 * - Uses proper wake locks for downloaded content
 * - Integrates with Android's media controls (lock screen, Bluetooth, etc.)
 *
 * GH#28: Fix for playback stopping after ~1 minute when app is backgrounded.
 * Uses ThrottledMediaNotificationProvider to prevent notification rate limiting.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "podcast_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    @Inject
    lateinit var playbackController: IPlaybackController

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
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

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startForegroundWithNotification()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            // Optionally stop foreground when playback ends
                            stopForeground(STOP_FOREGROUND_DETACH)
                        }
                    }
                })
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playing))
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentIntent(createMainActivityPendingIntent())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
