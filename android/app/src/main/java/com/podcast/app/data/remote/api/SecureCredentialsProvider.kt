package com.podcast.app.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.podcast.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure credentials provider using EncryptedSharedPreferences.
 *
 * API keys are stored encrypted at rest using Android Keystore.
 * On first access, credentials are auto-initialized from BuildConfig.
 * This is compliant with GrapheneOS security requirements.
 */
@Singleton
class SecureCredentialsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : PodcastIndexCredentialsProvider {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefs ->
            initializeFromBuildConfigIfNeeded(prefs)
        }
    }

    /**
     * Auto-initialize credentials from BuildConfig on first access.
     * This allows embedding API keys at build time while storing them securely.
     */
    private fun initializeFromBuildConfigIfNeeded(prefs: SharedPreferences) {
        val existingKey = prefs.getString(KEY_API_KEY, null)
        if (existingKey.isNullOrBlank() && BuildConfig.PODCAST_INDEX_API_KEY.isNotBlank()) {
            prefs.edit()
                .putString(KEY_API_KEY, BuildConfig.PODCAST_INDEX_API_KEY)
                .putString(KEY_API_SECRET, BuildConfig.PODCAST_INDEX_API_SECRET)
                .apply()
        }
    }

    override fun getCredentials(): PodcastIndexCredentials? {
        val apiKey = encryptedPrefs.getString(KEY_API_KEY, null)
        val apiSecret = encryptedPrefs.getString(KEY_API_SECRET, null)

        return if (!apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank()) {
            PodcastIndexCredentials(apiKey, apiSecret)
        } else {
            null
        }
    }

    override suspend fun setCredentials(credentials: PodcastIndexCredentials) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_API_KEY, credentials.apiKey)
                .putString(KEY_API_SECRET, credentials.apiSecret)
                .apply()
        }
    }

    override suspend fun clearCredentials() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .remove(KEY_API_KEY)
                .remove(KEY_API_SECRET)
                .apply()
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "podcast_secure_prefs"
        private const val KEY_API_KEY = "podcast_index_api_key"
        private const val KEY_API_SECRET = "podcast_index_api_secret"
    }
}
