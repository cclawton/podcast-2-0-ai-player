package com.podcast.app.ui.screens.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.download.DownloadManager
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlaybackState
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for PodcastFeedScreen (GH#32).
 * Manages podcast preview and episode list for unsubscribed podcasts.
 */
@HiltViewModel
class PodcastFeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
    private val api: PodcastIndexApi,
    private val downloadDao: DownloadDao,
    private val playbackController: IPlaybackController,
    private val downloadManager: DownloadManager,
    private val privacyManager: PrivacyManager
) : ViewModel() {

    companion object {
        private const val TAG = "PodcastFeedViewModel"
    }

    private val podcastIndexId: Long = savedStateHandle.get<String>("podcastIndexId")?.toLongOrNull() ?: 0L

    private val _podcast = MutableStateFlow<Podcast?>(null)
    val podcast: StateFlow<Podcast?> = _podcast.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSubscribing = MutableStateFlow(false)
    val isSubscribing: StateFlow<Boolean> = _isSubscribing.asStateFlow()

    private val _subscriptionSuccess = MutableStateFlow<Long?>(null)
    val subscriptionSuccess: StateFlow<Long?> = _subscriptionSuccess.asStateFlow()

    val downloads: StateFlow<Map<Long, Download>> = downloadDao.getAllDownloadsFlow()
        .map { list -> list.associateBy { it.episodeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentEpisode: StateFlow<Episode?> = playbackController.currentEpisode
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    init {
        loadPodcastFeed()
    }

    private fun loadPodcastFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                if (!privacyManager.isFeatureAllowed(NetworkFeature.PODCAST_SEARCH)) {
                    _error.value = privacyManager.getOfflineFallbackMessage(NetworkFeature.PODCAST_SEARCH)
                    _isLoading.value = false
                    return@launch
                }

                DiagnosticLogger.i(TAG, "Loading podcast feed for ID: $podcastIndexId")

                // Fetch podcast details
                val podcastResponse = api.getPodcastById(podcastIndexId)
                val fetchedPodcast = podcastResponse.feed?.let { feed ->
                    Podcast(
                        podcastIndexId = feed.id,
                        title = feed.title,
                        feedUrl = feed.url,
                        imageUrl = feed.artwork ?: feed.image,
                        description = feed.description,
                        language = feed.language ?: "en",
                        explicit = feed.explicit,
                        author = feed.author,
                        episodeCount = feed.episodeCount,
                        websiteUrl = feed.link,
                        podcastGuid = feed.podcastGuid,
                        isSubscribed = false
                    )
                }

                if (fetchedPodcast == null) {
                    _error.value = "Podcast not found"
                    _isLoading.value = false
                    return@launch
                }

                _podcast.value = fetchedPodcast
                DiagnosticLogger.i(TAG, "Loaded podcast: ${fetchedPodcast.title}")

                // Fetch episodes
                val episodesResponse = api.getEpisodesByFeedId(podcastIndexId, max = 50)
                _episodes.value = episodesResponse.items.map { it.toEpisode(0) }
                DiagnosticLogger.i(TAG, "Loaded ${_episodes.value.size} episodes")

            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Failed to load podcast: ${e.message}")
                _error.value = "Failed to load podcast: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    fun subscribe() {
        viewModelScope.launch {
            _isSubscribing.value = true
            _error.value = null

            repository.subscribeToPodcast(podcastIndexId)
                .onSuccess { podcast ->
                    DiagnosticLogger.i(TAG, "Subscribed to podcast: ${podcast.title}")
                    _subscriptionSuccess.value = podcast.id
                    _podcast.value = podcast
                }
                .onFailure { e ->
                    DiagnosticLogger.e(TAG, "Failed to subscribe: ${e.message}")
                    _error.value = "Failed to subscribe: ${e.message}"
                }

            _isSubscribing.value = false
        }
    }

    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            val podcast = _podcast.value ?: return@launch

            // For unsubscribed podcasts, we need to subscribe first to download
            if (!podcast.isSubscribed) {
                _error.value = "Subscribe to enable downloading"
                return@launch
            }

            try {
                downloadManager.downloadEpisode(episode)
                DiagnosticLogger.i(TAG, "Download enqueued: ${episode.title}")
            } catch (e: Exception) {
                _error.value = "Failed to start download: ${e.message}"
            }
        }
    }

    fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            val podcast = _podcast.value ?: return@launch

            // Streaming works without subscription if network is available
            if (!privacyManager.isFeatureAllowed(NetworkFeature.AUDIO_STREAMING)) {
                _error.value = "Audio streaming is disabled. Subscribe and download to listen offline."
                return@launch
            }

            try {
                // For feed episodes, we need to save the episode first to get a valid ID
                // For now, we'll stream directly using episode ID 0 (will need proper episode saving)
                playbackController.playEpisode(episode.id, 0)
                DiagnosticLogger.i(TAG, "Playing episode: ${episode.title}")
            } catch (e: Exception) {
                _error.value = "Failed to play episode: ${e.message}"
            }
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSubscriptionSuccess() {
        _subscriptionSuccess.value = null
    }

    fun refresh() {
        loadPodcastFeed()
    }

    private fun EpisodeItem.toEpisode(podcastId: Long) = Episode(
        episodeIndexId = id,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = enclosureUrl,
        audioDuration = duration,
        audioSize = enclosureLength,
        audioType = enclosureType ?: "audio/mpeg",
        publishedAt = datePublished?.let { it * 1000L },
        episodeGuid = guid,
        explicit = explicit == 1,
        link = link,
        imageUrl = image ?: feedImage,
        transcriptUrl = transcripts?.firstOrNull()?.url,
        transcriptType = transcripts?.firstOrNull()?.type,
        seasonNumber = season,
        episodeNumber = episode
    )
}
