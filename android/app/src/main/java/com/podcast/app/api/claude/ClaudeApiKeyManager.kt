package com.podcast.app.api.claude

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage manager for Claude API key.
 *
 * Uses EncryptedSharedPreferences with Android Keystore for secure storage.
 * The API key is never logged or exposed in plain text.
 */
@Singleton
class ClaudeApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_FILE = "claude_api_prefs"
        const val KEY_API_KEY = "api_key"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get the stored API key.
     * Returns null if no key is stored.
     */
    fun getApiKey(): String? = sharedPrefs.getString(KEY_API_KEY, null)

    /**
     * Save the API key securely.
     */
    fun saveApiKey(apiKey: String) {
        sharedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    /**
     * Clear the stored API key.
     */
    fun clearApiKey() {
        sharedPrefs.edit().remove(KEY_API_KEY).apply()
    }

    /**
     * Check if an API key is stored.
     */
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    /**
     * Get a masked version of the API key for display.
     * Shows first 10 and last 4 characters.
     */
    fun getMaskedApiKey(): String? {
        val key = getApiKey() ?: return null
        if (key.length <= 14) return "••••••••"
        return "${key.take(10)}••••${key.takeLast(4)}"
    }
}
