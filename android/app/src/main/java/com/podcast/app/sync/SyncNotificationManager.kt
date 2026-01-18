package com.podcast.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.podcast.app.R
import com.podcast.app.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notifications for background sync operations.
 *
 * Privacy considerations:
 * - Uses local notification system only
 * - No network calls for notification handling
 * - Respects user notification preferences
 */
@Singleton
class SyncNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "podcast_sync_channel"
        const val CHANNEL_NAME = "Episode Updates"
        const val CHANNEL_DESCRIPTION = "Notifications for new podcast episodes"

        private const val NOTIFICATION_ID_NEW_EPISODES = 1001
        private const val NOTIFICATION_ID_SYNC_ERROR = 1002
    }

    /**
     * Create the notification channel for Android 8.0+.
     * This should be called during app initialization.
     */
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    /**
     * Show notification for new episodes found during sync.
     *
     * @param newEpisodes List of new episodes to notify about
     * @param maxToShow Maximum number of episodes to show in notification
     */
    fun showNewEpisodesNotification(
        newEpisodes: List<FeedRefreshService.NewEpisodeInfo>,
        maxToShow: Int = 5
    ) {
        if (newEpisodes.isEmpty()) return

        // Check notification permission (required on Android 13+)
        if (!hasNotificationPermission()) {
            return
        }

        // Create intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "library")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content
        val title = if (newEpisodes.size == 1) {
            "New Episode Available"
        } else {
            "${newEpisodes.size} New Episodes"
        }

        val contentText = if (newEpisodes.size == 1) {
            "${newEpisodes.first().podcastTitle}: ${newEpisodes.first().episodeTitle}"
        } else {
            val podcastCount = newEpisodes.map { it.podcastTitle }.distinct().size
            "$podcastCount podcasts have new episodes"
        }

        // Build expanded content for InboxStyle
        val inboxStyle = NotificationCompat.InboxStyle()
        newEpisodes.take(maxToShow).forEach { episode ->
            inboxStyle.addLine("${episode.podcastTitle}: ${episode.episodeTitle}")
        }

        if (newEpisodes.size > maxToShow) {
            inboxStyle.setSummaryText("+ ${newEpisodes.size - maxToShow} more")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEW_EPISODES, notification)
        } catch (e: SecurityException) {
            // Notification permission was revoked
        }
    }

    /**
     * Show notification for sync error.
     *
     * @param errorMessage The error message to display
     */
    fun showSyncErrorNotification(errorMessage: String) {
        if (!hasNotificationPermission()) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "settings")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sync Error")
            .setContentText(errorMessage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SYNC_ERROR, notification)
        } catch (e: SecurityException) {
            // Notification permission was revoked
        }
    }

    /**
     * Cancel all sync-related notifications.
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).apply {
            cancel(NOTIFICATION_ID_NEW_EPISODES)
            cancel(NOTIFICATION_ID_SYNC_ERROR)
        }
    }

    /**
     * Check if notification permission is granted.
     * Required on Android 13+ (API 33+).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if the notification channel is enabled by the user.
     */
    fun isChannelEnabled(): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager?.getNotificationChannel(CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
