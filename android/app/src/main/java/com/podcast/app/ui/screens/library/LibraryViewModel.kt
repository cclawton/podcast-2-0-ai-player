package com.podcast.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlaybackState
import com.podcast.app.privacy.PrivacyManager
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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playbackController: IPlaybackController,
    private val privacyManager: PrivacyManager,
    private val downloadDao: com.podcast.app.data.local.dao.DownloadDao
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val subscribedPodcasts: StateFlow<List<Podcast>> = repository.getSubscribedPodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentEpisodes: StateFlow<List<Episode>> = repository.getRecentEpisodes(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<Map<Long, Download>> = downloadDao.getAllDownloadsFlow()
        .map { list -> list.associateBy { it.episodeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Map of podcast ID to image URL for fallback images
    val podcastImages: StateFlow<Map<Long, String?>> = subscribedPodcasts
        .map { podcasts -> podcasts.associate { it.id to it.imageUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentEpisode: StateFlow<Episode?> = playbackController.currentEpisode

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    val canUseNetwork: StateFlow<Boolean> = privacyManager.canUseNetwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                subscribedPodcasts.value.forEach { podcast ->
                    repository.refreshEpisodes(podcast.id)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun playEpisode(episodeId: Long) {
        viewModelScope.launch {
            playbackController.playEpisode(episodeId)
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun skipNext() {
        viewModelScope.launch {
            playbackController.playNext()
        }
    }

    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            val existingDownload = downloadDao.getDownload(episode.id)
            when (existingDownload?.status) {
                DownloadStatus.COMPLETED -> {
                    // Delete the download
                    downloadDao.deleteByEpisodeId(episode.id)
                    // TODO: Also delete the actual file
                }
                DownloadStatus.IN_PROGRESS, DownloadStatus.PENDING -> {
                    // Cancel the download
                    downloadDao.updateDownloadStatus(episode.id, DownloadStatus.CANCELLED)
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED, null -> {
                    // Start a new download
                    val download = Download(
                        episodeId = episode.id,
                        filePath = getDownloadPath(episode),
                        status = DownloadStatus.PENDING
                    )
                    downloadDao.insert(download)
                    // TODO: Trigger actual download via DownloadManager
                    startDownload(episode.id)
                }
            }
        }
    }

    private fun getDownloadPath(episode: Episode): String {
        return "downloads/${episode.podcastId}/${episode.id}.mp3"
    }

    private suspend fun startDownload(episodeId: Long) {
        // Update status to IN_PROGRESS to show visual feedback
        downloadDao.updateDownloadStatus(episodeId, DownloadStatus.IN_PROGRESS)

        // Simulate download progress for now
        // TODO: Replace with actual download implementation using WorkManager
        viewModelScope.launch {
            try {
                // Get episode info for size estimation
                val episode = repository.getEpisodeById(episodeId)
                val estimatedSize = 50_000_000L // 50MB estimate

                // Update with estimated file size
                downloadDao.updateDownloadProgress(
                    episodeId = episodeId,
                    status = DownloadStatus.IN_PROGRESS,
                    downloadedBytes = 0
                )

                // Simulate progress updates
                for (progress in 1..10) {
                    kotlinx.coroutines.delay(500)
                    val downloadedBytes = (estimatedSize * progress / 10)
                    downloadDao.updateDownloadProgress(
                        episodeId = episodeId,
                        status = DownloadStatus.IN_PROGRESS,
                        downloadedBytes = downloadedBytes
                    )
                }

                // Mark as completed
                downloadDao.updateDownloadProgress(
                    episodeId = episodeId,
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = estimatedSize
                )
            } catch (e: Exception) {
                downloadDao.updateDownloadStatus(episodeId, DownloadStatus.FAILED, e.message)
            }
        }
    }
}
