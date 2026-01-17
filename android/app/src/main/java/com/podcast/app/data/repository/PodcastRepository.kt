package com.podcast.app.data.repository

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.remote.models.PodcastFeed
import com.podcast.app.data.rss.RssFeedParser
import com.podcast.app.data.rss.RssParseException
import com.podcast.app.di.RssHttpClient
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
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
    private val api: PodcastIndexApi,
    private val rssFeedParser: RssFeedParser,
    @RssHttpClient private val rssHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "PodcastRepository"
    }
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
        } catch (e: HttpException) {
            Log.e(TAG, "Search failed with HTTP ${e.code()}: ${e.message()}")
            val errorMessage = when (e.code()) {
                401, 403 -> "API authentication failed - check credentials"
                else -> "Search failed (HTTP ${e.code()})"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}", e)
            Result.failure(Exception("Search failed: ${e.message}"))
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
     * Subscribe to a podcast from an RSS feed URL.
     *
     * This allows users to manually add podcasts that are not indexed
     * by Podcast Index (e.g., private feeds, paywalled content).
     *
     * @param feedUrl The RSS feed URL
     * @return Result containing the subscribed Podcast on success
     */
    suspend fun subscribeFromRssFeed(feedUrl: String): Result<Podcast> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate URL format
                if (!isValidRssUrl(feedUrl)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid RSS feed URL")
                    )
                }

                // Check if already subscribed to this feed
                val existingPodcast = podcastDao.getPodcastByFeedUrl(feedUrl)
                if (existingPodcast != null) {
                    if (existingPodcast.isSubscribed) {
                        return@withContext Result.failure(
                            IllegalStateException("Already subscribed to this feed")
                        )
                    }
                    // Re-subscribe to existing podcast
                    val resubscribed = existingPodcast.copy(isSubscribed = true)
                    podcastDao.insertPodcast(resubscribed)
                    return@withContext Result.success(resubscribed)
                }

                // Fetch and parse the RSS feed
                val request = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "Podcast 2.0 AI Player/1.0")
                    .build()

                val response = rssHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch feed: HTTP ${response.code}")
                    )
                }

                val responseBody = response.body
                    ?: return@withContext Result.failure(Exception("Empty response from feed"))

                val parsedFeed = responseBody.byteStream().use { inputStream ->
                    rssFeedParser.parse(inputStream, feedUrl)
                }

                // Save podcast to database
                val podcastId = podcastDao.insertPodcast(parsedFeed.podcast)
                val savedPodcast = parsedFeed.podcast.copy(id = podcastId)

                // Save episodes with correct podcast ID
                val episodesWithPodcastId = parsedFeed.episodes.map { episode ->
                    episode.copy(podcastId = podcastId)
                }
                episodeDao.insertEpisodes(episodesWithPodcastId)

                Result.success(savedPodcast)
            } catch (e: RssParseException) {
                Result.failure(Exception("Invalid RSS feed: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(Exception("Failed to subscribe: ${e.message}"))
            }
        }
    }

    /**
     * Validate that a string is a valid RSS feed URL.
     */
    private fun isValidRssUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            (uri.scheme == "http" || uri.scheme == "https") && uri.host != null
        } catch (_: Exception) {
            false
        }
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
        } catch (e: HttpException) {
            Log.e(TAG, "Trending failed with HTTP ${e.code()}: ${e.message()}")
            val errorMessage = when (e.code()) {
                401, 403 -> "API authentication failed - check credentials"
                else -> "Failed to load trending (HTTP ${e.code()})"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Trending failed: ${e.message}", e)
            Result.failure(Exception("Failed to load trending: ${e.message}"))
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
