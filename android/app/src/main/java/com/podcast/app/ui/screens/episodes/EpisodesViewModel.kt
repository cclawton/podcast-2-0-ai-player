package com.podcast.app.ui.screens.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.dao.PlaybackProgressDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.PlaybackProgress
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.download.DownloadManager
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val downloadDao: DownloadDao,
    private val progressDao: PlaybackProgressDao,
    private val playbackController: IPlaybackController,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val podcastId: Long = savedStateHandle.get<String>("podcastId")?.toLongOrNull() ?: 0L

    private val _podcast = MutableStateFlow<Podcast?>(null)
    val podcast: StateFlow<Podcast?> = _podcast.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val episodes: StateFlow<List<Episode>> = repository.getEpisodes(podcastId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<Map<Long, Download>> = downloadDao.getAllDownloadsFlow()
        .map { list -> list.associateBy { it.episodeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val progress: StateFlow<Map<Long, PlaybackProgress>> = progressDao.getProgressForPodcastFlow(podcastId)
        .map { list -> list.associateBy { it.episodeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentEpisode: StateFlow<Episode?> = playbackController.currentEpisode

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    val autoDownloadEnabled: StateFlow<Boolean> = _podcast
        .map { it?.autoDownload ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadPodcast()
    }

    private fun loadPodcast() {
        viewModelScope.launch {
            _podcast.value = podcastDao.getPodcastById(podcastId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            repository.refreshEpisodes(podcastId)
                .onFailure { exception ->
                    _error.value = "Failed to refresh: ${exception.message}"
                }

            _isRefreshing.value = false
        }
    }

    fun playEpisode(episodeId: Long) {
        viewModelScope.launch {
            playbackController.playEpisode(episodeId)
        }
    }

    /**
     * Download an episode using the DownloadManager.
     *
     * Behavior depends on current download status:
     * - COMPLETED: Delete the downloaded file
     * - IN_PROGRESS/PENDING: Cancel the download
     * - FAILED/CANCELLED/null: Start a new download
     */
    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            downloadManager.downloadEpisode(episode)
        }
    }

    fun unsubscribe() {
        viewModelScope.launch {
            repository.unsubscribeFromPodcast(podcastId)
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

    fun clearError() {
        _error.value = null
    }

    fun toggleAutoDownload() {
        viewModelScope.launch {
            val current = _podcast.value ?: return@launch
            val newValue = !current.autoDownload
            podcastDao.updateAutoDownload(podcastId, newValue)
            // Reload podcast to update state
            _podcast.value = podcastDao.getPodcastById(podcastId)
        }
    }
}
