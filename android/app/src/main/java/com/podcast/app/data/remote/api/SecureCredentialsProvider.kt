package com.podcast.app.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.podcast.app.BuildConfig
import com.podcast.app.util.DiagnosticLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic info about credential storage status.
 * Safe to expose - does not reveal actual credential values.
 */
data class CredentialDiagnostics(
    val encryptedStorageAvailable: Boolean,
    val initializationError: String?,
    val hasStoredApiKey: Boolean,
    val hasStoredApiSecret: Boolean,
    val buildConfigHasApiKey: Boolean,
    val buildConfigHasApiSecret: Boolean,
    val credentialsReady: Boolean
)

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

    companion object {
        private const val TAG = "SecureCredentials"
        private const val PREFS_FILE_NAME = "podcast_secure_prefs"
        private const val KEY_API_KEY = "podcast_index_api_key"
        private const val KEY_API_SECRET = "podcast_index_api_secret"
    }

    // Track initialization status for diagnostics
    private var initializationError: String? = null
    private var encryptedPrefsAvailable = false

    private val masterKey: MasterKey? by lazy {
        try {
            DiagnosticLogger.d(TAG, "Creating MasterKey...")
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build().also {
                    DiagnosticLogger.i(TAG, "MasterKey created successfully")
                }
        } catch (e: GeneralSecurityException) {
            val msg = "MasterKey creation failed: ${e.javaClass.simpleName} - ${e.message}"
            DiagnosticLogger.e(TAG, msg)
            initializationError = msg
            null
        } catch (e: Exception) {
            val msg = "Unexpected MasterKey error: ${e.javaClass.simpleName} - ${e.message}"
            DiagnosticLogger.e(TAG, msg)
            initializationError = msg
            null
        }
    }

    private val encryptedPrefs: SharedPreferences? by lazy {
        val key = masterKey
        if (key == null) {
            DiagnosticLogger.e(TAG, "Cannot create EncryptedSharedPreferences - MasterKey is null")
            return@lazy null
        }

        try {
            DiagnosticLogger.d(TAG, "Creating EncryptedSharedPreferences...")
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { prefs ->
                DiagnosticLogger.i(TAG, "EncryptedSharedPreferences created successfully")
                encryptedPrefsAvailable = true
                initializeFromBuildConfigIfNeeded(prefs)
            }
        } catch (e: GeneralSecurityException) {
            val msg = "EncryptedSharedPreferences failed: ${e.javaClass.simpleName} - ${e.message}"
            DiagnosticLogger.e(TAG, msg)
            initializationError = msg
            null
        } catch (e: Exception) {
            val msg = "Unexpected storage error: ${e.javaClass.simpleName} - ${e.message}"
            DiagnosticLogger.e(TAG, msg)
            initializationError = msg
            null
        }
    }

    /**
     * Auto-initialize credentials from BuildConfig on first access.
     * This allows embedding API keys at build time while storing them securely.
     */
    private fun initializeFromBuildConfigIfNeeded(prefs: SharedPreferences) {
        try {
            val existingKey = prefs.getString(KEY_API_KEY, null)
            val buildConfigKey = BuildConfig.PODCAST_INDEX_API_KEY
            val buildConfigSecret = BuildConfig.PODCAST_INDEX_API_SECRET

            DiagnosticLogger.d(TAG, "BuildConfig check: key=${if (buildConfigKey.isNotBlank()) "${buildConfigKey.length} chars" else "EMPTY"}, secret=${if (buildConfigSecret.isNotBlank()) "${buildConfigSecret.length} chars" else "EMPTY"}")

            if (existingKey.isNullOrBlank() && buildConfigKey.isNotBlank()) {
                DiagnosticLogger.i(TAG, "Initializing credentials from BuildConfig...")
                val success = prefs.edit()
                    .putString(KEY_API_KEY, buildConfigKey)
                    .putString(KEY_API_SECRET, buildConfigSecret)
                    .commit()
                if (success) {
                    DiagnosticLogger.i(TAG, "Credentials initialized from BuildConfig")
                } else {
                    DiagnosticLogger.e(TAG, "Failed to commit credentials to storage")
                }
            } else if (!existingKey.isNullOrBlank()) {
                DiagnosticLogger.d(TAG, "Existing stored credentials found (${existingKey.length} chars)")
            } else {
                DiagnosticLogger.w(TAG, "No credentials available - BuildConfig empty and no stored credentials")
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error initializing from BuildConfig", e)
        }
    }

    override fun getCredentials(): PodcastIndexCredentials? {
        val prefs = encryptedPrefs
        if (prefs == null) {
            DiagnosticLogger.e(TAG, "getCredentials: Storage unavailable - ${initializationError ?: "unknown error"}")
            return null
        }

        return try {
            val apiKey = prefs.getString(KEY_API_KEY, null)
            val apiSecret = prefs.getString(KEY_API_SECRET, null)

            if (!apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank()) {
                DiagnosticLogger.d(TAG, "getCredentials: Returning credentials (key=${apiKey.length}c, secret=${apiSecret.length}c)")
                PodcastIndexCredentials(apiKey, apiSecret)
            } else {
                DiagnosticLogger.w(TAG, "getCredentials: Incomplete - key=${if (apiKey.isNullOrBlank()) "MISSING" else "ok"}, secret=${if (apiSecret.isNullOrBlank()) "MISSING" else "ok"}")
                null
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "getCredentials failed", e)
            null
        }
    }

    override suspend fun setCredentials(credentials: PodcastIndexCredentials) {
        withContext(Dispatchers.IO) {
            val prefs = encryptedPrefs
            if (prefs == null) {
                DiagnosticLogger.e(TAG, "setCredentials: Storage unavailable")
                return@withContext
            }

            try {
                val success = prefs.edit()
                    .putString(KEY_API_KEY, credentials.apiKey)
                    .putString(KEY_API_SECRET, credentials.apiSecret)
                    .commit()
                if (success) {
                    DiagnosticLogger.i(TAG, "Credentials saved successfully")
                } else {
                    DiagnosticLogger.e(TAG, "Failed to save credentials")
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "setCredentials failed", e)
            }
        }
    }

    override suspend fun clearCredentials() {
        withContext(Dispatchers.IO) {
            val prefs = encryptedPrefs
            if (prefs == null) {
                DiagnosticLogger.e(TAG, "clearCredentials: Storage unavailable")
                return@withContext
            }

            try {
                val success = prefs.edit()
                    .remove(KEY_API_KEY)
                    .remove(KEY_API_SECRET)
                    .commit()
                if (success) {
                    DiagnosticLogger.i(TAG, "Credentials cleared")
                } else {
                    DiagnosticLogger.e(TAG, "Failed to clear credentials")
                }
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "clearCredentials failed", e)
            }
        }
    }

    /**
     * Get diagnostic information about credential storage status.
     * Safe to expose - does not reveal actual credential values.
     */
    fun getDiagnosticInfo(): CredentialDiagnostics {
        val prefs = encryptedPrefs
        val hasKey = try {
            prefs?.getString(KEY_API_KEY, null)?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
        val hasSecret = try {
            prefs?.getString(KEY_API_SECRET, null)?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
        val buildConfigHasKey = BuildConfig.PODCAST_INDEX_API_KEY.isNotBlank()
        val buildConfigHasSecret = BuildConfig.PODCAST_INDEX_API_SECRET.isNotBlank()

        return CredentialDiagnostics(
            encryptedStorageAvailable = encryptedPrefsAvailable,
            initializationError = initializationError,
            hasStoredApiKey = hasKey,
            hasStoredApiSecret = hasSecret,
            buildConfigHasApiKey = buildConfigHasKey,
            buildConfigHasApiSecret = buildConfigHasSecret,
            credentialsReady = hasKey && hasSecret
        )
    }
}
