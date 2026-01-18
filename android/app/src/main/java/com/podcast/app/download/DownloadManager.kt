package com.podcast.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages episode downloads with background execution via WorkManager.
 *
 * Features:
 * - Reliable background downloads that survive app closure
 * - Automatic retry on network failure
 * - Download queue management
 * - Progress tracking via Room database
 * - Storage management (file deletion)
 *
 * Security considerations:
 * - Downloads are stored in app-private storage
 * - No external storage permissions required on Android 10+
 * - File paths are validated before operations
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val episodeDao: EpisodeDao
) {
    companion object {
        private const val TAG = "DownloadManager"
        const val WORK_NAME_PREFIX = "download_episode_"
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FILE_SIZE = "file_size"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    /**
     * Get the downloads directory for the app.
     */
    fun getDownloadsDir(): File {
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        return downloadsDir
    }

    /**
     * Get the file path for a downloaded episode.
     */
    fun getDownloadPath(episode: Episode): String {
        val podcastDir = File(getDownloadsDir(), "podcast_${episode.podcastId}")
        if (!podcastDir.exists()) {
            podcastDir.mkdirs()
        }
        // Use episode ID and sanitized title for filename
        val safeTitle = episode.title.take(50).replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return File(podcastDir, "episode_${episode.id}_$safeTitle.mp3").absolutePath
    }

    /**
     * Start downloading an episode.
     *
     * If a download already exists:
     * - COMPLETED: Delete the file and database entry
     * - IN_PROGRESS/PENDING: Cancel the download
     * - FAILED/CANCELLED: Restart the download
     */
    suspend fun downloadEpisode(episode: Episode) {
        val existingDownload = downloadDao.getDownload(episode.id)

        when (existingDownload?.status) {
            DownloadStatus.COMPLETED -> {
                // Delete the download
                deleteDownload(episode.id)
            }
            DownloadStatus.IN_PROGRESS, DownloadStatus.PENDING -> {
                // Cancel the download
                cancelDownload(episode.id)
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED, null -> {
                // Start a new download
                startDownload(episode)
            }
        }
    }

    /**
     * Start downloading an episode.
     */
    private suspend fun startDownload(episode: Episode) {
        val filePath = getDownloadPath(episode)

        // Create download entry
        val download = Download(
            episodeId = episode.id,
            filePath = filePath,
            fileSize = episode.audioSize,
            status = DownloadStatus.PENDING
        )
        downloadDao.insert(download)

        // Create WorkManager request
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putLong(KEY_EPISODE_ID, episode.id)
            .putString(KEY_AUDIO_URL, episode.audioUrl)
            .putString(KEY_FILE_PATH, filePath)
            .putLong(KEY_FILE_SIZE, episode.audioSize ?: 0L)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download")
            .addTag("episode_${episode.id}")
            .build()

        workManager.enqueueUniqueWork(
            "$WORK_NAME_PREFIX${episode.id}",
            ExistingWorkPolicy.REPLACE,
            downloadWork
        )
    }

    /**
     * Cancel a pending or in-progress download.
     */
    suspend fun cancelDownload(episodeId: Long) {
        workManager.cancelUniqueWork("$WORK_NAME_PREFIX$episodeId")
        downloadDao.updateDownloadStatus(episodeId, DownloadStatus.CANCELLED)

        // Clean up partial file
        val download = downloadDao.getDownload(episodeId)
        download?.let {
            deleteFile(it.filePath)
        }
    }

    /**
     * Delete a completed download.
     */
    suspend fun deleteDownload(episodeId: Long) {
        val download = downloadDao.getDownload(episodeId)
        download?.let {
            deleteFile(it.filePath)
        }
        downloadDao.deleteByEpisodeId(episodeId)
    }

    /**
     * Retry a failed download.
     */
    suspend fun retryDownload(episodeId: Long) {
        val episode = episodeDao.getEpisodeById(episodeId) ?: return
        downloadDao.deleteByEpisodeId(episodeId)
        startDownload(episode)
    }

    /**
     * Clear all downloads.
     */
    suspend fun clearAllDownloads() {
        // Cancel all pending downloads
        workManager.cancelAllWorkByTag("download")

        // Get all downloads and delete files
        val downloads = downloadDao.getAllDownloadsFlow()
        scope.launch {
            downloads.collect { downloadList ->
                downloadList.forEach { download ->
                    deleteFile(download.filePath)
                }
            }
        }

        // Clear database entries
        // Note: We'd need a deleteAll() method in DAO, but for now we iterate
        scope.launch {
            downloads.collect { downloadList ->
                downloadList.forEach { download ->
                    downloadDao.deleteByEpisodeId(download.episodeId)
                }
            }
        }
    }

    /**
     * Get the total size of downloaded files.
     */
    fun getTotalDownloadedSize(): Flow<Long> {
        return downloadDao.getTotalDownloadedSize().map { it ?: 0L }
    }

    /**
     * Get the count of downloaded episodes.
     */
    fun getDownloadedEpisodeCount(): Flow<Int> {
        return downloadDao.getDownloadedEpisodeCount()
    }

    /**
     * Observe download status for an episode.
     */
    fun observeDownload(episodeId: Long): Flow<Download?> {
        return downloadDao.observeDownload(episodeId)
    }

    /**
     * Observe all downloads.
     */
    fun observeAllDownloads(): Flow<List<Download>> {
        return downloadDao.getAllDownloadsFlow()
    }

    /**
     * Observe pending/active downloads.
     */
    fun observePendingDownloads(): Flow<List<Download>> {
        return downloadDao.getPendingDownloads()
    }

    /**
     * Observe completed downloads.
     */
    fun observeCompletedDownloads(): Flow<List<Download>> {
        return downloadDao.getCompletedDownloads()
    }

    /**
     * Get WorkManager status for a download.
     */
    fun getWorkInfo(episodeId: Long): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkFlow("$WORK_NAME_PREFIX$episodeId")
    }

    /**
     * Check if an episode is downloaded and playable.
     */
    suspend fun isEpisodeDownloaded(episodeId: Long): Boolean {
        val download = downloadDao.getDownload(episodeId) ?: return false
        if (download.status != DownloadStatus.COMPLETED) return false

        val file = File(download.filePath)
        return file.exists() && file.canRead()
    }

    /**
     * Get the local file path for a downloaded episode, or null if not available.
     */
    suspend fun getLocalFilePath(episodeId: Long): String? {
        val download = downloadDao.getDownload(episodeId) ?: return null
        if (download.status != DownloadStatus.COMPLETED) return null

        val file = File(download.filePath)
        return if (file.exists() && file.canRead()) file.absolutePath else null
    }

    /**
     * Calculate storage used by downloads.
     */
    fun calculateStorageUsed(): Long {
        val downloadsDir = getDownloadsDir()
        return calculateDirectorySize(downloadsDir)
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    /**
     * Delete a file safely.
     */
    private fun deleteFile(filePath: String) {
        try {
            val file = File(filePath)
            // Validate the file is within our downloads directory
            if (file.absolutePath.startsWith(getDownloadsDir().absolutePath)) {
                file.delete()
            }
        } catch (e: Exception) {
            // Log but don't throw - file deletion failure shouldn't crash the app
            android.util.Log.e(TAG, "Failed to delete file: $filePath", e)
        }
    }
}
