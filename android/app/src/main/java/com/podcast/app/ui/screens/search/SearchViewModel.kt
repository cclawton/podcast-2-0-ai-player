package com.podcast.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.podcast.app.api.claude.AISearchService
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val privacyManager: PrivacyManager,
    private val aiSearchService: AISearchService
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

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

    private val _showRssDialog = MutableStateFlow(false)
    val showRssDialog: StateFlow<Boolean> = _showRssDialog.asStateFlow()

    private val _rssUrl = MutableStateFlow("")
    val rssUrl: StateFlow<String> = _rssUrl.asStateFlow()

    private val _rssSubscriptionSuccess = MutableStateFlow<Podcast?>(null)
    val rssSubscriptionSuccess: StateFlow<Podcast?> = _rssSubscriptionSuccess.asStateFlow()

    // Subscription confirmation dialog state
    private val _showSubscribeConfirmation = MutableStateFlow(false)
    val showSubscribeConfirmation: StateFlow<Boolean> = _showSubscribeConfirmation.asStateFlow()

    private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

    val canSearch: StateFlow<Boolean> = privacyManager.canUseNetwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null

    // ================================
    // AI Search State (GH#30)
    // ================================
    private val _showAiSearch = MutableStateFlow(false)
    val showAiSearch: StateFlow<Boolean> = _showAiSearch.asStateFlow()

    private val _aiQuery = MutableStateFlow("")
    val aiQuery: StateFlow<String> = _aiQuery.asStateFlow()

    private val _aiSearchResults = MutableStateFlow<List<Podcast>>(emptyList())
    val aiSearchResults: StateFlow<List<Podcast>> = _aiSearchResults.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _aiExplanation = MutableStateFlow<String?>(null)
    val aiExplanation: StateFlow<String?> = _aiExplanation.asStateFlow()

    val isAiAvailable: Boolean
        get() = aiSearchService.isApiKeyConfigured()

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
        DiagnosticLogger.i(TAG, "performSearch: '$searchQuery'")

        val featureAllowed = privacyManager.isFeatureAllowed(NetworkFeature.PODCAST_SEARCH)
        DiagnosticLogger.d(TAG, "Network feature PODCAST_SEARCH allowed: $featureAllowed")

        if (!featureAllowed) {
            val msg = privacyManager.getOfflineFallbackMessage(NetworkFeature.PODCAST_SEARCH)
            DiagnosticLogger.w(TAG, "Search blocked by privacy settings: $msg")
            _error.value = msg
            return
        }

        _isLoading.value = true
        _error.value = null

        DiagnosticLogger.d(TAG, "Calling repository.searchPodcasts...")
        repository.searchPodcasts(searchQuery)
            .onSuccess { podcasts ->
                DiagnosticLogger.i(TAG, "Search success: ${podcasts.size} results")
                _searchResults.value = podcasts
            }
            .onFailure { exception ->
                val errorMsg = exception.message ?: "Search failed"
                DiagnosticLogger.e(TAG, "Search failure: $errorMsg")
                _error.value = errorMsg
            }

        _isLoading.value = false
    }

    private fun loadTrending() {
        viewModelScope.launch {
            DiagnosticLogger.d(TAG, "loadTrending: checking privacy settings...")

            val featureAllowed = privacyManager.isFeatureAllowed(NetworkFeature.PODCAST_SEARCH)
            DiagnosticLogger.d(TAG, "Network feature PODCAST_SEARCH allowed: $featureAllowed")

            if (!featureAllowed) {
                DiagnosticLogger.w(TAG, "Trending blocked by privacy settings")
                return@launch
            }

            DiagnosticLogger.d(TAG, "Calling repository.getTrendingPodcasts...")
            repository.getTrendingPodcasts(20)
                .onSuccess { podcasts ->
                    DiagnosticLogger.i(TAG, "Trending success: ${podcasts.size} results")
                    _trendingPodcasts.value = podcasts
                }
                .onFailure { exception ->
                    DiagnosticLogger.e(TAG, "Trending failure: ${exception.message}")
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

    // ================================
    // Subscription Confirmation Dialog
    // ================================

    fun showSubscribeConfirmation(podcast: Podcast) {
        _selectedPodcast.value = podcast
        _showSubscribeConfirmation.value = true
    }

    fun hideSubscribeConfirmation() {
        _showSubscribeConfirmation.value = false
        _selectedPodcast.value = null
    }

    fun confirmSubscription() {
        _selectedPodcast.value?.let { podcast ->
            subscribeToPodcast(podcast.podcastIndexId)
            hideSubscribeConfirmation()
        }
    }

    // ================================
    // RSS Feed Subscription
    // ================================

    fun showRssDialog() {
        _showRssDialog.value = true
    }

    fun hideRssDialog() {
        _showRssDialog.value = false
        _rssUrl.value = ""
    }

    fun updateRssUrl(url: String) {
        _rssUrl.value = url
    }

    fun subscribeFromRss() {
        val url = _rssUrl.value.trim()
        if (url.isBlank()) {
            _error.value = "Please enter an RSS feed URL"
            return
        }

        viewModelScope.launch {
            if (!privacyManager.isFeatureAllowed(NetworkFeature.FEED_UPDATES)) {
                _error.value = "Network access is disabled. Enable it in settings to add RSS feeds."
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            repository.subscribeFromRssFeed(url)
                .onSuccess { podcast ->
                    _rssSubscriptionSuccess.value = podcast
                    hideRssDialog()
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Failed to subscribe to RSS feed"
                }

            _isLoading.value = false
        }
    }

    fun clearRssSubscriptionSuccess() {
        _rssSubscriptionSuccess.value = null
    }

    // ================================
    // AI Search Methods (GH#30)
    // ================================

    fun toggleAiSearch() {
        _showAiSearch.value = !_showAiSearch.value
        if (!_showAiSearch.value) {
            // Clear AI search state when closing
            _aiQuery.value = ""
            _aiSearchResults.value = emptyList()
            _aiError.value = null
            _aiExplanation.value = null
        }
    }

    fun updateAiQuery(query: String) {
        _aiQuery.value = query
        _aiError.value = null
    }

    fun performAiSearch() {
        val query = _aiQuery.value.trim()
        if (query.isBlank()) {
            _aiError.value = "Please enter a search query"
            return
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiError.value = null
            _aiExplanation.value = null

            when (val result = aiSearchService.search(query)) {
                is AISearchService.AISearchResult.Success -> {
                    DiagnosticLogger.i(TAG, "AI search success: ${result.podcasts.size} results")
                    _aiSearchResults.value = result.podcasts
                    _aiExplanation.value = result.explanation
                }
                is AISearchService.AISearchResult.Error -> {
                    DiagnosticLogger.e(TAG, "AI search error: ${result.message}")
                    _aiError.value = result.message
                }
                is AISearchService.AISearchResult.ApiKeyNotConfigured -> {
                    DiagnosticLogger.w(TAG, "AI search: API key not configured")
                    _aiError.value = "Claude API key not configured. Go to Settings to add your API key."
                }
                is AISearchService.AISearchResult.ClaudeApiDisabled -> {
                    DiagnosticLogger.w(TAG, "AI search: Claude API disabled")
                    _aiError.value = "Claude API is disabled in privacy settings."
                }
            }

            _isAiLoading.value = false
        }
    }

    fun clearAiError() {
        _aiError.value = null
    }
}
