package com.podcast.app.api

import com.podcast.app.api.model.EpisodesResponse
import com.podcast.app.api.model.PodcastResponse
import com.podcast.app.api.model.RecentEpisodesResponse
import com.podcast.app.api.model.SearchResponse
import com.podcast.app.api.model.SingleEpisodeResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Podcast Index API.
 *
 * Base URL: https://api.podcastindex.org/api/1.0
 *
 * Authentication is handled by [PodcastIndexAuthInterceptor].
 * All endpoints require valid API credentials.
 */
interface PodcastIndexApi {

    companion object {
        const val BASE_URL = "https://api.podcastindex.org/api/1.0/"
        const val DEFAULT_MAX_RESULTS = 20
        const val MAX_EPISODES_PER_REQUEST = 50
    }

    // ========== Search Endpoints ==========

    /**
     * Search for podcasts by term.
     *
     * @param query Search query string
     * @param max Maximum number of results (default: 20)
     * @param clean Only return non-explicit feeds (optional)
     * @param similar Include similar results (optional)
     * @return Search results with matching podcast feeds
     */
    @GET("search/byterm")
    suspend fun searchByTerm(
        @Query("q") query: String,
        @Query("max") max: Int = DEFAULT_MAX_RESULTS,
        @Query("clean") clean: Boolean? = null,
        @Query("similar") similar: Boolean? = null
    ): Response<SearchResponse>

    /**
     * Search for podcasts by title only.
     *
     * @param query Title search string
     * @param max Maximum number of results
     * @param similar Include similar results
     * @return Search results matching title
     */
    @GET("search/bytitle")
    suspend fun searchByTitle(
        @Query("q") query: String,
        @Query("max") max: Int = DEFAULT_MAX_RESULTS,
        @Query("similar") similar: Boolean? = null
    ): Response<SearchResponse>

    /**
     * Search for podcasts by person (host, guest, etc.).
     *
     * @param person Person name to search
     * @param max Maximum number of results
     * @return Podcasts featuring the specified person
     */
    @GET("search/byperson")
    suspend fun searchByPerson(
        @Query("q") person: String,
        @Query("max") max: Int = DEFAULT_MAX_RESULTS
    ): Response<SearchResponse>

    // ========== Podcast Metadata Endpoints ==========

    /**
     * Get podcast metadata by Podcast Index feed ID.
     *
     * @param id Podcast Index feed ID
     * @return Podcast metadata including P2.0 tags
     */
    @GET("podcasts/byfeedid")
    suspend fun getPodcastById(
        @Query("id") id: Long
    ): Response<PodcastResponse>

    /**
     * Get podcast metadata by feed URL.
     *
     * @param url Feed URL
     * @return Podcast metadata
     */
    @GET("podcasts/byfeedurl")
    suspend fun getPodcastByFeedUrl(
        @Query("url") url: String
    ): Response<PodcastResponse>

    /**
     * Get podcast metadata by iTunes ID.
     *
     * @param id iTunes/Apple Podcasts ID
     * @return Podcast metadata
     */
    @GET("podcasts/byitunesid")
    suspend fun getPodcastByItunesId(
        @Query("id") id: Long
    ): Response<PodcastResponse>

    /**
     * Get podcast metadata by GUID.
     *
     * @param guid Podcast GUID
     * @return Podcast metadata
     */
    @GET("podcasts/byguid")
    suspend fun getPodcastByGuid(
        @Query("guid") guid: String
    ): Response<PodcastResponse>

    // ========== Episode Endpoints ==========

    /**
     * Get episodes for a podcast by feed ID.
     *
     * @param id Podcast Index feed ID
     * @param max Maximum number of episodes (default: 50)
     * @param since Only return episodes published after this timestamp (Unix epoch)
     * @param fulltext Include full episode description text
     * @return Episodes for the podcast
     */
    @GET("episodes/byfeedid")
    suspend fun getEpisodesByFeedId(
        @Query("id") id: Long,
        @Query("max") max: Int = MAX_EPISODES_PER_REQUEST,
        @Query("since") since: Long? = null,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<EpisodesResponse>

    /**
     * Get episodes for a podcast by feed URL.
     *
     * @param url Feed URL
     * @param max Maximum number of episodes
     * @param since Only return episodes published after this timestamp
     * @param fulltext Include full episode description text
     * @return Episodes for the podcast
     */
    @GET("episodes/byfeedurl")
    suspend fun getEpisodesByFeedUrl(
        @Query("url") url: String,
        @Query("max") max: Int = MAX_EPISODES_PER_REQUEST,
        @Query("since") since: Long? = null,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<EpisodesResponse>

    /**
     * Get episodes for a podcast by iTunes ID.
     *
     * @param id iTunes/Apple Podcasts ID
     * @param max Maximum number of episodes
     * @param since Only return episodes published after this timestamp
     * @param fulltext Include full episode description text
     * @return Episodes for the podcast
     */
    @GET("episodes/byitunesid")
    suspend fun getEpisodesByItunesId(
        @Query("id") id: Long,
        @Query("max") max: Int = MAX_EPISODES_PER_REQUEST,
        @Query("since") since: Long? = null,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<EpisodesResponse>

    /**
     * Get a single episode by ID.
     *
     * @param id Episode ID
     * @param fulltext Include full episode description text
     * @return Single episode with full metadata
     */
    @GET("episodes/byid")
    suspend fun getEpisodeById(
        @Query("id") id: Long,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<SingleEpisodeResponse>

    /**
     * Get a single episode by GUID.
     *
     * @param guid Episode GUID
     * @param feedUrl Feed URL (required for GUID lookup)
     * @param fulltext Include full episode description text
     * @return Single episode with full metadata
     */
    @GET("episodes/byguid")
    suspend fun getEpisodeByGuid(
        @Query("guid") guid: String,
        @Query("feedurl") feedUrl: String,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<SingleEpisodeResponse>

    /**
     * Get recent episodes across all podcasts.
     *
     * @param max Maximum number of episodes (default: 20)
     * @param excludeString Exclude episodes with titles containing this string
     * @param before Only return episodes published before this timestamp
     * @param fulltext Include full episode description text
     * @return Recent episodes from various podcasts
     */
    @GET("episodes/recent")
    suspend fun getRecentEpisodes(
        @Query("max") max: Int = DEFAULT_MAX_RESULTS,
        @Query("excludeString") excludeString: String? = null,
        @Query("before") before: Long? = null,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<RecentEpisodesResponse>

    /**
     * Get random episodes.
     *
     * @param max Maximum number of episodes
     * @param lang Filter by language code (e.g., "en")
     * @param cat Category filter
     * @param notcat Exclude category
     * @param fulltext Include full episode description text
     * @return Random episodes
     */
    @GET("episodes/random")
    suspend fun getRandomEpisodes(
        @Query("max") max: Int = DEFAULT_MAX_RESULTS,
        @Query("lang") lang: String? = null,
        @Query("cat") cat: String? = null,
        @Query("notcat") notcat: String? = null,
        @Query("fulltext") fulltext: Boolean? = null
    ): Response<EpisodesResponse>

    // ========== Live Episodes ==========

    /**
     * Get currently live podcast episodes.
     *
     * @param max Maximum number of results
     * @return Currently live episodes
     */
    @GET("episodes/live")
    suspend fun getLiveEpisodes(
        @Query("max") max: Int = DEFAULT_MAX_RESULTS
    ): Response<EpisodesResponse>
}
