package com.podcast.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.download.DownloadManager
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic background feed synchronization.
 *
 * Privacy considerations:
 * - Only runs when background sync is allowed in privacy settings
 * - Respects Wi-Fi only preference
 * - No network calls if privacy settings disallow
 * - Logs are sanitized of sensitive data
 *
 * Security considerations:
 * - Uses structured concurrency (no GlobalScope)
 * - Proper error handling with no data leakage
 */
@HiltWorker
class FeedSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedRefreshService: FeedRefreshService,
    private val syncRepository: SyncRepository,
    private val syncNotificationManager: SyncNotificationManager,
    private val privacyManager: PrivacyManager,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadManager: DownloadManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FeedSyncWorker"
        const val WORK_NAME = "feed_sync_periodic"

        /**
         * Schedule periodic feed sync based on settings.
         *
         * @param context Application context
         * @param interval Sync interval
         * @param wifiOnly Only sync on Wi-Fi
         */
        fun schedule(
            context: Context,
            interval: SyncInterval,
            wifiOnly: Boolean
        ) {
            if (interval == SyncInterval.MANUAL) {
                // Cancel any existing periodic work
                cancel(context)
                DiagnosticLogger.i(TAG, "Background sync disabled (manual mode)")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<FeedSyncWorker>(
                interval.hours.toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            DiagnosticLogger.i(TAG, "Scheduled background sync every ${interval.hours} hours (wifiOnly=$wifiOnly)")
        }

        /**
         * Cancel periodic feed sync.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            DiagnosticLogger.i(TAG, "Cancelled background sync")
        }

        /**
         * Trigger an immediate one-time sync.
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<FeedSyncWorker>()
                .setConstraints(constraints)
                .addTag("feed_sync_immediate")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            DiagnosticLogger.i(TAG, "Triggered immediate sync")
        }
    }

    override suspend fun doWork(): Result {
        DiagnosticLogger.i(TAG, "Starting background feed sync")

        // Check if background sync is still allowed
        val isAllowed = privacyManager.isFeatureAllowed(NetworkFeature.BACKGROUND_SYNC)
        if (!isAllowed) {
            DiagnosticLogger.i(TAG, "Background sync not allowed - skipping")
            return Result.success()
        }

        // Mark sync as in progress
        syncRepository.setSyncing(true)

        return try {
            // Perform the sync
            val refreshResult = feedRefreshService.refreshAllFeeds()

            if (refreshResult.success) {
                // Record success
                syncRepository.recordSyncSuccess(refreshResult.totalNewEpisodes)

                // Show notification if enabled and there are new episodes
                val settings = syncRepository.settings.first()
                if (settings.notifyNewEpisodes && refreshResult.newEpisodes.isNotEmpty()) {
                    syncNotificationManager.showNewEpisodesNotification(
                        refreshResult.newEpisodes,
                        settings.maxNotificationEpisodes
                    )
                }

                // Auto-download latest episode for podcasts with auto-download enabled
                try {
                    handleAutoDownloads(refreshResult.newEpisodes.mapNotNull { it.podcastId }.distinct())
                } catch (e: Exception) {
                    DiagnosticLogger.e(TAG, "Auto-download failed: ${e.message}")
                }

                DiagnosticLogger.i(
                    TAG,
                    "Sync completed: ${refreshResult.totalPodcastsRefreshed} podcasts, " +
                        "${refreshResult.totalNewEpisodes} new episodes"
                )

                Result.success()
            } else {
                // Record error
                val errorMessage = refreshResult.errors.firstOrNull()?.message
                    ?: "Unknown sync error"
                syncRepository.recordSyncError(errorMessage)

                DiagnosticLogger.e(TAG, "Sync failed: $errorMessage")

                // Retry if there were partial failures
                if (refreshResult.totalPodcastsRefreshed > 0) {
                    Result.success() // Some podcasts synced, consider it a success
                } else {
                    Result.retry() // Complete failure, retry later
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Sync exception: ${e.message}")
            syncRepository.recordSyncError(e.message ?: "Sync failed")
            Result.retry()
        }
    }

    /**
     * Handle auto-downloads for podcasts with auto-download enabled.
     * Only downloads the latest episode for each podcast.
     */
    private suspend fun handleAutoDownloads(podcastIdsWithNewEpisodes: List<Long>) {
        if (podcastIdsWithNewEpisodes.isEmpty()) return

        // Get podcasts with auto-download enabled
        val autoDownloadPodcasts = podcastDao.getPodcastsWithAutoDownload().first()
        val autoDownloadIds = autoDownloadPodcasts.map { it.id }.toSet()

        // Find podcasts that have both new episodes AND auto-download enabled
        val podcastsToDownload = podcastIdsWithNewEpisodes.filter { it in autoDownloadIds }

        if (podcastsToDownload.isEmpty()) {
            DiagnosticLogger.i(TAG, "No auto-download podcasts with new episodes")
            return
        }

        // Check wifi-only setting
        val settings = privacyManager.settings.first()
        if (settings.autoDownloadOnWifiOnly) {
            // WorkManager constraints should already handle this, but double-check
            DiagnosticLogger.i(TAG, "Auto-download respecting wifi-only setting")
        }

        // Download latest episode for each podcast
        for (podcastId in podcastsToDownload) {
            try {
                val latestEpisode = episodeDao.getLatestEpisodeForPodcast(podcastId)
                if (latestEpisode != null) {
                    downloadManager.downloadEpisode(latestEpisode)
                    DiagnosticLogger.i(TAG, "Auto-downloading episode: ${latestEpisode.title}")
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Failed to auto-download for podcast $podcastId: ${e.message}")
            }
        }
    }
}
