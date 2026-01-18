package com.podcast.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.podcast.app.download.DownloadWorker
import com.podcast.app.sync.SyncNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main application class with Hilt dependency injection.
 *
 * Implements WorkManager Configuration.Provider to enable Hilt injection
 * in WorkManager workers (e.g., DownloadWorker).
 */
@HiltAndroidApp
class PodcastApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Create notification channels for Android 8.0+.
     *
     * Channels:
     * - podcast_downloads: Episode download progress notifications
     * - podcast_sync_channel: New episode notifications from background sync
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Download progress channel
            val downloadChannel = NotificationChannel(
                DownloadWorker.CHANNEL_ID,
                DownloadWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for podcast episode downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(downloadChannel)

            // Sync notification channel for new episodes
            val syncChannel = NotificationChannel(
                SyncNotificationManager.CHANNEL_ID,
                SyncNotificationManager.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = SyncNotificationManager.CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(syncChannel)
        }
    }
}
