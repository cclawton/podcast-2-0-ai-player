package com.podcast.app.api.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Claude API communication.
 *
 * Security considerations:
 * - API key is passed per-call, not stored in client
 * - No logging of API key or full responses
 * - Proper timeout handling
 */
@Singleton
class ClaudeApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        const val API_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val TEST_MODEL = "claude-3-haiku-20240307"
    }

    /**
     * Test the API connection with a minimal request.
     *
     * @param apiKey The API key to test
     * @return Result with success/failure and optional error message
     */
    suspend fun testConnection(apiKey: String): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", TEST_MODEL)
                put("max_tokens", 10)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Say 'ok'")
                    })
                })
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    Result.success(ConnectionTestResult(true, "Connection successful"))
                }
                response.code == 401 -> {
                    Result.success(ConnectionTestResult(false, "Invalid API key"))
                }
                response.code == 429 -> {
                    Result.success(ConnectionTestResult(false, "Rate limit exceeded"))
                }
                else -> {
                    Result.success(ConnectionTestResult(false, "API error: ${response.code}"))
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Result.success(ConnectionTestResult(false, "No internet connection"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.success(ConnectionTestResult(false, "Connection timeout"))
        } catch (e: Exception) {
            Result.success(ConnectionTestResult(false, "Connection failed: ${e.message}"))
        }
    }

    /**
     * Test the API with simple natural language queries to demonstrate LLM functionality.
     *
     * GH#31: Enhanced API test that runs actual NL queries after connection verification.
     *
     * @param apiKey The API key to test
     * @return Result with connection status and query/response pairs
     */
    suspend fun testWithQueries(apiKey: String): Result<LLMTestResult> = withContext(Dispatchers.IO) {
        // First, verify basic connection
        val connectionResult = testConnection(apiKey).getOrNull()
        if (connectionResult == null || !connectionResult.success) {
            return@withContext Result.success(
                LLMTestResult(
                    connectionSuccess = false,
                    connectionMessage = connectionResult?.message ?: "Connection failed",
                    queryResponses = emptyList()
                )
            )
        }

        // Run simple natural language test queries
        val testQueries = listOf(
            "What color is the sun? Answer in one sentence.",
            "Why is the sky blue? Answer in one sentence."
        )

        val responses = mutableListOf<QueryResponse>()

        for (query in testQueries) {
            try {
                val response = sendQuery(apiKey, query)
                responses.add(QueryResponse(query = query, response = response))
            } catch (e: Exception) {
                responses.add(QueryResponse(query = query, response = "Error: ${e.message}"))
            }
        }

        Result.success(
            LLMTestResult(
                connectionSuccess = true,
                connectionMessage = "Connection successful",
                queryResponses = responses
            )
        )
    }

    /**
     * Send a single query to Claude and get the response.
     *
     * @param apiKey The API key
     * @param query The user query
     * @return The assistant's response text
     */
    private suspend fun sendQuery(apiKey: String, query: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", TEST_MODEL)
            put("max_tokens", 100)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", query)
                })
            })
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content")
        if (content.length() > 0) {
            content.getJSONObject(0).getString("text")
        } else {
            "No response"
        }
    }
}

/**
 * Result of a connection test.
 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String
)

/**
 * A single query/response pair from the LLM test.
 */
data class QueryResponse(
    val query: String,
    val response: String
)

/**
 * Result of the enhanced LLM test with natural language queries.
 */
data class LLMTestResult(
    val connectionSuccess: Boolean,
    val connectionMessage: String,
    val queryResponses: List<QueryResponse>
)
