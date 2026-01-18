package com.podcast.app.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.privacy.PrivacyRepository
import com.podcast.app.util.DiagnosticLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic cleanup of old downloaded episodes.
 *
 * Behavior:
 * - Runs daily when auto-delete is enabled
 * - Deletes downloads older than the configured retention period
 * - Optionally only deletes episodes that have been played (>90% progress)
 * - Never deletes the currently playing episode
 *
 * Privacy considerations:
 * - All operations are local, no network calls
 * - Respects user's retention preferences
 */
@HiltWorker
class EpisodeCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val progressDao: PlaybackProgressDao,
    private val privacyRepository: PrivacyRepository,
    private val playbackController: IPlaybackController
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "EpisodeCleanupWorker"
        const val WORK_NAME = "episode_cleanup_periodic"

        private const val PLAYED_THRESHOLD = 0.9f // 90% played = considered played

        /**
         * Schedule daily cleanup.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<EpisodeCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            DiagnosticLogger.i(TAG, "Scheduled daily episode cleanup")
        }

        /**
         * Cancel scheduled cleanup.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            DiagnosticLogger.i(TAG, "Cancelled episode cleanup")
        }
    }

    override suspend fun doWork(): Result {
        DiagnosticLogger.i(TAG, "Starting episode cleanup")

        val settings = privacyRepository.settings.first()

        // Check if auto-delete is enabled
        if (!settings.autoDeleteEnabled) {
            DiagnosticLogger.i(TAG, "Auto-delete disabled - skipping")
            return Result.success()
        }

        // Check retention period
        if (settings.downloadRetentionDays <= 0) {
            DiagnosticLogger.i(TAG, "Retention period is 0 (keep forever) - skipping")
            return Result.success()
        }

        return try {
            val deletedCount = performCleanup(
                retentionDays = settings.downloadRetentionDays,
                onlyPlayed = settings.autoDeleteOnlyPlayed
            )

            DiagnosticLogger.i(TAG, "Cleanup completed: deleted $deletedCount episodes")
            Result.success()
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Cleanup failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun performCleanup(retentionDays: Int, onlyPlayed: Boolean): Int {
        // Calculate cutoff timestamp
        val cutoffTime = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)

        // Get old downloads
        val oldDownloads = downloadDao.getDownloadsOlderThan(cutoffTime)

        if (oldDownloads.isEmpty()) {
            DiagnosticLogger.i(TAG, "No downloads older than $retentionDays days")
            return 0
        }

        // Get currently playing episode ID to avoid deleting it
        val currentEpisodeId = playbackController.currentEpisode.value?.id

        var deletedCount = 0

        for (download in oldDownloads) {
            // Never delete currently playing episode
            if (download.episodeId == currentEpisodeId) {
                DiagnosticLogger.i(TAG, "Skipping currently playing episode: ${download.episodeId}")
                continue
            }

            // Check if we should only delete played episodes
            if (onlyPlayed) {
                val progress = progressDao.getProgress(download.episodeId)
                val duration = progress?.durationSeconds ?: 0

                if (duration > 0 && progress != null) {
                    val playedPercent = progress.positionSeconds.toFloat() / duration
                    if (playedPercent < PLAYED_THRESHOLD) {
                        DiagnosticLogger.i(
                            TAG,
                            "Skipping unplayed episode ${download.episodeId} (${(playedPercent * 100).toInt()}% played)"
                        )
                        continue
                    }
                } else if (!progress?.isCompleted!!) {
                    // No progress data and not marked complete - skip
                    DiagnosticLogger.i(TAG, "Skipping episode ${download.episodeId} - no play progress")
                    continue
                }
            }

            // Delete the file
            try {
                val file = File(download.filePath)
                if (file.exists()) {
                    file.delete()
                    DiagnosticLogger.i(TAG, "Deleted file: ${download.filePath}")
                }

                // Remove from database
                downloadDao.deleteByEpisodeId(download.episodeId)
                deletedCount++

                DiagnosticLogger.i(TAG, "Cleaned up episode: ${download.episodeId}")
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Failed to delete episode ${download.episodeId}: ${e.message}")
            }
        }

        return deletedCount
    }
}
