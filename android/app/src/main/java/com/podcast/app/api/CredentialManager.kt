package com.podcast.app.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure credential storage for Podcast Index API keys.
 *
 * Uses Android's EncryptedSharedPreferences with AES-256-GCM encryption
 * backed by the Android Keystore system.
 *
 * SECURITY REQUIREMENTS (from CLAUDE.md):
 * - Uses EncryptedSharedPreferences for secure storage
 * - Never exposes credentials in logs or error messages
 * - Supports secure credential clearing
 * - Falls back gracefully if encryption fails
 *
 * PRIVACY NOTE: Credentials are stored locally and never transmitted
 * except to the Podcast Index API for authentication.
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CredentialManager"

        private const val PREFS_FILE_NAME = "podcast_api_credentials"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_CREDENTIALS_CONFIGURED = "credentials_configured"

        // Validation constants
        private const val MIN_API_KEY_LENGTH = 10
        private const val MIN_API_SECRET_LENGTH = 10
    }

    private val encryptedPrefs: SharedPreferences? by lazy {
        createEncryptedPreferences()
    }

    /**
     * Creates encrypted shared preferences using MasterKey with AES-256-GCM.
     * Falls back to null if encryption is unavailable (device-specific issues).
     */
    private fun createEncryptedPreferences(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Log without sensitive details
            Log.e(TAG, "Failed to create encrypted preferences: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Stores API credentials securely.
     *
     * @param apiKey Podcast Index API key
     * @param apiSecret Podcast Index API secret
     * @return true if credentials were stored successfully
     */
    fun setCredentials(apiKey: String, apiSecret: String): Boolean {
        // Validate input
        if (!validateCredentials(apiKey, apiSecret)) {
            Log.w(TAG, "Invalid credentials format")
            return false
        }

        val prefs = encryptedPrefs ?: run {
            Log.e(TAG, "Encrypted storage not available")
            return false
        }

        return try {
            prefs.edit()
                .putString(KEY_API_KEY, apiKey.trim())
                .putString(KEY_API_SECRET, apiSecret.trim())
                .putBoolean(KEY_CREDENTIALS_CONFIGURED, true)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credentials: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Retrieves the stored API key.
     *
     * @return API key or null if not configured
     */
    fun getApiKey(): String? {
        return encryptedPrefs?.getString(KEY_API_KEY, null)
    }

    /**
     * Retrieves the stored API secret.
     *
     * @return API secret or null if not configured
     */
    fun getApiSecret(): String? {
        return encryptedPrefs?.getString(KEY_API_SECRET, null)
    }

    /**
     * Checks if credentials have been configured.
     *
     * @return true if both API key and secret are stored
     */
    fun hasCredentials(): Boolean {
        val prefs = encryptedPrefs ?: return false
        return prefs.getBoolean(KEY_CREDENTIALS_CONFIGURED, false) &&
               !prefs.getString(KEY_API_KEY, null).isNullOrBlank() &&
               !prefs.getString(KEY_API_SECRET, null).isNullOrBlank()
    }

    /**
     * Clears all stored credentials.
     *
     * @return true if credentials were cleared successfully
     */
    fun clearCredentials(): Boolean {
        val prefs = encryptedPrefs ?: return false

        return try {
            prefs.edit()
                .remove(KEY_API_KEY)
                .remove(KEY_API_SECRET)
                .putBoolean(KEY_CREDENTIALS_CONFIGURED, false)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Validates credential format without checking against API.
     *
     * @param apiKey API key to validate
     * @param apiSecret API secret to validate
     * @return true if credentials appear valid
     */
    private fun validateCredentials(apiKey: String, apiSecret: String): Boolean {
        // Basic format validation
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return false
        }

        if (apiKey.trim().length < MIN_API_KEY_LENGTH) {
            return false
        }

        if (apiSecret.trim().length < MIN_API_SECRET_LENGTH) {
            return false
        }

        // Check for obviously invalid characters (prevent injection)
        val validPattern = Regex("^[a-zA-Z0-9_-]+$")
        if (!validPattern.matches(apiKey.trim()) || !validPattern.matches(apiSecret.trim())) {
            return false
        }

        return true
    }

    /**
     * Validates credentials format for user input feedback.
     * Returns a user-friendly error message or null if valid.
     */
    fun validateCredentialsForUser(apiKey: String, apiSecret: String): String? {
        if (apiKey.isBlank()) {
            return "API key is required"
        }
        if (apiSecret.isBlank()) {
            return "API secret is required"
        }
        if (apiKey.trim().length < MIN_API_KEY_LENGTH) {
            return "API key appears too short"
        }
        if (apiSecret.trim().length < MIN_API_SECRET_LENGTH) {
            return "API secret appears too short"
        }

        val validPattern = Regex("^[a-zA-Z0-9_-]+$")
        if (!validPattern.matches(apiKey.trim())) {
            return "API key contains invalid characters"
        }
        if (!validPattern.matches(apiSecret.trim())) {
            return "API secret contains invalid characters"
        }

        return null
    }

    /**
     * Checks if secure storage is available on this device.
     */
    fun isSecureStorageAvailable(): Boolean {
        return encryptedPrefs != null
    }
}
