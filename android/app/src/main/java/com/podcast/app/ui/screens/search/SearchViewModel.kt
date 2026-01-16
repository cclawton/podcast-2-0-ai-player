package com.podcast.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val privacyManager: PrivacyManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    val searchResults: StateFlow<List<Podcast>> = _searchResults.asStateFlow()

    private val _trendingPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val trendingPodcasts: StateFlow<List<Podcast>> = _trendingPodcasts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val canSearch: StateFlow<Boolean> = privacyManager.canUseNetwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null

    init {
        loadTrending()
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        _error.value = null

        // Debounced search
        searchJob?.cancel()
        if (newQuery.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300) // Debounce
                performSearch(newQuery)
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    private suspend fun performSearch(searchQuery: String) {
        if (!privacyManager.isFeatureAllowed(NetworkFeature.PODCAST_SEARCH)) {
            _error.value = privacyManager.getOfflineFallbackMessage(NetworkFeature.PODCAST_SEARCH)
            return
        }

        _isLoading.value = true
        _error.value = null

        repository.searchPodcasts(searchQuery)
            .onSuccess { podcasts ->
                _searchResults.value = podcasts
            }
            .onFailure { exception ->
                _error.value = "Search failed: ${exception.message}"
            }

        _isLoading.value = false
    }

    private fun loadTrending() {
        viewModelScope.launch {
            if (!privacyManager.isFeatureAllowed(NetworkFeature.PODCAST_SEARCH)) {
                return@launch
            }

            repository.getTrendingPodcasts(20)
                .onSuccess { podcasts ->
                    _trendingPodcasts.value = podcasts
                }
        }
    }

    fun subscribeToPodcast(podcastIndexId: Long) {
        viewModelScope.launch {
            repository.subscribeToPodcast(podcastIndexId)
                .onSuccess {
                    // Update UI to show subscribed state
                    _searchResults.value = _searchResults.value.map { podcast ->
                        if (podcast.podcastIndexId == podcastIndexId) {
                            podcast.copy(isSubscribed = true)
                        } else {
                            podcast
                        }
                    }
                }
                .onFailure { exception ->
                    _error.value = "Failed to subscribe: ${exception.message}"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
