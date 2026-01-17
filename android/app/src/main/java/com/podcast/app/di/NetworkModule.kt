package com.podcast.app.di

import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.api.PodcastIndexAuthInterceptor
import com.podcast.app.data.remote.api.PodcastIndexCredentialsProvider
import com.podcast.app.data.remote.api.SecureCredentialsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the plain OkHttpClient used for RSS feed fetching.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RssHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.podcastindex.org/api/1.0/"

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: PodcastIndexAuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePodcastIndexApi(retrofit: Retrofit): PodcastIndexApi {
        return retrofit.create(PodcastIndexApi::class.java)
    }

    /**
     * Provides a plain OkHttpClient for fetching RSS feeds.
     * This client does not include the Podcast Index auth interceptor.
     */
    @Provides
    @Singleton
    @RssHttpClient
    fun provideRssHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialsModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsProvider(
        impl: SecureCredentialsProvider
    ): PodcastIndexCredentialsProvider
}
