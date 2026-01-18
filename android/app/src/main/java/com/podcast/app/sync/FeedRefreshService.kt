package com.podcast.app.sync

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.rss.RssFeedParser
import com.podcast.app.di.RssHttpClient
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for refreshing podcast feeds and detecting new episodes.
 *
 * Privacy considerations:
 * - Only runs when network is allowed in privacy settings
 * - Respects background sync permission
 * - Logs errors without sensitive data
 */
@Singleton
class FeedRefreshService @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val rssFeedParser: RssFeedParser,
    @RssHttpClient private val httpClient: OkHttpClient,
    private val privacyManager: PrivacyManager
) {
    companion object {
        private const val TAG = "FeedRefreshService"
    }

    /**
     * Result of refreshing all subscribed feeds.
     */
    data class RefreshResult(
        val success: Boolean,
        val totalPodcastsRefreshed: Int = 0,
        val totalNewEpisodes: Int = 0,
        val newEpisodes: List<NewEpisodeInfo> = emptyList(),
        val errors: List<RefreshError> = emptyList()
    )

    /**
     * Information about a newly detected episode.
     */
    data class NewEpisodeInfo(
        val podcastTitle: String,
        val episodeTitle: String,
        val episodeId: Long,
        val podcastId: Long
    )

    /**
     * Error that occurred during feed refresh.
     */
    data class RefreshError(
        val podcastTitle: String,
        val podcastId: Long,
        val message: String
    )

    /**
     * Refresh all subscribed podcast feeds.
     *
     * @return RefreshResult with details about what was updated
     */
    suspend fun refreshAllFeeds(): RefreshResult = withContext(Dispatchers.IO) {
        // Check if background sync is allowed
        val isAllowed = privacyManager.isFeatureAllowed(NetworkFeature.BACKGROUND_SYNC)
        if (!isAllowed) {
            DiagnosticLogger.i(TAG, "Background sync not allowed by privacy settings")
            return@withContext RefreshResult(
                success = false,
                errors = listOf(
                    RefreshError(
                        podcastTitle = "All",
                        podcastId = 0L,
                        message = "Background sync disabled in privacy settings"
                    )
                )
            )
        }

        val subscribedPodcasts = podcastDao.getSubscribedPodcasts().first()

        if (subscribedPodcasts.isEmpty()) {
            DiagnosticLogger.i(TAG, "No subscribed podcasts to refresh")
            return@withContext RefreshResult(success = true)
        }

        DiagnosticLogger.i(TAG, "Refreshing ${subscribedPodcasts.size} podcast feeds")

        val allNewEpisodes = mutableListOf<NewEpisodeInfo>()
        val errors = mutableListOf<RefreshError>()
        var podcastsRefreshed = 0

        for (podcast in subscribedPodcasts) {
            try {
                val result = refreshPodcastFeed(podcast)
                if (result.isSuccess) {
                    val newEpisodes = result.getOrThrow()
                    allNewEpisodes.addAll(newEpisodes)
                    podcastsRefreshed++

                    // Update last synced timestamp
                    podcastDao.updateLastSynced(podcast.id, System.currentTimeMillis())
                } else {
                    errors.add(
                        RefreshError(
                            podcastTitle = podcast.title,
                            podcastId = podcast.id,
                            message = result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    )
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Error refreshing ${podcast.title}: ${e.message}")
                errors.add(
                    RefreshError(
                        podcastTitle = podcast.title,
                        podcastId = podcast.id,
                        message = e.message ?: "Unknown error"
                    )
                )
            }
        }

        DiagnosticLogger.i(TAG, "Refresh complete: $podcastsRefreshed podcasts, ${allNewEpisodes.size} new episodes, ${errors.size} errors")

        RefreshResult(
            success = errors.isEmpty(),
            totalPodcastsRefreshed = podcastsRefreshed,
            totalNewEpisodes = allNewEpisodes.size,
            newEpisodes = allNewEpisodes,
            errors = errors
        )
    }

    /**
     * Refresh a single podcast feed and detect new episodes.
     *
     * @param podcast The podcast to refresh
     * @return Result containing list of new episodes, or an error
     */
    suspend fun refreshPodcastFeed(podcast: Podcast): Result<List<NewEpisodeInfo>> = withContext(Dispatchers.IO) {
        try {
            DiagnosticLogger.d(TAG, "Fetching feed for: ${podcast.title}")

            // Fetch RSS feed
            val request = Request.Builder()
                .url(podcast.feedUrl)
                .header("User-Agent", "Podcast 2.0 AI Player/1.0")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(IOException("Empty response body"))

            // Parse RSS feed
            val parsedFeed = body.byteStream().use { inputStream ->
                rssFeedParser.parse(inputStream, podcast.feedUrl)
            }

            // Get existing episode GUIDs for this podcast
            val existingEpisodes = episodeDao.getEpisodesByPodcastOnce(podcast.id)
            val existingGuids = existingEpisodes.mapNotNull { it.episodeGuid }.toSet()
            val existingIndexIds = existingEpisodes.map { it.episodeIndexId }.toSet()

            // Find new episodes
            val newEpisodes = mutableListOf<Episode>()
            val newEpisodeInfos = mutableListOf<NewEpisodeInfo>()

            for (parsedEpisode in parsedFeed.episodes) {
                val isNew = when {
                    // Check by GUID first (most reliable)
                    parsedEpisode.episodeGuid != null ->
                        parsedEpisode.episodeGuid !in existingGuids
                    // Fall back to index ID
                    else -> parsedEpisode.episodeIndexId !in existingIndexIds
                }

                if (isNew) {
                    val episodeWithPodcastId = parsedEpisode.copy(podcastId = podcast.id)
                    newEpisodes.add(episodeWithPodcastId)
                }
            }

            // Insert new episodes
            if (newEpisodes.isNotEmpty()) {
                DiagnosticLogger.i(TAG, "Found ${newEpisodes.size} new episodes for ${podcast.title}")
                episodeDao.insertEpisodes(newEpisodes)

                // Create episode info for notifications
                for (episode in newEpisodes) {
                    newEpisodeInfos.add(
                        NewEpisodeInfo(
                            podcastTitle = podcast.title,
                            episodeTitle = episode.title,
                            episodeId = episode.id,
                            podcastId = podcast.id
                        )
                    )
                }
            }

            Result.success(newEpisodeInfos)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Failed to refresh ${podcast.title}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Refresh a single podcast by ID.
     *
     * @param podcastId The podcast ID to refresh
     * @return Result containing list of new episodes
     */
    suspend fun refreshPodcast(podcastId: Long): Result<List<NewEpisodeInfo>> {
        val podcast = podcastDao.getPodcastById(podcastId)
            ?: return Result.failure(IllegalArgumentException("Podcast not found: $podcastId"))

        return refreshPodcastFeed(podcast)
    }
}
