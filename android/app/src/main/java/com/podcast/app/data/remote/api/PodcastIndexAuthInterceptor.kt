package com.podcast.app.data.remote.api

import com.podcast.app.util.DiagnosticLogger
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.net.ssl.SSLException

/**
 * Exception thrown when Podcast Index API authentication fails.
 * Extends IOException so Retrofit treats it as a network error.
 */
class PodcastIndexAuthException(message: String) : IOException(message) {
    companion object {
        fun missingCredentials() = PodcastIndexAuthException(
            "API credentials not configured"
        )

        fun invalidCredentials() = PodcastIndexAuthException(
            "API authentication failed - check credentials"
        )
    }
}

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

    companion object {
        private const val TAG = "PodcastIndexAuth"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method

        DiagnosticLogger.d(TAG, "Request: $method ${request.url.encodedPath}")

        val credentials = credentialsProvider.getCredentials()

        // If no credentials, proceed without auth - API will return 401
        if (credentials == null || credentials.apiKey.isBlank() || credentials.apiSecret.isBlank()) {
            DiagnosticLogger.w(TAG, "No API credentials - request will fail with 401")
            return chain.proceed(request)
        }

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val authHash = generateAuthHash(credentials.apiKey, credentials.apiSecret, timestamp)

        DiagnosticLogger.d(TAG, "Auth headers added (key=${credentials.apiKey.length}c, ts=$timestamp)")

        val authenticatedRequest = request.newBuilder()
            .addHeader("X-Auth-Key", credentials.apiKey)
            .addHeader("X-Auth-Date", timestamp)
            .addHeader("Authorization", authHash)
            .addHeader("User-Agent", "PodcastApp/1.0")
            .build()

        return try {
            val response = chain.proceed(authenticatedRequest)
            val code = response.code

            when {
                code in 200..299 -> {
                    DiagnosticLogger.i(TAG, "Response: $code OK for ${request.url.encodedPath}")
                }
                code == 401 -> {
                    DiagnosticLogger.e(TAG, "Response: 401 Unauthorized - API credentials rejected")
                }
                code == 403 -> {
                    DiagnosticLogger.e(TAG, "Response: 403 Forbidden - access denied")
                }
                code == 429 -> {
                    DiagnosticLogger.w(TAG, "Response: 429 Rate limited")
                }
                code in 400..499 -> {
                    DiagnosticLogger.w(TAG, "Response: $code Client error for ${request.url.encodedPath}")
                }
                code in 500..599 -> {
                    DiagnosticLogger.e(TAG, "Response: $code Server error for ${request.url.encodedPath}")
                }
                else -> {
                    DiagnosticLogger.d(TAG, "Response: $code for ${request.url.encodedPath}")
                }
            }

            response
        } catch (e: UnknownHostException) {
            DiagnosticLogger.e(TAG, "DNS resolution failed: ${e.message}")
            throw e
        } catch (e: SocketTimeoutException) {
            DiagnosticLogger.e(TAG, "Connection timeout: ${e.message}")
            throw e
        } catch (e: SSLException) {
            DiagnosticLogger.e(TAG, "SSL/TLS error: ${e.message}")
            throw e
        } catch (e: IOException) {
            DiagnosticLogger.e(TAG, "Network error: ${e.javaClass.simpleName} - ${e.message}")
            throw e
        }
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
