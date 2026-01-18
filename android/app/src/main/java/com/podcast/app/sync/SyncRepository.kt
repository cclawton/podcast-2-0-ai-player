package com.podcast.app.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore extension for sync settings.
 */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings"
)

/**
 * Repository for persisting sync settings using DataStore.
 *
 * Thread-safe and reactive - emits updates when settings change.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferenceKeys {
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val NOTIFY_NEW_EPISODES = booleanPreferencesKey("notify_new_episodes")
        val MAX_NOTIFICATION_EPISODES = intPreferencesKey("max_notification_episodes")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val LAST_SYNC_NEW_EPISODES = intPreferencesKey("last_sync_new_episodes")
        val IS_SYNCING = booleanPreferencesKey("is_syncing")
    }

    /**
     * Flow of current sync settings.
     * Emits updates whenever any setting changes.
     */
    val settings: Flow<SyncSettings> = context.syncDataStore.data.map { prefs ->
        SyncSettings(
            syncInterval = SyncInterval.fromHours(
                prefs[PreferenceKeys.SYNC_INTERVAL_HOURS] ?: SyncInterval.MANUAL.hours
            ),
            wifiOnly = prefs[PreferenceKeys.WIFI_ONLY] ?: true,
            notifyNewEpisodes = prefs[PreferenceKeys.NOTIFY_NEW_EPISODES] ?: true,
            maxNotificationEpisodes = prefs[PreferenceKeys.MAX_NOTIFICATION_EPISODES] ?: 5,
            lastSyncTimestamp = prefs[PreferenceKeys.LAST_SYNC_TIMESTAMP] ?: 0L,
            lastSyncError = prefs[PreferenceKeys.LAST_SYNC_ERROR],
            lastSyncNewEpisodes = prefs[PreferenceKeys.LAST_SYNC_NEW_EPISODES] ?: 0,
            isSyncing = prefs[PreferenceKeys.IS_SYNCING] ?: false
        )
    }

    /**
     * Update the sync interval.
     */
    suspend fun updateSyncInterval(interval: SyncInterval) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.SYNC_INTERVAL_HOURS] = interval.hours
        }
    }

    /**
     * Update Wi-Fi only setting.
     */
    suspend fun updateWifiOnly(wifiOnly: Boolean) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.WIFI_ONLY] = wifiOnly
        }
    }

    /**
     * Update notification preference.
     */
    suspend fun updateNotifyNewEpisodes(notify: Boolean) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.NOTIFY_NEW_EPISODES] = notify
        }
    }

    /**
     * Record a successful sync.
     */
    suspend fun recordSyncSuccess(newEpisodesCount: Int) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
            prefs.minusAssign(PreferenceKeys.LAST_SYNC_ERROR) // Clear error on success
            prefs[PreferenceKeys.LAST_SYNC_NEW_EPISODES] = newEpisodesCount
            prefs[PreferenceKeys.IS_SYNCING] = false
        }
    }

    /**
     * Record a sync error.
     */
    suspend fun recordSyncError(error: String) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
            prefs[PreferenceKeys.LAST_SYNC_ERROR] = error
            prefs[PreferenceKeys.IS_SYNCING] = false
        }
    }

    /**
     * Set syncing state.
     */
    suspend fun setSyncing(isSyncing: Boolean) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.IS_SYNCING] = isSyncing
        }
    }

    /**
     * Update all sync settings at once.
     */
    suspend fun updateSettings(settings: SyncSettings) {
        context.syncDataStore.edit { prefs ->
            prefs[PreferenceKeys.SYNC_INTERVAL_HOURS] = settings.syncInterval.hours
            prefs[PreferenceKeys.WIFI_ONLY] = settings.wifiOnly
            prefs[PreferenceKeys.NOTIFY_NEW_EPISODES] = settings.notifyNewEpisodes
            prefs[PreferenceKeys.MAX_NOTIFICATION_EPISODES] = settings.maxNotificationEpisodes
        }
    }

    /**
     * Reset sync settings to defaults.
     */
    suspend fun resetToDefaults() {
        context.syncDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
