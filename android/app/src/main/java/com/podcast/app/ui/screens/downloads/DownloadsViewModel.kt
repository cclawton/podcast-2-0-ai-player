package com.podcast.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadWithEpisode(
    val download: Download,
    val episode: Episode?
)

data class DownloadsUiState(
    val activeDownloads: List<DownloadWithEpisode> = emptyList(),
    val completedDownloads: List<DownloadWithEpisode> = emptyList(),
    val totalDownloadedSize: Long = 0,
    val downloadedEpisodeCount: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val episodeDao: EpisodeDao,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadDao.getAllDownloadsFlow(),
        downloadDao.getTotalDownloadedSize(),
        downloadDao.getDownloadedEpisodeCount()
    ) { downloads, totalSize, count ->
        val activeDownloads = downloads.filter {
            it.status == DownloadStatus.PENDING || it.status == DownloadStatus.IN_PROGRESS
        }
        val completedDownloads = downloads.filter {
            it.status == DownloadStatus.COMPLETED
        }
        val failedDownloads = downloads.filter {
            it.status == DownloadStatus.FAILED
        }

        // Fetch episode info for each download
        val activeWithEpisodes = activeDownloads.map { download ->
            DownloadWithEpisode(
                download = download,
                episode = episodeDao.getEpisodeById(download.episodeId)
            )
        }
        val completedWithEpisodes = (completedDownloads + failedDownloads).map { download ->
            DownloadWithEpisode(
                download = download,
                episode = episodeDao.getEpisodeById(download.episodeId)
            )
        }

        DownloadsUiState(
            activeDownloads = activeWithEpisodes,
            completedDownloads = completedWithEpisodes,
            totalDownloadedSize = totalSize ?: 0,
            downloadedEpisodeCount = count
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DownloadsUiState()
    )

    /**
     * Cancel a pending or in-progress download.
     */
    fun cancelDownload(episodeId: Long) {
        viewModelScope.launch {
            downloadManager.cancelDownload(episodeId)
        }
    }

    /**
     * Retry a failed download.
     */
    fun retryDownload(episodeId: Long) {
        viewModelScope.launch {
            downloadManager.retryDownload(episodeId)
        }
    }

    /**
     * Delete a completed download, including the actual file.
     */
    fun deleteDownload(episodeId: Long) {
        viewModelScope.launch {
            downloadManager.deleteDownload(episodeId)
        }
    }

    /**
     * Clear all downloads, including their files.
     */
    fun clearAllDownloads() {
        viewModelScope.launch {
            _isClearing.value = true
            try {
                downloadManager.clearAllDownloads()
            } finally {
                _isClearing.value = false
            }
        }
    }
}
