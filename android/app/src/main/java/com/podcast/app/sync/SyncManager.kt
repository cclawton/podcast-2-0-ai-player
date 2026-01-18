package com.podcast.app.sync

import android.content.Context
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for feed synchronization.
 *
 * Coordinates:
 * - Settings management
 * - WorkManager scheduling
 * - Manual sync triggers
 * - Notification channel setup
 *
 * Privacy considerations:
 * - Respects privacy settings before any network operations
 * - Does not sync if background sync is disabled
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val feedRefreshService: FeedRefreshService,
    private val syncNotificationManager: SyncNotificationManager,
    private val privacyManager: PrivacyManager
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    /**
     * Flow of current sync settings.
     */
    val settings: Flow<SyncSettings> = syncRepository.settings

    /**
     * Initialize the sync system.
     * Should be called during app startup.
     */
    fun initialize() {
        // Create notification channel
        syncNotificationManager.createNotificationChannel()

        DiagnosticLogger.i(TAG, "Sync system initialized")
    }

    /**
     * Update sync interval and reschedule background sync.
     */
    suspend fun setSyncInterval(interval: SyncInterval) {
        syncRepository.updateSyncInterval(interval)

        // Get current settings to check Wi-Fi only preference
        val settings = syncRepository.settings.first()

        // Reschedule with new interval
        FeedSyncWorker.schedule(context, interval, settings.wifiOnly)

        DiagnosticLogger.i(TAG, "Sync interval updated to: ${interval.displayName}")
    }

    /**
     * Update Wi-Fi only preference and reschedule if needed.
     */
    suspend fun setWifiOnly(wifiOnly: Boolean) {
        syncRepository.updateWifiOnly(wifiOnly)

        // Reschedule with current interval and new network preference
        val settings = syncRepository.settings.first()
        if (settings.syncInterval != SyncInterval.MANUAL) {
            FeedSyncWorker.schedule(context, settings.syncInterval, wifiOnly)
        }

        DiagnosticLogger.i(TAG, "Wi-Fi only updated to: $wifiOnly")
    }

    /**
     * Update notification preference.
     */
    suspend fun setNotifyNewEpisodes(notify: Boolean) {
        syncRepository.updateNotifyNewEpisodes(notify)
        DiagnosticLogger.i(TAG, "Notify new episodes updated to: $notify")
    }

    /**
     * Trigger a manual sync now.
     *
     * @return Result with sync details
     */
    suspend fun syncNow(): SyncResult {
        // Check if background sync is allowed
        val isAllowed = privacyManager.isFeatureAllowed(NetworkFeature.BACKGROUND_SYNC)
        if (!isAllowed) {
            return SyncResult(
                success = false,
                error = "Background sync is disabled in privacy settings"
            )
        }

        // Mark as syncing
        syncRepository.setSyncing(true)

        try {
            val result = feedRefreshService.refreshAllFeeds()

            if (result.success) {
                syncRepository.recordSyncSuccess(result.totalNewEpisodes)

                // Show notification if enabled
                val settings = syncRepository.settings.first()
                if (settings.notifyNewEpisodes && result.newEpisodes.isNotEmpty()) {
                    syncNotificationManager.showNewEpisodesNotification(
                        result.newEpisodes,
                        settings.maxNotificationEpisodes
                    )
                }

                return SyncResult(
                    success = true,
                    newEpisodesCount = result.totalNewEpisodes,
                    podcastsSynced = result.totalPodcastsRefreshed
                )
            } else {
                val errorMessage = result.errors.firstOrNull()?.message ?: "Sync failed"
                syncRepository.recordSyncError(errorMessage)

                return SyncResult(
                    success = false,
                    newEpisodesCount = result.totalNewEpisodes,
                    podcastsSynced = result.totalPodcastsRefreshed,
                    error = errorMessage
                )
            }
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Sync failed"
            syncRepository.recordSyncError(errorMessage)

            return SyncResult(
                success = false,
                error = errorMessage
            )
        }
    }

    /**
     * Apply current settings to WorkManager.
     * Call this on app start to ensure WorkManager is configured correctly.
     */
    suspend fun applySettings() {
        val settings = syncRepository.settings.first()
        FeedSyncWorker.schedule(context, settings.syncInterval, settings.wifiOnly)
    }

    /**
     * Check if sync is available (privacy settings allow it).
     */
    suspend fun isSyncAvailable(): Boolean {
        return privacyManager.isFeatureAllowed(NetworkFeature.BACKGROUND_SYNC)
    }

    /**
     * Cancel all background sync.
     */
    fun cancelBackgroundSync() {
        FeedSyncWorker.cancel(context)
        DiagnosticLogger.i(TAG, "Background sync cancelled")
    }
}
