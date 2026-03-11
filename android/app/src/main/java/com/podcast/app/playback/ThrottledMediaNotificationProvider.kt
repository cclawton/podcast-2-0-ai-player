package com.podcast.app.playback

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.podcast.app.R
import com.podcast.app.util.DiagnosticLogger

/**
 * Custom MediaNotification.Provider that throttles notification updates.
 *
 * GH#28 FIX: Android enforces a rate limit of 5.0 notification updates/second.
 * The default MediaNotificationProvider updates the notification on every
 * position change, which can exceed this limit and cause "Shedding notify"
 * warnings, leading to inconsistent foreground service state.
 *
 * This provider:
 * - Throttles position-only updates to at most once per second
 * - Always allows state changes (play/pause/stop) immediately
 * - Uses IMPORTANCE_LOW to avoid sound/vibration for media notifications
 */
@UnstableApi
class ThrottledMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        private const val TAG = "ThrottledNotification"
        const val NOTIFICATION_ID = 10116  // Match the ID from logs for continuity
        const val CHANNEL_ID = PlaybackService.NOTIFICATION_CHANNEL_ID

        // Throttle to max 1 update per second (well under 5.0/sec limit)
        private const val MIN_UPDATE_INTERVAL_MS = 1000L
    }

    private var throttleCount = 0L
    private var updateCount = 0L

    private var lastUpdateTimeMs = 0L
    private var lastPlaybackState: Int = Player.STATE_IDLE
    private var lastIsPlaying: Boolean = false

    // Cache the notification to avoid rebuilding when throttled
    private var cachedNotification: Notification? = null

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player
        val currentTimeMs = System.currentTimeMillis()

        // Check if this is a state change (not just position update)
        val isStateChange = player.playbackState != lastPlaybackState ||
                player.isPlaying != lastIsPlaying

        // Always update on state changes; throttle position-only updates
        if (!isStateChange && (currentTimeMs - lastUpdateTimeMs) < MIN_UPDATE_INTERVAL_MS) {
            throttleCount++
            // Log every 60 throttles (~1 minute)
            if (throttleCount % 60 == 0L) {
                DiagnosticLogger.d(TAG, "throttled $throttleCount updates so far (updated $updateCount)")
            }
            // Return the cached notification without rebuilding
            cachedNotification?.let { cached ->
                return MediaNotification(NOTIFICATION_ID, cached)
            }
        }

        // Update tracking state
        lastUpdateTimeMs = currentTimeMs
        lastPlaybackState = player.playbackState
        lastIsPlaying = player.isPlaying
        updateCount++

        if (isStateChange) {
            DiagnosticLogger.i(TAG, "state change notification: isPlaying=${player.isPlaying}, state=${player.playbackState}")
        }

        // Build and cache the new notification
        val notification = buildNotification(mediaSession, actionFactory)
        cachedNotification = notification
        return MediaNotification(NOTIFICATION_ID, notification)
    }

    /**
     * GH#47: Build notification with explicit media action buttons for lock screen
     * and notification shade. MediaStyle + VISIBILITY_PUBLIC ensures controls
     * appear on the lock screen. Uses Media3's actionFactory to create properly
     * routed action intents that the MediaSession handles automatically.
     */
    private fun buildNotification(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ): Notification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata

        val contentTitle = metadata.title?.toString()
            ?: metadata.displayTitle?.toString()
            ?: context.getString(R.string.app_name)

        val contentText = metadata.artist?.toString()
            ?: metadata.albumArtist?.toString()
            ?: if (player.isPlaying) context.getString(R.string.notification_playing)
            else context.getString(R.string.notification_paused)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(mediaSession.sessionActivity)

        // GH#47: Add explicit media action buttons for lock screen + notification shade
        // Action 0: Skip backward (rewind)
        builder.addAction(
            actionFactory.createCustomAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notification_rewind),
                context.getString(R.string.skip_backward),
                Player.COMMAND_SEEK_BACK.toString(),
                Bundle.EMPTY
            )
        )

        // Action 1: Play/Pause toggle
        if (player.isPlaying) {
            builder.addAction(
                actionFactory.createCustomAction(
                    mediaSession,
                    IconCompat.createWithResource(context, R.drawable.ic_notification_pause),
                    context.getString(R.string.pause),
                    Player.COMMAND_PLAY_PAUSE.toString(),
                    Bundle.EMPTY
                )
            )
        } else {
            builder.addAction(
                actionFactory.createCustomAction(
                    mediaSession,
                    IconCompat.createWithResource(context, R.drawable.ic_notification_play),
                    context.getString(R.string.play),
                    Player.COMMAND_PLAY_PAUSE.toString(),
                    Bundle.EMPTY
                )
            )
        }

        // Action 2: Skip forward
        builder.addAction(
            actionFactory.createCustomAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notification_forward),
                context.getString(R.string.skip_forward),
                Player.COMMAND_SEEK_FORWARD.toString(),
                Bundle.EMPTY
            )
        )

        // MediaStyle shows actions 0,1,2 in compact view (lock screen + collapsed notification)
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        // No custom commands handled
        return false
    }
}
