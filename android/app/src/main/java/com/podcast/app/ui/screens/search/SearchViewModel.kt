package com.podcast.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.api.claude.AISearchService
import com.podcast.app.data.local.dao.DownloadDao
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.download.DownloadManager
import com.podcast.app.playback.PlaybackController
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
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val privacyManager: PrivacyManager,
    private val aiSearchService: AISearchService,
    private val downloadManager: DownloadManager,
    private val downloadDao: DownloadDao,
    private val playbackController: PlaybackController
) : ViewModel() {

    // GH#42: Expose playback state for MiniPlayer on SearchScreen
    val currentEpisode: StateFlow<Episode?> = playbackController.currentEpisode
    val playbackState: StateFlow<com.podcast.app.playback.PlaybackState> = playbackController.playbackState

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun skipNext() {
        viewModelScope.launch {
            playbackController.playNext()
        }
    }

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
    // AI Search State (GH#30, GH#35)
    // ================================
    private val _showAiSearch = MutableStateFlow(false)
    val showAiSearch: StateFlow<Boolean> = _showAiSearch.asStateFlow()

    private val _aiQuery = MutableStateFlow("")
    val aiQuery: StateFlow<String> = _aiQuery.asStateFlow()

    private val _aiSearchResults = MutableStateFlow<List<Podcast>>(emptyList())
    val aiSearchResults: StateFlow<List<Podcast>> = _aiSearchResults.asStateFlow()

    private val _aiEpisodeResults = MutableStateFlow<List<AISearchService.AISearchEpisode>>(emptyList())
    val aiEpisodeResults: StateFlow<List<AISearchService.AISearchEpisode>> = _aiEpisodeResults.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _aiExplanation = MutableStateFlow<String?>(null)
    val aiExplanation: StateFlow<String?> = _aiExplanation.asStateFlow()

    // GH#36: Search type for conditional UI display (byperson/bytitle/byterm)
    private val _aiSearchType = MutableStateFlow<String?>(null)
    val aiSearchType: StateFlow<String?> = _aiSearchType.asStateFlow()

    // GH#33: Track if AI is fully configured (API key + Claude API enabled in settings)
    private val _isAiConfigured = MutableStateFlow(false)
    val isAiConfigured: StateFlow<Boolean> = _isAiConfigured.asStateFlow()

    val isAiAvailable: Boolean
        get() = aiSearchService.isApiKeyConfigured()

    // ================================
    // GH#38: Episode Download State (AI Search Download)
    // ================================

    /**
     * Map of episode index ID -> Download state.
     * Tracks download progress for episodes in AI search results.
     */
    private val _episodeDownloadStates = MutableStateFlow<Map<Long, Download>>(emptyMap())
    val episodeDownloadStates: StateFlow<Map<Long, Download>> = _episodeDownloadStates.asStateFlow()

    /**
     * Map of episode index ID -> local database Episode ID.
     * Used to track which AI search episodes have been saved to local DB.
     */
    private val _savedEpisodeIds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val savedEpisodeIds: StateFlow<Map<Long, Long>> = _savedEpisodeIds.asStateFlow()

    init {
        loadTrending()
        initializeAiSearchState()
        observeDownloadStates()
    }

    /**
     * GH#38: Observe download states for all episodes.
     * Updates UI when download progress changes.
     */
    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadDao.getAllDownloadsFlow().collect { downloads ->
                // Create map of episode ID -> Download
                val downloadMap = downloads.associateBy { it.episodeId }

                // Match against saved episode IDs to update AI search episode states
                val savedIds = _savedEpisodeIds.value
                val aiEpisodeDownloadStates = mutableMapOf<Long, Download>()

                savedIds.forEach { (episodeIndexId, localEpisodeId) ->
                    downloadMap[localEpisodeId]?.let { download ->
                        aiEpisodeDownloadStates[episodeIndexId] = download
                    }
                }

                _episodeDownloadStates.value = aiEpisodeDownloadStates
            }
        }
    }

    /**
     * GH#33: Initialize AI search state based on configuration.
     * Auto-show AI search field when Claude API is enabled AND API key is configured.
     */
    private fun initializeAiSearchState() {
        viewModelScope.launch {
            // Check if AI is fully configured (API key present + Claude API allowed in privacy settings)
            val isConfigured = aiSearchService.isAvailable()
            _isAiConfigured.value = isConfigured

            // Auto-show AI search field if fully configured
            if (isConfigured) {
                DiagnosticLogger.i(TAG, "AI search auto-enabled: Claude API configured and enabled")
                _showAiSearch.value = true
            }
        }
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
                    // Also update AI search results
                    _aiSearchResults.value = _aiSearchResults.value.map { podcast ->
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
    // AI Search Methods (GH#30, GH#35)
    // ================================

    fun toggleAiSearch() {
        _showAiSearch.value = !_showAiSearch.value
        if (!_showAiSearch.value) {
            // Clear AI search state when closing
            clearAiSearchResults()
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
                    DiagnosticLogger.i(TAG, "AI search success: type=${result.searchType}, ${result.podcasts.size} podcasts, ${result.episodes.size} episodes")
                    _aiSearchResults.value = result.podcasts
                    _aiEpisodeResults.value = result.episodes
                    _aiExplanation.value = result.explanation
                    _aiSearchType.value = result.searchType  // GH#36: Set search type for UI
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

    fun clearAiSearchResults() {
        _aiQuery.value = ""
        _aiSearchResults.value = emptyList()
        _aiEpisodeResults.value = emptyList()
        _aiError.value = null
        _aiExplanation.value = null
        _aiSearchType.value = null  // GH#36: Clear search type
    }

    fun clearAiError() {
        _aiError.value = null
    }

    // ================================
    // GH#38: Episode Download Actions
    // ================================

    /**
     * Download an episode from AI search results.
     * Saves the podcast and episode to local DB, then starts download.
     *
     * @param episode The AI search episode to download
     */
    fun downloadAiSearchEpisode(episode: AISearchService.AISearchEpisode) {
        viewModelScope.launch {
            DiagnosticLogger.i(TAG, "downloadAiSearchEpisode: ${episode.title}")

            // First, save the episode to local DB
            val result = repository.saveEpisodeForDownload(
                podcastIndexId = episode.podcastId,
                podcastTitle = episode.podcastTitle,
                podcastImageUrl = episode.podcastImageUrl,
                podcastFeedUrl = null,  // Not available from AI search
                episodeIndexId = episode.id,
                episodeTitle = episode.title,
                episodeDescription = episode.description,
                episodeAudioUrl = episode.audioUrl,
                episodeDuration = episode.audioDuration,
                episodePublishedAt = episode.publishedAt,
                episodeImageUrl = episode.imageUrl
            )

            result.onSuccess { savedEpisode ->
                // Track the mapping between index ID and local DB ID
                _savedEpisodeIds.value = _savedEpisodeIds.value + (episode.id to savedEpisode.id)

                // Start the download
                downloadManager.downloadEpisode(savedEpisode)
                DiagnosticLogger.i(TAG, "Started download for episode: ${savedEpisode.id}")
            }.onFailure { error ->
                DiagnosticLogger.e(TAG, "Failed to save episode for download: ${error.message}")
                _aiError.value = "Failed to start download: ${error.message}"
            }
        }
    }

    /**
     * Handle download button click for AI search episode.
     * Behavior depends on current download state:
     * - Not started: Start download
     * - In progress/pending: Cancel download
     * - Completed: Delete download
     * - Failed: Retry download
     */
    fun onAiEpisodeDownloadClick(episode: AISearchService.AISearchEpisode) {
        val downloadState = _episodeDownloadStates.value[episode.id]
        val savedEpisodeId = _savedEpisodeIds.value[episode.id]

        viewModelScope.launch {
            when (downloadState?.status) {
                DownloadStatus.COMPLETED -> {
                    // Delete the download
                    savedEpisodeId?.let { downloadManager.deleteDownload(it) }
                }
                DownloadStatus.IN_PROGRESS, DownloadStatus.PENDING -> {
                    // Cancel the download
                    savedEpisodeId?.let { downloadManager.cancelDownload(it) }
                }
                DownloadStatus.FAILED -> {
                    // Retry the download
                    savedEpisodeId?.let { downloadManager.retryDownload(it) }
                }
                DownloadStatus.CANCELLED, null -> {
                    // Start a new download
                    downloadAiSearchEpisode(episode)
                }
            }
        }
    }

    /**
     * Get the local file path for a downloaded episode, if available.
     * Used for playing downloaded episodes from search results.
     */
    suspend fun getDownloadedFilePath(episodeIndexId: Long): String? {
        val savedEpisodeId = _savedEpisodeIds.value[episodeIndexId] ?: return null
        return downloadManager.getLocalFilePath(savedEpisodeId)
    }

    /**
     * Get the local episode ID for an AI search episode, if saved.
     */
    fun getLocalEpisodeId(episodeIndexId: Long): Long? {
        return _savedEpisodeIds.value[episodeIndexId]
    }

    /**
     * GH#38: Play an episode from AI search results.
     * Saves the episode to local DB if not already saved, then starts playback.
     */
    fun playAiSearchEpisode(episode: AISearchService.AISearchEpisode) {
        viewModelScope.launch {
            DiagnosticLogger.i(TAG, "playAiSearchEpisode: ${episode.title}")

            // Check if episode is already saved locally
            var localEpisodeId = _savedEpisodeIds.value[episode.id]

            if (localEpisodeId == null) {
                // Save the episode to local DB first
                val result = repository.saveEpisodeForDownload(
                    podcastIndexId = episode.podcastId,
                    podcastTitle = episode.podcastTitle,
                    podcastImageUrl = episode.podcastImageUrl,
                    podcastFeedUrl = null,
                    episodeIndexId = episode.id,
                    episodeTitle = episode.title,
                    episodeDescription = episode.description,
                    episodeAudioUrl = episode.audioUrl,
                    episodeDuration = episode.audioDuration,
                    episodePublishedAt = episode.publishedAt,
                    episodeImageUrl = episode.imageUrl
                )

                result.onSuccess { savedEpisode ->
                    localEpisodeId = savedEpisode.id
                    _savedEpisodeIds.value = _savedEpisodeIds.value + (episode.id to savedEpisode.id)
                }.onFailure { error ->
                    DiagnosticLogger.e(TAG, "Failed to save episode for playback: ${error.message}")
                    _aiError.value = "Failed to play episode: ${error.message}"
                    return@launch
                }
            }

            // Start playback
            localEpisodeId?.let { episodeId ->
                playbackController.playEpisode(episodeId, 0)
                DiagnosticLogger.i(TAG, "Started playback for episode: $episodeId")
            }
        }
    }
}
