package com.podcast.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.api.claude.ClaudeApiClient
import com.podcast.app.api.claude.ClaudeApiKeyManager
import com.podcast.app.api.claude.LLMTestResult
import com.podcast.app.api.claude.QueryResponse
import com.podcast.app.privacy.OperationalMode
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.privacy.PrivacyPreset
import com.podcast.app.privacy.PrivacySettings
import com.podcast.app.sync.SyncInterval
import com.podcast.app.sync.SyncManager
import com.podcast.app.sync.SyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val privacyManager: PrivacyManager,
    private val syncManager: SyncManager,
    private val claudeApiKeyManager: ClaudeApiKeyManager,
    private val claudeApiClient: ClaudeApiClient
) : ViewModel() {

    val settings: StateFlow<PrivacySettings> = privacyManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrivacySettings())

    val syncSettings: StateFlow<SyncSettings> = syncManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncSettings())

    private val _operationalMode = MutableStateFlow(OperationalMode.OFFLINE_NO_PERMISSION)
    val operationalMode: StateFlow<OperationalMode> = _operationalMode.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val permissionState = privacyManager.permissionState

    // Claude API state
    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<Boolean?>(null)
    val connectionTestResult: StateFlow<Boolean?> = _connectionTestResult.asStateFlow()

    private val _connectionTestMessage = MutableStateFlow<String?>(null)
    val connectionTestMessage: StateFlow<String?> = _connectionTestMessage.asStateFlow()

    // LLM Test state (GH#31)
    private val _llmTestResult = MutableStateFlow<LLMTestResult?>(null)
    val llmTestResult: StateFlow<LLMTestResult?> = _llmTestResult.asStateFlow()

    private val _isRunningLlmTest = MutableStateFlow(false)
    val isRunningLlmTest: StateFlow<Boolean> = _isRunningLlmTest.asStateFlow()

    // GH#34: API key save state
    private val _isApiKeySaved = MutableStateFlow(false)
    val isApiKeySaved: StateFlow<Boolean> = _isApiKeySaved.asStateFlow()

    private val _isEditingApiKey = MutableStateFlow(false)
    val isEditingApiKey: StateFlow<Boolean> = _isEditingApiKey.asStateFlow()

    init {
        refreshOperationalMode()
        // Initialize sync manager
        syncManager.initialize()
        // Load Claude API key and check saved state
        loadClaudeApiKey()
    }

    private fun loadClaudeApiKey() {
        val savedKey = claudeApiKeyManager.getApiKey() ?: ""
        _claudeApiKey.value = savedKey
        _isApiKeySaved.value = claudeApiKeyManager.hasApiKey()
        _isEditingApiKey.value = false
    }

    private fun refreshOperationalMode() {
        viewModelScope.launch {
            _operationalMode.value = privacyManager.getOperationalMode()
        }
    }

    fun applyPreset(preset: PrivacyPreset) {
        viewModelScope.launch {
            privacyManager.applyPreset(preset)
            refreshOperationalMode()
        }
    }

    fun updateNetworkEnabled(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(networkEnabled = enabled) }
            refreshOperationalMode()
        }
    }

    fun updatePodcastSearch(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowPodcastSearch = enabled) }
        }
    }

    fun updateFeedUpdates(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowFeedUpdates = enabled) }
        }
    }

    fun updateAudioStreaming(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowAudioStreaming = enabled) }
        }
    }

    fun updateImageLoading(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowImageLoading = enabled) }
        }
    }

    fun updateBackgroundSync(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowBackgroundSync = enabled) }
        }
    }

    fun updateOllamaIntegration(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowOllamaIntegration = enabled) }
        }
    }

    fun updateClaudeApi(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(allowClaudeApi = enabled) }
            if (!enabled) {
                // Clear API key when disabling
                clearClaudeApiKey()
            }
        }
    }

    /**
     * Update the API key field value (but do not save to storage yet).
     * GH#34: Key is only saved to storage when saveApiKey() is called.
     */
    fun updateClaudeApiKey(key: String) {
        _claudeApiKey.value = key
        _connectionTestResult.value = null
        _connectionTestMessage.value = null
    }

    /**
     * Save the API key to secure storage.
     * GH#34: Explicit save action required.
     */
    fun saveApiKey() {
        val key = _claudeApiKey.value
        if (key.isNotBlank()) {
            claudeApiKeyManager.saveApiKey(key)
            _isApiKeySaved.value = true
            _isEditingApiKey.value = false
        }
    }

    /**
     * Enter edit mode for the API key.
     * GH#34: Allows changing a previously saved key.
     */
    fun startEditingApiKey() {
        _isEditingApiKey.value = true
    }

    fun clearClaudeApiKey() {
        _claudeApiKey.value = ""
        _connectionTestResult.value = null
        _connectionTestMessage.value = null
        _isApiKeySaved.value = false
        _isEditingApiKey.value = false
        claudeApiKeyManager.clearApiKey()
    }

    fun testClaudeConnection() {
        val apiKey = _claudeApiKey.value
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            _connectionTestMessage.value = null

            val result = claudeApiClient.testConnection(apiKey)
            result.onSuccess { testResult ->
                _connectionTestResult.value = testResult.success
                _connectionTestMessage.value = testResult.message
            }.onFailure { e ->
                _connectionTestResult.value = false
                _connectionTestMessage.value = e.message ?: "Connection failed"
            }

            _isTestingConnection.value = false
        }
    }

    /**
     * Test Claude API with simple natural language queries (GH#31).
     * Runs "What color is the sun?" and "Why is the sky blue?" to demonstrate LLM capability.
     */
    fun testClaudeWithQueries() {
        val apiKey = _claudeApiKey.value
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            _isRunningLlmTest.value = true
            _llmTestResult.value = null

            val result = claudeApiClient.testWithQueries(apiKey)
            result.onSuccess { testResult ->
                _llmTestResult.value = testResult
                // Also update connection status
                _connectionTestResult.value = testResult.connectionSuccess
                _connectionTestMessage.value = testResult.connectionMessage
            }.onFailure { e ->
                _llmTestResult.value = LLMTestResult(
                    connectionSuccess = false,
                    connectionMessage = e.message ?: "Test failed",
                    queryResponses = emptyList()
                )
            }

            _isRunningLlmTest.value = false
        }
    }

    fun clearLlmTestResult() {
        _llmTestResult.value = null
    }

    fun updateAutoDownloadOnWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(autoDownloadOnWifiOnly = enabled) }
        }
    }

    fun updateStoreSearchHistory(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(storeSearchHistory = enabled) }
        }
    }

    fun updateStorePlaybackHistory(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(storePlaybackHistory = enabled) }
        }
    }

    fun updateAutoDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(autoDeleteEnabled = enabled) }
        }
    }

    fun updateAutoDeleteOnlyPlayed(enabled: Boolean) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(autoDeleteOnlyPlayed = enabled) }
        }
    }

    fun updateRetentionDays(days: Int) {
        viewModelScope.launch {
            privacyManager.updateSettings { it.copy(downloadRetentionDays = days) }
        }
    }

    fun refreshPermissions() {
        privacyManager.refreshPermissions()
        refreshOperationalMode()
    }

    // Sync settings methods

    fun updateSyncInterval(interval: SyncInterval) {
        viewModelScope.launch {
            syncManager.setSyncInterval(interval)
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            syncManager.setWifiOnly(wifiOnly)
        }
    }

    fun updateSyncNotifications(enabled: Boolean) {
        viewModelScope.launch {
            syncManager.setNotifyNewEpisodes(enabled)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null

            val result = syncManager.syncNow()

            _isSyncing.value = false
            _syncMessage.value = if (result.success) {
                if (result.newEpisodesCount > 0) {
                    "Found ${result.newEpisodesCount} new episode${if (result.newEpisodesCount > 1) "s" else ""}"
                } else {
                    "All feeds up to date"
                }
            } else {
                result.error ?: "Sync failed"
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun formatLastSyncTime(timestamp: Long): String {
        if (timestamp == 0L) return "Never"

        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / (1000 * 60)

        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}
