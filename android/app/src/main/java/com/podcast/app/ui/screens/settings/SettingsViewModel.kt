package com.podcast.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.privacy.OperationalMode
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.privacy.PrivacyPreset
import com.podcast.app.privacy.PrivacySettings
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
    private val privacyManager: PrivacyManager
) : ViewModel() {

    val settings: StateFlow<PrivacySettings> = privacyManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrivacySettings())

    private val _operationalMode = MutableStateFlow(OperationalMode.OFFLINE_NO_PERMISSION)
    val operationalMode: StateFlow<OperationalMode> = _operationalMode.asStateFlow()

    val permissionState = privacyManager.permissionState

    init {
        refreshOperationalMode()
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
        }
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

    fun refreshPermissions() {
        privacyManager.refreshPermissions()
        refreshOperationalMode()
    }
}
