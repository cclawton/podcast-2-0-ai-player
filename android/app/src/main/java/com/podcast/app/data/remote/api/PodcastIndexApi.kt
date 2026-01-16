package com.podcast.app.data.remote.api

import com.podcast.app.data.remote.models.EpisodesResponse
import com.podcast.app.data.remote.models.PodcastResponse
import com.podcast.app.data.remote.models.RecentEpisodesResponse
import com.podcast.app.data.remote.models.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Podcast Index API interface.
 * Base URL: https://api.podcastindex.org/api/1.0/
 *
 * Authentication headers (X-Auth-Date, X-Auth-Key, Authorization) are added
 * by [PodcastIndexAuthInterceptor].
 */
interface PodcastIndexApi {

    // Search endpoints

    @GET("search/byterm")
    suspend fun searchByTerm(
        @Query("q") query: String,
        @Query("max") max: Int = 20,
        @Query("clean") clean: Boolean = true
    ): SearchResponse

    @GET("search/byperson")
    suspend fun searchByPerson(
        @Query("q") person: String,
        @Query("max") max: Int = 20
    ): SearchResponse

    @GET("search/bytitle")
    suspend fun searchByTitle(
        @Query("q") title: String,
        @Query("max") max: Int = 20
    ): SearchResponse

    // Podcast metadata endpoints

    @GET("podcasts/byfeedid")
    suspend fun getPodcastById(
        @Query("id") id: Long
    ): PodcastResponse

    @GET("podcasts/byfeedurl")
    suspend fun getPodcastByFeedUrl(
        @Query("url") feedUrl: String
    ): PodcastResponse

    @GET("podcasts/byguid")
    suspend fun getPodcastByGuid(
        @Query("guid") guid: String
    ): PodcastResponse

    @GET("podcasts/trending")
    suspend fun getTrendingPodcasts(
        @Query("max") max: Int = 20,
        @Query("lang") language: String? = null,
        @Query("cat") category: String? = null
    ): SearchResponse

    // Episode endpoints

    @GET("episodes/byfeedid")
    suspend fun getEpisodesByFeedId(
        @Query("id") feedId: Long,
        @Query("max") max: Int = 50
    ): EpisodesResponse

    @GET("episodes/byid")
    suspend fun getEpisodeById(
        @Query("id") id: Long
    ): EpisodesResponse

    @GET("episodes/byguid")
    suspend fun getEpisodeByGuid(
        @Query("guid") guid: String,
        @Query("feedid") feedId: Long? = null,
        @Query("feedurl") feedUrl: String? = null
    ): EpisodesResponse

    @GET("episodes/random")
    suspend fun getRandomEpisodes(
        @Query("max") max: Int = 10,
        @Query("lang") language: String? = null,
        @Query("cat") category: String? = null
    ): RecentEpisodesResponse

    @GET("recent/episodes")
    suspend fun getRecentEpisodes(
        @Query("max") max: Int = 20,
        @Query("excludeString") excludeString: String? = null,
        @Query("before") before: Long? = null
    ): RecentEpisodesResponse

    // Categories

    @GET("categories/list")
    suspend fun getCategories(): Map<String, Any>
}
