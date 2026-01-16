package com.podcast.app.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.inject.Inject

/**
 * OkHttp interceptor for Podcast Index API authentication.
 *
 * Authentication requires:
 * - X-Auth-Key: API key
 * - X-Auth-Date: Unix epoch timestamp
 * - Authorization: SHA-1 hash of (apiKey + apiSecret + timestamp)
 * - User-Agent: Application identifier
 */
class PodcastIndexAuthInterceptor @Inject constructor(
    private val credentialsProvider: PodcastIndexCredentialsProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = credentialsProvider.getCredentials()

        // Skip auth if credentials not configured
        if (credentials == null) {
            return chain.proceed(chain.request())
        }

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val authHash = generateAuthHash(credentials.apiKey, credentials.apiSecret, timestamp)

        val authenticatedRequest = chain.request().newBuilder()
            .addHeader("X-Auth-Key", credentials.apiKey)
            .addHeader("X-Auth-Date", timestamp)
            .addHeader("Authorization", authHash)
            .addHeader("User-Agent", "PodcastApp/1.0")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private fun generateAuthHash(apiKey: String, apiSecret: String, timestamp: String): String {
        val input = "$apiKey$apiSecret$timestamp"
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Provides Podcast Index API credentials from secure storage.
 */
interface PodcastIndexCredentialsProvider {
    fun getCredentials(): PodcastIndexCredentials?
    suspend fun setCredentials(credentials: PodcastIndexCredentials)
    suspend fun clearCredentials()
}

data class PodcastIndexCredentials(
    val apiKey: String,
    val apiSecret: String
)
