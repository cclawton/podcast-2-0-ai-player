package com.podcast.app.api.claude

import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.remote.models.PodcastFeed
import com.podcast.app.privacy.NetworkFeature
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AISearchService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val claudeApiKeyManager: ClaudeApiKeyManager,
    private val podcastIndexApi: PodcastIndexApi,
    private val privacyManager: PrivacyManager
) {
    private companion object {
        const val TAG = "AISearchService"
        const val API_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL = "claude-3-haiku-20240307"
        const val MAX_QUERY_LENGTH = 500
        const val MAX_SEARCH_RESULTS = 20
        const val MAX_EPISODES_PER_PODCAST = 5
        const val MAX_TOTAL_EPISODES = 10
    }

    /**
     * Data class representing an episode result from AI search.
     * Contains episode details plus podcast context for display.
     */
    data class AISearchEpisode(
        val id: Long,
        val title: String,
        val description: String?,
        val audioUrl: String,
        val audioDuration: Int?,
        val publishedAt: Long?,
        val imageUrl: String?,
        val podcastId: Long,
        val podcastTitle: String,
        val podcastImageUrl: String?
    )

    sealed class AISearchResult {
        data class Success(
            val podcasts: List<Podcast>,
            val episodes: List<AISearchEpisode>,
            val searchType: String,
            val interpretedQuery: String,
            val explanation: String
        ) : AISearchResult()
        data class Error(val message: String) : AISearchResult()
        data object ApiKeyNotConfigured : AISearchResult()
        data object ClaudeApiDisabled : AISearchResult()
    }

    suspend fun isAvailable(): Boolean {
        if (!claudeApiKeyManager.hasApiKey()) return false
        return privacyManager.isFeatureAllowed(NetworkFeature.CLAUDE_API)
    }

    fun isApiKeyConfigured(): Boolean = claudeApiKeyManager.hasApiKey()

    suspend fun search(naturalLanguageQuery: String): AISearchResult = withContext(Dispatchers.IO) {
        DiagnosticLogger.i(TAG, "AI search initiated")
        val sanitizedQuery = sanitizeQuery(naturalLanguageQuery) ?: return@withContext AISearchResult.Error("Invalid search query")
        val apiKey = claudeApiKeyManager.getApiKey()
        if (apiKey.isNullOrBlank()) return@withContext AISearchResult.ApiKeyNotConfigured
        if (!privacyManager.isFeatureAllowed(NetworkFeature.CLAUDE_API)) return@withContext AISearchResult.ClaudeApiDisabled
        try {
            val interpretation = interpretQuery(apiKey, sanitizedQuery) ?: return@withContext AISearchResult.Error("Failed to interpret query")
            val podcasts = executeSearch(interpretation)
            val episodes = fetchEpisodesFromPodcasts(podcasts)
            AISearchResult.Success(podcasts, episodes, interpretation.searchType, interpretation.query, interpretation.explanation)
        } catch (e: java.net.UnknownHostException) { AISearchResult.Error("No internet connection")
        } catch (e: java.net.SocketTimeoutException) { AISearchResult.Error("Connection timeout")
        } catch (e: Exception) { AISearchResult.Error("Search failed") }
    }

    private fun sanitizeQuery(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_QUERY_LENGTH) return null
        return trimmed.replace(Regex("[<>;&|]"), "").trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun interpretQuery(apiKey: String, query: String): QueryInterpretation? {
        val systemPrompt = "You are a podcast search assistant. Respond with ONLY a JSON object with fields: search_type (byterm/byperson/bytitle), query, explanation"
        val requestBody = JSONObject().apply {
            put("model", MODEL); put("max_tokens", 256); put("system", systemPrompt)
            put("messages", JSONArray().apply { put(JSONObject().apply { put("role", "user"); put("content", query) }) })
        }
        val request = Request.Builder().url(API_URL).addHeader("x-api-key", apiKey).addHeader("anthropic-version", ANTHROPIC_VERSION).addHeader("content-type", "application/json").post(requestBody.toString().toRequestBody("application/json".toMediaType())).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        return parseClaudeResponse(response.body?.string() ?: return null)
    }

    private fun parseClaudeResponse(responseBody: String): QueryInterpretation? = try {
        val json = JSONObject(responseBody); val content = json.getJSONArray("content")
        if (content.length() == 0) null else {
            val text = content.getJSONObject(0).getString("text").trim(); val parsed = JSONObject(text)
            QueryInterpretation(parsed.getString("search_type"), parsed.getString("query"), parsed.optString("explanation", ""))
        }
    } catch (e: Exception) { null }

    private suspend fun executeSearch(interpretation: QueryInterpretation): List<Podcast> {
        val response = when (interpretation.searchType) {
            "byperson" -> podcastIndexApi.searchByPerson(interpretation.query, MAX_SEARCH_RESULTS)
            "bytitle" -> podcastIndexApi.searchByTitle(interpretation.query, MAX_SEARCH_RESULTS)
            else -> podcastIndexApi.searchByTerm(interpretation.query, MAX_SEARCH_RESULTS)
        }
        return response.feeds.map { it.toPodcast() }
    }

    /**
     * Fetches recent episodes from the top podcasts in the search results.
     * Uses concurrent API calls for better performance.
     */
    private suspend fun fetchEpisodesFromPodcasts(podcasts: List<Podcast>): List<AISearchEpisode> = withContext(Dispatchers.IO) {
        if (podcasts.isEmpty()) return@withContext emptyList()

        // Fetch episodes from top 5 podcasts concurrently
        val podcastsToFetch = podcasts.take(5)
        val episodesDeferred = podcastsToFetch.map { podcast ->
            async {
                try {
                    val response = podcastIndexApi.getEpisodesByFeedId(
                        feedId = podcast.podcastIndexId,
                        max = MAX_EPISODES_PER_PODCAST
                    )
                    response.items.map { it.toAISearchEpisode(podcast) }
                } catch (e: Exception) {
                    DiagnosticLogger.w(TAG, "Failed to fetch episodes for podcast $${podcast.podcastIndexId}: $${e.message}")
                    emptyList()
                }
            }
        }

        // Collect all episodes, sort by date, and take top results
        episodesDeferred.awaitAll()
            .flatten()
            .sortedByDescending { it.publishedAt ?: 0 }
            .take(MAX_TOTAL_EPISODES)
    }

    private fun PodcastFeed.toPodcast(): Podcast = Podcast(
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

    private fun EpisodeItem.toAISearchEpisode(podcast: Podcast): AISearchEpisode = AISearchEpisode(
        id = id,
        title = title,
        description = description,
        audioUrl = enclosureUrl,
        audioDuration = duration,
        publishedAt = datePublished?.let { it * 1000 }, // Convert seconds to milliseconds
        imageUrl = image ?: feedImage,
        podcastId = podcast.podcastIndexId,
        podcastTitle = podcast.title,
        podcastImageUrl = podcast.imageUrl
    )

    private data class QueryInterpretation(val searchType: String, val query: String, val explanation: String)
}
