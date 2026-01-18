package com.podcast.app.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages privacy and permissions for GrapheneOS optimization.
 *
 * Key responsibilities:
 * - Track permission states
 * - Provide offline fallbacks for all features
 * - Handle network permission denial gracefully
 * - Gate features based on user preferences and permissions
 *
 * GrapheneOS Considerations:
 * - INTERNET permission may be revoked at runtime
 * - Network access toggles in GrapheneOS settings
 * - Must check actual network availability, not just permission
 */
@Singleton
class PrivacyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privacyRepository: PrivacyRepository,
    private val networkStateMonitor: NetworkStateMonitor
) {
    private val _permissionState = MutableStateFlow(checkPermissions())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    /**
     * Current privacy settings.
     */
    val settings: Flow<PrivacySettings> = privacyRepository.settings

    /**
     * Combined state: can we perform network operations?
     */
    val canUseNetwork: Flow<Boolean> = combine(
        settings,
        networkStateMonitor.isNetworkAvailable
    ) { settings, networkAvailable ->
        settings.networkEnabled && networkAvailable && hasInternetPermission()
    }

    /**
     * Check if INTERNET permission is granted.
     * On GrapheneOS, this can be revoked even after installation.
     */
    fun hasInternetPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if RECORD_AUDIO permission is granted (for voice commands).
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if foreground service permission is granted.
     */
    fun hasForegroundServicePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted.
     * This is a runtime permission on Android 13+ (API 33+).
     */
    fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android 13
        }
    }

    /**
     * Check all permissions and return current state.
     */
    private fun checkPermissions(): PermissionState {
        return PermissionState(
            hasInternet = hasInternetPermission(),
            hasRecordAudio = hasRecordAudioPermission(),
            hasForegroundService = hasForegroundServicePermission(),
            hasPostNotifications = hasPostNotificationsPermission()
        )
    }

    /**
     * Refresh permission state. Call after permission changes.
     */
    fun refreshPermissions() {
        _permissionState.value = checkPermissions()
    }

    /**
     * Check if a specific network feature is allowed.
     * Combines user settings, permissions, and network availability.
     */
    suspend fun isFeatureAllowed(feature: NetworkFeature): Boolean {
        val currentSettings = settings.first()
        val networkAvailable = networkStateMonitor.isNetworkAvailable.first()
        val hasPermission = hasInternetPermission()

        // If network not available or no permission, feature is not allowed
        if (!networkAvailable || !hasPermission) {
            return false
        }

        // Check master toggle
        if (!currentSettings.networkEnabled) {
            return false
        }

        // Check feature-specific toggle
        return when (feature) {
            NetworkFeature.PODCAST_SEARCH -> currentSettings.allowPodcastSearch
            NetworkFeature.FEED_UPDATES -> currentSettings.allowFeedUpdates
            NetworkFeature.AUDIO_STREAMING -> currentSettings.allowAudioStreaming
            NetworkFeature.IMAGE_LOADING -> currentSettings.allowImageLoading
            NetworkFeature.BACKGROUND_SYNC -> currentSettings.allowBackgroundSync
            NetworkFeature.TRANSCRIPT_FETCH -> currentSettings.allowTranscripts
            NetworkFeature.CHAPTER_IMAGES -> currentSettings.allowChapterImages
            NetworkFeature.OLLAMA_LLM -> currentSettings.allowOllamaIntegration
            NetworkFeature.CLAUDE_API -> currentSettings.allowClaudeApi
        }
    }

    /**
     * Get the current operational mode based on permissions and settings.
     */
    suspend fun getOperationalMode(): OperationalMode {
        val currentSettings = settings.first()
        val networkAvailable = networkStateMonitor.isNetworkAvailable.first()
        val hasPermission = hasInternetPermission()

        return when {
            !hasPermission -> OperationalMode.OFFLINE_NO_PERMISSION
            !networkAvailable -> OperationalMode.OFFLINE_NO_NETWORK
            !currentSettings.networkEnabled -> OperationalMode.OFFLINE_USER_CHOICE
            currentSettings.isAnyNetworkAllowed() -> OperationalMode.ONLINE_LIMITED
            else -> OperationalMode.ONLINE_FULL
        }
    }

    /**
     * Update privacy settings.
     */
    suspend fun updateSettings(update: (PrivacySettings) -> PrivacySettings) {
        val current = settings.first()
        privacyRepository.updateSettings(update(current))
    }

    /**
     * Apply a preset privacy profile.
     */
    suspend fun applyPreset(preset: PrivacyPreset) {
        val newSettings = when (preset) {
            PrivacyPreset.MAXIMUM_PRIVACY -> PrivacySettings.MAXIMUM_PRIVACY
            PrivacyPreset.BALANCED -> PrivacySettings.BALANCED
            PrivacyPreset.FULL_FEATURES -> PrivacySettings()
        }
        privacyRepository.updateSettings(newSettings)
    }

    /**
     * Get list of required permissions with rationales.
     */
    fun getPermissionRequirements(): List<PermissionRequirement> {
        return listOf(
            PermissionRequirement(
                permission = Manifest.permission.INTERNET,
                isRequired = false, // App works offline
                rationale = "Access the internet to search podcasts, download episodes, and stream audio. " +
                    "This permission is OPTIONAL - the app works fully offline with downloaded content.",
                features = listOf(
                    "Search and discover new podcasts",
                    "Download new episodes",
                    "Stream audio",
                    "Update feed information"
                )
            ),
            PermissionRequirement(
                permission = Manifest.permission.FOREGROUND_SERVICE,
                isRequired = true,
                rationale = "Continue audio playback when the app is in the background. " +
                    "This is required for uninterrupted listening.",
                features = listOf(
                    "Background audio playback",
                    "Media notification controls"
                )
            ),
            PermissionRequirement(
                permission = Manifest.permission.RECORD_AUDIO,
                isRequired = false,
                rationale = "Use voice commands to control playback. " +
                    "Voice processing happens on-device using pattern matching.",
                features = listOf(
                    "Voice commands: 'Play', 'Pause', 'Skip'",
                    "Voice search (offline pattern matching)"
                )
            ),
            PermissionRequirement(
                permission = Manifest.permission.WAKE_LOCK,
                isRequired = true,
                rationale = "Keep the device awake during playback to prevent audio interruption.",
                features = listOf(
                    "Uninterrupted audio playback",
                    "Prevent sleep during playback"
                )
            )
        )
    }

    /**
     * Get offline fallback message for a feature.
     */
    fun getOfflineFallbackMessage(feature: NetworkFeature): String {
        return when (feature) {
            NetworkFeature.PODCAST_SEARCH ->
                "Search is unavailable offline. Browse your downloaded podcasts instead."
            NetworkFeature.FEED_UPDATES ->
                "Cannot check for new episodes. Showing cached episodes."
            NetworkFeature.AUDIO_STREAMING ->
                "Streaming unavailable. Only downloaded episodes can be played."
            NetworkFeature.IMAGE_LOADING ->
                "Images unavailable. Showing placeholder artwork."
            NetworkFeature.BACKGROUND_SYNC ->
                "Background sync disabled. Open app to update feeds."
            NetworkFeature.TRANSCRIPT_FETCH ->
                "Transcripts unavailable offline."
            NetworkFeature.CHAPTER_IMAGES ->
                "Chapter images unavailable offline."
            NetworkFeature.OLLAMA_LLM ->
                "Ollama not available. Using pattern matching for commands."
            NetworkFeature.CLAUDE_API ->
                "Claude API unavailable. Using local processing only."
        }
    }
}

