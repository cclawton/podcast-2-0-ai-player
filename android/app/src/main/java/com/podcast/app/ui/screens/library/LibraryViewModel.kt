package com.podcast.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.playback.PlaybackController
import com.podcast.app.playback.PlaybackState
import com.podcast.app.privacy.PrivacyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playbackController: PlaybackController,
    private val privacyManager: PrivacyManager
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val subscribedPodcasts: StateFlow<List<Podcast>> = repository.getSubscribedPodcasts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentEpisodes: StateFlow<List<Episode>> = repository.getRecentEpisodes(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
