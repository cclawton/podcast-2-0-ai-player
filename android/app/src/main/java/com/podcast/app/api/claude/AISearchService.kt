package com.podcast.app.api.claude

import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.remote.models.PersonSearchItem
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
        const val MODEL = "claude-haiku-4-5-20251001"
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

            // GH#38: Handle byperson differently - it returns episodes directly, not podcast feeds
            val (podcasts, episodes) = if (interpretation.searchType == "byperson") {
                executeByPersonSearch(interpretation)
            } else {
                executeStandardSearch(interpretation)
            }

            AISearchResult.Success(podcasts, episodes, interpretation.searchType, interpretation.query, interpretation.explanation)
        } catch (e: java.net.UnknownHostException) { AISearchResult.Error("No internet connection")
        } catch (e: java.net.SocketTimeoutException) { AISearchResult.Error("Connection timeout")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Search failed: ${e.message}")
            AISearchResult.Error("Search failed: ${e.message}")
        }
    }

    private fun sanitizeQuery(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_QUERY_LENGTH) return null
        return trimmed.replace(Regex("[<>;&|]"), "").trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun interpretQuery(apiKey: String, query: String): QueryInterpretation? {
        val systemPrompt = """You are a podcast search assistant for the PodcastIndex API. Your job is to interpret natural language queries and extract optimal search parameters.

AVAILABLE SEARCH TYPES:
- "byperson": Search for podcasts featuring a specific person (guest, host, or author). Use when the query mentions a person's name.
- "bytitle": Search podcast titles. Use when looking for a specific podcast show.
- "byterm": General keyword search across all podcast metadata. Use for topics, subjects, or when unsure.

CRITICAL RULES:
1. Extract ONLY the key search term (person name, podcast title, or topic) - NOT the full query phrase
2. Remove filler words like "recent", "latest", "episodes", "podcasts", "featuring", "with", "about", "find", "show me"
3. For person searches, use just the person's name (e.g., "David Deutsch" not "recent podcasts with David Deutsch")
4. For podcast searches, use just the podcast name (e.g., "Joe Rogan" not "joe rogans recent guests")

EXAMPLES:
- "joe rogans recent guests" → {"search_type": "bytitle", "query": "Joe Rogan", "explanation": "Searching for the Joe Rogan podcast to find recent episodes and guests"}
- "recent podcasts with david deutsch" → {"search_type": "byperson", "query": "David Deutsch", "explanation": "Searching for podcast episodes featuring David Deutsch as a guest"}
- "podcasts about quantum computing" → {"search_type": "byterm", "query": "quantum computing", "explanation": "Searching for podcasts about quantum computing"}
- "find the lex fridman podcast" → {"search_type": "bytitle", "query": "Lex Fridman", "explanation": "Searching for the Lex Fridman podcast"}
- "episodes with elon musk" → {"search_type": "byperson", "query": "Elon Musk", "explanation": "Searching for podcast episodes featuring Elon Musk"}

Respond with ONLY a JSON object with fields: search_type, query, explanation"""
        val requestBody = JSONObject().apply {
            put("model", MODEL); put("max_tokens", 256); put("system", systemPrompt)
            put("messages", JSONArray().apply { put(JSONObject().apply { put("role", "user"); put("content", query) }) })
        }
        val request = Request.Builder().url(API_URL).addHeader("x-api-key", apiKey).addHeader("anthropic-version", ANTHROPIC_VERSION).addHeader("content-type", "application/json").post(requestBody.toString().toRequestBody("application/json".toMediaType())).build()
        DiagnosticLogger.d(TAG, "Sending query to Claude: $query")
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            DiagnosticLogger.w(TAG, "Claude API error: ${response.code} - ${response.message}")
            return null
        }
        val responseBody = response.body?.string() ?: return null
        val interpretation = parseClaudeResponse(responseBody)
        DiagnosticLogger.i(TAG, "Claude interpretation: type=${interpretation?.searchType}, query=${interpretation?.query}")
        return interpretation
    }

    private fun parseClaudeResponse(responseBody: String): QueryInterpretation? = try {
        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content")
        if (content.length() == 0) null else {
            var text = content.getJSONObject(0).getString("text").trim()
            // Strip markdown code blocks if present
            text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            // Find JSON object boundaries
            val startIdx = text.indexOf('{')
            val endIdx = text.lastIndexOf('}')
            if (startIdx >= 0 && endIdx > startIdx) {
                text = text.substring(startIdx, endIdx + 1)
            }
            val parsed = JSONObject(text)
            val searchType = parsed.optString("search_type", "byterm")
            val query = parsed.optString("query", "")
            val explanation = parsed.optString("explanation", "")
            if (query.isNotBlank()) {
                QueryInterpretation(searchType, query, explanation)
            } else null
        }
    } catch (e: Exception) {
        DiagnosticLogger.w(TAG, "Failed to parse Claude response: ${e.message}")
        null
    }

    /**
     * GH#38: Execute byperson search which returns episodes directly (not podcast feeds).
     * The byperson endpoint returns items (episodes) with embedded feed metadata.
     * Returns both unique podcasts extracted from episodes and the episodes themselves.
     */
    private suspend fun executeByPersonSearch(interpretation: QueryInterpretation): Pair<List<Podcast>, List<AISearchEpisode>> {
        DiagnosticLogger.d(TAG, "Executing PodcastIndex byperson search: query='${interpretation.query}'")

        val response = podcastIndexApi.searchByPerson(interpretation.query, MAX_SEARCH_RESULTS)
        val personItems = response.items
        DiagnosticLogger.i(TAG, "PodcastIndex byperson returned ${personItems.size} episodes")

        if (personItems.isEmpty()) {
            // Fallback to byterm search if byperson returns empty
            // byperson requires Podcast 2.0 person tags which are rarely implemented
            DiagnosticLogger.i(TAG, "byperson returned empty, falling back to byterm")
            return executeStandardSearch(interpretation.copy(searchType = "byterm"))
        }

        // Convert episodes directly from byperson response
        val episodes = personItems
            .take(MAX_TOTAL_EPISODES)
            .map { it.toAISearchEpisode() }
            .sortedByDescending { it.publishedAt ?: 0 }

        // Extract unique podcasts from the episode results
        val uniquePodcasts = personItems
            .distinctBy { it.feedId }
            .take(MAX_SEARCH_RESULTS)
            .mapNotNull { item ->
                // Create a minimal Podcast from the embedded feed metadata
                if (item.feedTitle != null) {
                    Podcast(
                        podcastIndexId = item.feedId,
                        title = item.feedTitle,
                        feedUrl = item.feedUrl ?: "",
                        imageUrl = item.feedImage,
                        description = null,
                        language = item.feedLanguage ?: "en",
                        explicit = false,
                        author = item.feedAuthor,
                        episodeCount = 0,
                        websiteUrl = null,
                        podcastGuid = null,
                        isSubscribed = false
                    )
                } else null
            }

        DiagnosticLogger.i(TAG, "byperson extracted ${uniquePodcasts.size} unique podcasts, ${episodes.size} episodes")
        return Pair(uniquePodcasts, episodes)
    }

    /**
     * Execute standard search (bytitle or byterm) which returns podcast feeds.
     * Then fetches episodes from the top podcasts.
     */
    private suspend fun executeStandardSearch(interpretation: QueryInterpretation): Pair<List<Podcast>, List<AISearchEpisode>> {
        DiagnosticLogger.d(TAG, "Executing PodcastIndex ${interpretation.searchType} search: query='${interpretation.query}'")

        val response = when (interpretation.searchType) {
            "bytitle" -> podcastIndexApi.searchByTitle(interpretation.query, MAX_SEARCH_RESULTS)
            else -> podcastIndexApi.searchByTerm(interpretation.query, MAX_SEARCH_RESULTS)
        }
        var podcasts = response.feeds.map { it.toPodcast() }
        DiagnosticLogger.i(TAG, "PodcastIndex ${interpretation.searchType} returned ${podcasts.size} podcasts")

        // GH#37: Fallback to byterm if bytitle returns empty
        // bytitle requires near-exact title match
        // byterm is more flexible and searches title, author, owner
        if (podcasts.isEmpty() && interpretation.searchType == "bytitle") {
            DiagnosticLogger.i(TAG, "bytitle returned empty, falling back to byterm")
            val fallbackResponse = podcastIndexApi.searchByTerm(interpretation.query, MAX_SEARCH_RESULTS)
            podcasts = fallbackResponse.feeds.map { it.toPodcast() }
            DiagnosticLogger.i(TAG, "Fallback byterm returned ${podcasts.size} podcasts")
        }

        // Fetch episodes from top podcasts
        val episodes = fetchEpisodesFromPodcasts(podcasts)

        return Pair(podcasts, episodes)
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

    /**
     * GH#38: Convert PersonSearchItem (from byperson endpoint) to AISearchEpisode.
     * PersonSearchItem has embedded feed metadata so we don't need a separate Podcast object.
     */
    private fun PersonSearchItem.toAISearchEpisode(): AISearchEpisode = AISearchEpisode(
        id = id,
        title = title,
        description = description,
        audioUrl = enclosureUrl,
        audioDuration = duration,
        publishedAt = datePublished?.let { it * 1000 }, // Convert seconds to milliseconds
        imageUrl = image ?: feedImage,
        podcastId = feedId,
        podcastTitle = feedTitle ?: "Unknown Podcast",
        podcastImageUrl = feedImage
    )

    private data class QueryInterpretation(val searchType: String, val query: String, val explanation: String)
}