/**
 * Current permission state snapshot.
 */
data class PermissionState(
    val hasInternet: Boolean = false,
    val hasRecordAudio: Boolean = false,
    val hasForegroundService: Boolean = false,
    val hasPostNotifications: Boolean = false
) {
    val canPlayAudio: Boolean get() = hasForegroundService
    val canUseVoice: Boolean get() = hasRecordAudio
    val canUseNetwork: Boolean get() = hasInternet
    val canShowNotifications: Boolean get() = hasPostNotifications
}

/**
 * Operational mode based on permissions and settings.
 */
enum class OperationalMode {
    /** No INTERNET permission - fully offline */
    OFFLINE_NO_PERMISSION,

    /** Permission granted but no network connectivity */
    OFFLINE_NO_NETWORK,

    /** User disabled network in app settings */
    OFFLINE_USER_CHOICE,

    /** Online but with some features disabled */
    ONLINE_LIMITED,

    /** Full online operation */
    ONLINE_FULL
}

/**
 * Privacy presets for quick configuration.
 */
enum class PrivacyPreset(val displayName: String, val description: String) {
    MAXIMUM_PRIVACY(
        "Maximum Privacy",
        "No network access. Only downloaded content available."
    ),
    BALANCED(
        "Balanced",
        "Core features enabled. No background sync or AI."
    ),
    FULL_FEATURES(
        "Full Features",
        "All features enabled including AI assistants."
    )
}
