package com.podcast.app.sync

import kotlinx.serialization.Serializable

/**
 * Sync interval options for background feed refresh.
 */
enum class SyncInterval(val hours: Int, val displayName: String) {
    MANUAL(0, "Manual only"),
    HOURLY(1, "Every hour"),
    THREE_HOURS(3, "Every 3 hours"),
    SIX_HOURS(6, "Every 6 hours"),
    TWELVE_HOURS(12, "Every 12 hours"),
    DAILY(24, "Once a day");

    companion object {
        fun fromHours(hours: Int): SyncInterval {
            return entries.find { it.hours == hours } ?: MANUAL
        }
    }
}

/**
 * Settings for background feed synchronization.
 *
 * Privacy considerations:
 * - Sync is disabled by default (respects user choice)
 * - Only syncs when network is allowed in privacy settings
 * - Wi-Fi only option to save mobile data
 * - Notifications can be disabled
 */
@Serializable
data class SyncSettings(
    /**
     * Interval between automatic sync operations.
     * MANUAL means no automatic sync.
     */
    val syncInterval: SyncInterval = SyncInterval.MANUAL,

    /**
     * Only sync when connected to Wi-Fi.
     * Respects user's data preferences.
     */
    val wifiOnly: Boolean = true,

    /**
     * Show notification when new episodes are found.
     */
    val notifyNewEpisodes: Boolean = true,

    /**
     * Maximum number of episodes to show in notification.
     */
    val maxNotificationEpisodes: Int = 5,

    /**
     * Last successful sync timestamp (epoch millis).
     */
    val lastSyncTimestamp: Long = 0L,

    /**
     * Last sync error message, if any.
     */
    val lastSyncError: String? = null,

    /**
     * Number of new episodes found in last sync.
     */
    val lastSyncNewEpisodes: Int = 0,

    /**
     * Whether sync is currently in progress.
     */
    val isSyncing: Boolean = false
) {
    companion object {
        val DEFAULT = SyncSettings()
    }
}

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val success: Boolean,
    val newEpisodesCount: Int = 0,
    val podcastsSynced: Int = 0,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
