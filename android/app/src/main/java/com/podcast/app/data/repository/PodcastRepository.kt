package com.podcast.app.data.repository

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.remote.models.PodcastFeed
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for podcast data, combining local database and remote API.
 *
 * Implements offline-first architecture:
 * - Data is primarily served from local database
 * - API is used to refresh and search for new content
 * - Works fully offline with cached data
 */
@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val api: PodcastIndexApi
) {
    /**
     * Get subscribed podcasts from local database.
     */
    fun getSubscribedPodcasts(): Flow<List<Podcast>> {
        return podcastDao.getSubscribedPodcasts()
    }

    /**
     * Search for podcasts via API.
     */
    suspend fun searchPodcasts(query: String, limit: Int = 20): Result<List<Podcast>> {
        return try {
            val response = api.searchByTerm(query, limit)
            val podcasts = response.feeds.map { it.toPodcast() }
            // Cache results locally
            podcastDao.insertPodcasts(podcasts)
            Result.success(podcasts)
        } catch (e: Exception) {
            // Try to return cached results
            Result.failure(e)
        }
    }

    /**
     * Get podcast by ID, fetching from API if not in database.
     */
    suspend fun getPodcast(podcastIndexId: Long): Podcast? {
        // Try local first
        val local = podcastDao.getPodcastByIndexId(podcastIndexId)
        if (local != null) return local

        // Fetch from API
        return try {
            val response = api.getPodcastById(podcastIndexId)
            response.feed?.toPodcast()?.also {
                podcastDao.insertPodcast(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Subscribe to a podcast.
     */
    suspend fun subscribeToPodcast(podcastIndexId: Long): Result<Podcast> {
        return try {
            val podcast = getPodcast(podcastIndexId)
                ?: return Result.failure(Exception("Podcast not found"))

            val subscribedPodcast = podcast.copy(isSubscribed = true)
            podcastDao.insertPodcast(subscribedPodcast)

            // Fetch episodes
            refreshEpisodes(subscribedPodcast.id)

            Result.success(subscribedPodcast)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unsubscribe from a podcast.
     */
    suspend fun unsubscribeFromPodcast(podcastId: Long) {
        podcastDao.updateSubscription(podcastId, false)
    }

    /**
     * Get episodes for a podcast.
     */
    fun getEpisodes(podcastId: Long, limit: Int = 100): Flow<List<Episode>> {
        return episodeDao.getEpisodesByPodcast(podcastId, limit)
    }

    /**
     * Refresh episodes from API.
     */
    suspend fun refreshEpisodes(podcastId: Long): Result<List<Episode>> {
        return try {
            val podcast = podcastDao.getPodcastById(podcastId)
                ?: return Result.failure(Exception("Podcast not found"))

            val response = api.getEpisodesByFeedId(podcast.podcastIndexId)
            val episodes = response.items.map { it.toEpisode(podcastId) }
            episodeDao.insertEpisodes(episodes)

            Result.success(episodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recent episodes from subscribed podcasts.
     */
    fun getRecentEpisodes(limit: Int = 50): Flow<List<Episode>> {
        return episodeDao.getRecentEpisodesFromSubscriptions(limit)
    }

    /**
     * Get trending podcasts.
     */
    suspend fun getTrendingPodcasts(limit: Int = 20): Result<List<Podcast>> {
        return try {
            val response = api.getTrendingPodcasts(limit)
            val podcasts = response.feeds.map { it.toPodcast() }
            Result.success(podcasts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun PodcastFeed.toPodcast(): Podcast {
        return Podcast(
            podcastIndexId = id,
            title = title,
            feedUrl = url,
            imageUrl = artwork ?: image,
            description = description,
            language = language ?: "en",
            explicit = explicit,
            author = author,
            episodeCount = episodeCount,
            websiteUrl = link,
            podcastGuid = podcastGuid,
            isSubscribed = false
        )
    }

    private fun EpisodeItem.toEpisode(podcastId: Long): Episode {
        return Episode(
            episodeIndexId = id,
            podcastId = podcastId,
            title = title,
            description = description,
            audioUrl = enclosureUrl,
            audioDuration = duration,
            audioSize = enclosureLength,
            audioType = enclosureType ?: "audio/mpeg",
            publishedAt = datePublished,
            episodeGuid = guid,
            explicit = explicit == 1,
            link = link,
            imageUrl = image ?: feedImage,
            transcriptUrl = transcripts?.firstOrNull()?.url,
            transcriptType = transcripts?.firstOrNull()?.type,
            seasonNumber = season,
            episodeNumber = episode
        )
    }
}
