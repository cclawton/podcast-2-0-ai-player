package com.podcast.app.api

import android.content.Context
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.api.PodcastIndexAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing Podcast Index API dependencies.
 *
 * Configuration follows security and privacy requirements:
 * - Secure auth via [PodcastIndexAuthInterceptor]
 * - Response caching for offline support
 * - Reasonable timeouts to avoid hanging
 * - Debug logging only in debug builds
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    private const val BASE_URL = "https://api.podcastindex.org/api/1.0/"
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    /**
     * Provides JSON serializer configured for Podcast Index API responses.
     *
     * - ignoreUnknownKeys: API may add fields we don't model
     * - isLenient: Handle minor formatting issues
     * - coerceInputValues: Convert null to default values for non-nullable fields
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Provides HTTP cache for offline support.
     */
    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, CACHE_SIZE_BYTES)
    }

    /**
     * Provides logging interceptor for debug builds.
     * In release builds, logging is disabled for security.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (com.podcast.app.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Redact auth headers in logs
            redactHeader("X-Auth-Key")
            redactHeader("Authorization")
        }
    }

    /**
     * Provides configured OkHttpClient with:
     * - Authentication interceptor
     * - Response caching
     * - Timeouts
     * - Debug logging (debug builds only)
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: PodcastIndexAuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        cache: Cache
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Retry on connection failure
            .retryOnConnectionFailure(true)
            // Follow redirects (API may redirect)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Provides Retrofit instance configured for Podcast Index API.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /**
     * Provides Podcast Index API interface.
     */
    @Provides
    @Singleton
    fun providePodcastIndexApi(retrofit: Retrofit): PodcastIndexApi {
        return retrofit.create(PodcastIndexApi::class.java)
    }
}
