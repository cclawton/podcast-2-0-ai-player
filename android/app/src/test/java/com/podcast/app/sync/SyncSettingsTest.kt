package com.podcast.app.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for SyncSettings and SyncInterval.
 */
class SyncSettingsTest {

    @Test
    fun `SyncInterval fromHours returns correct interval`() {
        assertThat(SyncInterval.fromHours(0)).isEqualTo(SyncInterval.MANUAL)
        assertThat(SyncInterval.fromHours(1)).isEqualTo(SyncInterval.HOURLY)
        assertThat(SyncInterval.fromHours(3)).isEqualTo(SyncInterval.THREE_HOURS)
        assertThat(SyncInterval.fromHours(6)).isEqualTo(SyncInterval.SIX_HOURS)
        assertThat(SyncInterval.fromHours(12)).isEqualTo(SyncInterval.TWELVE_HOURS)
        assertThat(SyncInterval.fromHours(24)).isEqualTo(SyncInterval.DAILY)
    }

    @Test
    fun `SyncInterval fromHours returns MANUAL for unknown values`() {
        assertThat(SyncInterval.fromHours(2)).isEqualTo(SyncInterval.MANUAL)
        assertThat(SyncInterval.fromHours(48)).isEqualTo(SyncInterval.MANUAL)
        assertThat(SyncInterval.fromHours(-1)).isEqualTo(SyncInterval.MANUAL)
    }

    @Test
    fun `SyncSettings DEFAULT has expected values`() {
        val settings = SyncSettings.DEFAULT

        assertThat(settings.syncInterval).isEqualTo(SyncInterval.MANUAL)
        assertThat(settings.wifiOnly).isTrue()
        assertThat(settings.notifyNewEpisodes).isTrue()
        assertThat(settings.maxNotificationEpisodes).isEqualTo(5)
        assertThat(settings.lastSyncTimestamp).isEqualTo(0L)
        assertThat(settings.lastSyncError).isNull()
        assertThat(settings.lastSyncNewEpisodes).isEqualTo(0)
        assertThat(settings.isSyncing).isFalse()
    }

    @Test
    fun `SyncInterval entries have correct hours`() {
        assertThat(SyncInterval.MANUAL.hours).isEqualTo(0)
        assertThat(SyncInterval.HOURLY.hours).isEqualTo(1)
        assertThat(SyncInterval.THREE_HOURS.hours).isEqualTo(3)
        assertThat(SyncInterval.SIX_HOURS.hours).isEqualTo(6)
        assertThat(SyncInterval.TWELVE_HOURS.hours).isEqualTo(12)
        assertThat(SyncInterval.DAILY.hours).isEqualTo(24)
    }

    @Test
    fun `SyncInterval entries have display names`() {
        SyncInterval.entries.forEach { interval ->
            assertThat(interval.displayName).isNotEmpty()
        }
    }

    @Test
    fun `SyncResult success with new episodes`() {
        val result = SyncResult(
            success = true,
            newEpisodesCount = 5,
            podcastsSynced = 3
        )

        assertThat(result.success).isTrue()
        assertThat(result.newEpisodesCount).isEqualTo(5)
        assertThat(result.podcastsSynced).isEqualTo(3)
        assertThat(result.error).isNull()
        assertThat(result.timestamp).isGreaterThan(0)
    }

    @Test
    fun `SyncResult failure with error`() {
        val result = SyncResult(
            success = false,
            error = "Network unavailable"
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).isEqualTo("Network unavailable")
        assertThat(result.newEpisodesCount).isEqualTo(0)
    }
}
