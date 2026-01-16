package com.podcast.app.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore extension for privacy settings.
 */
private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacy_settings"
)

/**
 * Repository for persisting privacy settings using DataStore.
 *
 * Uses DataStore (not SharedPreferences) for:
 * - Thread-safe access
 * - Flow-based reactive updates
 * - Corruption protection
 * - Type safety
 */
@Singleton
class PrivacyRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferenceKeys {
        val NETWORK_ENABLED = booleanPreferencesKey("network_enabled")
        val ALLOW_PODCAST_SEARCH = booleanPreferencesKey("allow_podcast_search")
        val ALLOW_FEED_UPDATES = booleanPreferencesKey("allow_feed_updates")
        val ALLOW_AUDIO_STREAMING = booleanPreferencesKey("allow_audio_streaming")
        val ALLOW_IMAGE_LOADING = booleanPreferencesKey("allow_image_loading")
        val ALLOW_BACKGROUND_SYNC = booleanPreferencesKey("allow_background_sync")
        val ALLOW_OLLAMA_INTEGRATION = booleanPreferencesKey("allow_ollama_integration")
        val ALLOW_CLAUDE_API = booleanPreferencesKey("allow_claude_api")
        val ALLOW_CHAPTER_IMAGES = booleanPreferencesKey("allow_chapter_images")
        val ALLOW_TRANSCRIPTS = booleanPreferencesKey("allow_transcripts")
        val AUTO_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("auto_download_wifi_only")
        val MAX_AUTO_DOWNLOAD_SIZE = longPreferencesKey("max_auto_download_size")
        val DOWNLOAD_RETENTION_DAYS = intPreferencesKey("download_retention_days")
        val STORE_SEARCH_HISTORY = booleanPreferencesKey("store_search_history")
        val STORE_PLAYBACK_HISTORY = booleanPreferencesKey("store_playback_history")
    }

    /**
     * Flow of current privacy settings.
     * Emits updates whenever any setting changes.
     */
    val settings: Flow<PrivacySettings> = context.privacyDataStore.data.map { prefs ->
        PrivacySettings(
            networkEnabled = prefs[PreferenceKeys.NETWORK_ENABLED] ?: true,
            allowPodcastSearch = prefs[PreferenceKeys.ALLOW_PODCAST_SEARCH] ?: true,
            allowFeedUpdates = prefs[PreferenceKeys.ALLOW_FEED_UPDATES] ?: true,
            allowAudioStreaming = prefs[PreferenceKeys.ALLOW_AUDIO_STREAMING] ?: true,
            allowImageLoading = prefs[PreferenceKeys.ALLOW_IMAGE_LOADING] ?: true,
            allowBackgroundSync = prefs[PreferenceKeys.ALLOW_BACKGROUND_SYNC] ?: false,
            allowOllamaIntegration = prefs[PreferenceKeys.ALLOW_OLLAMA_INTEGRATION] ?: false,
            allowClaudeApi = prefs[PreferenceKeys.ALLOW_CLAUDE_API] ?: false,
            allowChapterImages = prefs[PreferenceKeys.ALLOW_CHAPTER_IMAGES] ?: true,
            allowTranscripts = prefs[PreferenceKeys.ALLOW_TRANSCRIPTS] ?: true,
            autoDownloadOnWifiOnly = prefs[PreferenceKeys.AUTO_DOWNLOAD_WIFI_ONLY] ?: true,
            maxAutoDownloadSize = prefs[PreferenceKeys.MAX_AUTO_DOWNLOAD_SIZE]
                ?: (100 * 1024 * 1024L),
            downloadRetentionDays = prefs[PreferenceKeys.DOWNLOAD_RETENTION_DAYS] ?: 30,
            storeSearchHistory = prefs[PreferenceKeys.STORE_SEARCH_HISTORY] ?: true,
            storePlaybackHistory = prefs[PreferenceKeys.STORE_PLAYBACK_HISTORY] ?: true
        )
    }

    /**
     * Update all privacy settings.
     */
    suspend fun updateSettings(newSettings: PrivacySettings) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.NETWORK_ENABLED] = newSettings.networkEnabled
            prefs[PreferenceKeys.ALLOW_PODCAST_SEARCH] = newSettings.allowPodcastSearch
            prefs[PreferenceKeys.ALLOW_FEED_UPDATES] = newSettings.allowFeedUpdates
            prefs[PreferenceKeys.ALLOW_AUDIO_STREAMING] = newSettings.allowAudioStreaming
            prefs[PreferenceKeys.ALLOW_IMAGE_LOADING] = newSettings.allowImageLoading
            prefs[PreferenceKeys.ALLOW_BACKGROUND_SYNC] = newSettings.allowBackgroundSync
            prefs[PreferenceKeys.ALLOW_OLLAMA_INTEGRATION] = newSettings.allowOllamaIntegration
            prefs[PreferenceKeys.ALLOW_CLAUDE_API] = newSettings.allowClaudeApi
            prefs[PreferenceKeys.ALLOW_CHAPTER_IMAGES] = newSettings.allowChapterImages
            prefs[PreferenceKeys.ALLOW_TRANSCRIPTS] = newSettings.allowTranscripts
            prefs[PreferenceKeys.AUTO_DOWNLOAD_WIFI_ONLY] = newSettings.autoDownloadOnWifiOnly
            prefs[PreferenceKeys.MAX_AUTO_DOWNLOAD_SIZE] = newSettings.maxAutoDownloadSize
            prefs[PreferenceKeys.DOWNLOAD_RETENTION_DAYS] = newSettings.downloadRetentionDays
            prefs[PreferenceKeys.STORE_SEARCH_HISTORY] = newSettings.storeSearchHistory
            prefs[PreferenceKeys.STORE_PLAYBACK_HISTORY] = newSettings.storePlaybackHistory
        }
    }

    /**
     * Update a single boolean setting.
     */
    suspend fun updateNetworkEnabled(enabled: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.NETWORK_ENABLED] = enabled
        }
    }

    suspend fun updateAllowPodcastSearch(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_PODCAST_SEARCH] = allowed
        }
    }

    suspend fun updateAllowFeedUpdates(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_FEED_UPDATES] = allowed
        }
    }

    suspend fun updateAllowAudioStreaming(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_AUDIO_STREAMING] = allowed
        }
    }

    suspend fun updateAllowImageLoading(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_IMAGE_LOADING] = allowed
        }
    }

    suspend fun updateAllowBackgroundSync(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_BACKGROUND_SYNC] = allowed
        }
    }

    suspend fun updateAllowOllamaIntegration(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_OLLAMA_INTEGRATION] = allowed
        }
    }

    suspend fun updateAllowClaudeApi(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.ALLOW_CLAUDE_API] = allowed
        }
    }

    suspend fun updateAutoDownloadWifiOnly(wifiOnly: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.AUTO_DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun updateMaxAutoDownloadSize(sizeBytes: Long) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.MAX_AUTO_DOWNLOAD_SIZE] = sizeBytes
        }
    }

    suspend fun updateDownloadRetentionDays(days: Int) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.DOWNLOAD_RETENTION_DAYS] = days
        }
    }

    suspend fun updateStoreSearchHistory(store: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.STORE_SEARCH_HISTORY] = store
        }
    }

    suspend fun updateStorePlaybackHistory(store: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PreferenceKeys.STORE_PLAYBACK_HISTORY] = store
        }
    }

    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults() {
        updateSettings(PrivacySettings.DEFAULT)
    }

    /**
     * Apply a privacy preset.
     */
    suspend fun applyPreset(preset: PrivacyPreset) {
        val settings = when (preset) {
            PrivacyPreset.MAXIMUM_PRIVACY -> PrivacySettings.MAXIMUM_PRIVACY
            PrivacyPreset.BALANCED -> PrivacySettings.BALANCED
            PrivacyPreset.FULL_FEATURES -> PrivacySettings()
        }
        updateSettings(settings)
    }
}
