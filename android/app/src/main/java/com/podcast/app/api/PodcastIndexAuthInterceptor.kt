package com.podcast.app.api

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that adds Podcast Index API authentication headers.
 *
 * Authentication follows the Podcast Index API specification:
 * - X-Auth-Date: Current Unix timestamp
 * - X-Auth-Key: API Key
 * - Authorization: SHA-1 hash of (API_KEY + API_SECRET + timestamp)
 * - User-Agent: App identifier
 *
 * API credentials are retrieved securely from [CredentialManager].
 *
 * SECURITY NOTE: This interceptor will skip authentication if credentials
 * are not configured, allowing the app to function (with limited features)
 * without API access.
 */
@Singleton
class PodcastIndexAuthInterceptor @Inject constructor(
    private val credentialManager: CredentialManager
) : Interceptor {

    companion object {
        private const val HEADER_AUTH_DATE = "X-Auth-Date"
        private const val HEADER_AUTH_KEY = "X-Auth-Key"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_USER_AGENT = "User-Agent"

        private const val USER_AGENT_VALUE = "PodcastApp/1.0 (GrapheneOS)"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Check if credentials are available
        val apiKey = credentialManager.getApiKey()
        val apiSecret = credentialManager.getApiSecret()

        // If no credentials, proceed without auth (will likely fail, but allows offline testing)
        if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank()) {
            return chain.proceed(
                originalRequest.newBuilder()
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .build()
            )
        }

        // Generate timestamp
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        // Generate authorization hash: SHA-1(apiKey + apiSecret + timestamp)
        val authHash = generateAuthHash(apiKey, apiSecret, timestamp)

        // Build request with auth headers
        val authenticatedRequest = originalRequest.newBuilder()
            .header(HEADER_AUTH_DATE, timestamp)
            .header(HEADER_AUTH_KEY, apiKey)
            .header(HEADER_AUTHORIZATION, authHash)
            .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
            .build()

        return chain.proceed(authenticatedRequest)
    }

    /**
     * Generates the SHA-1 authorization hash as required by Podcast Index API.
     *
     * @param apiKey The API key
     * @param apiSecret The API secret
     * @param timestamp Unix timestamp as string
     * @return Hex-encoded SHA-1 hash
     */
    private fun generateAuthHash(apiKey: String, apiSecret: String, timestamp: String): String {
        val input = "$apiKey$apiSecret$timestamp"
        val messageDigest = MessageDigest.getInstance("SHA-1")
        val hashBytes = messageDigest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result wrapper for API operations with proper error handling.
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(
        val code: Int? = null,
        val message: String,
        val exception: Throwable? = null
    ) : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception ?: RuntimeException(message)
    }

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Error) -> Unit): ApiResult<T> {
        if (this is Error) action(this)
        return this
    }
}

/**
 * Extension function to convert Retrofit Response to ApiResult.
 */
suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): ApiResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(response.code(), "Empty response body")
            }
        } else {
            ApiResult.Error(
                code = response.code(),
                message = response.errorBody()?.string() ?: "Unknown error"
            )
        }
    } catch (e: Exception) {
        ApiResult.Error(
            message = e.localizedMessage ?: "Network error",
            exception = e
        )
    }
}
