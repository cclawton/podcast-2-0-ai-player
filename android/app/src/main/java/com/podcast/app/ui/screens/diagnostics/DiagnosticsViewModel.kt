package com.podcast.app.ui.screens.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.remote.api.CredentialDiagnostics
import com.podcast.app.data.remote.api.SecureCredentialsProvider
import com.podcast.app.privacy.PrivacyManager
import com.podcast.app.util.DiagnosticLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkDiagnostics(
    val hasInternet: Boolean,
    val hasWifi: Boolean,
    val hasCellular: Boolean,
    val isVpnActive: Boolean,
    val transportInfo: String
)

data class DiagnosticsState(
    val credentialDiagnostics: CredentialDiagnostics? = null,
    val networkDiagnostics: NetworkDiagnostics? = null,
    val privacyNetworkEnabled: Boolean = false,
    val privacySearchAllowed: Boolean = false,
    val logText: String = "",
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsProvider: SecureCredentialsProvider,
    private val privacyManager: PrivacyManager
) : ViewModel() {

    companion object {
        private const val TAG = "DiagnosticsVM"
    }

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    val logs = DiagnosticLogger.logFlow

    init {
        DiagnosticLogger.i(TAG, "DiagnosticsViewModel initialized")
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)

            DiagnosticLogger.i(TAG, "Refreshing diagnostics...")

            // Get credential diagnostics
            val credDiag = credentialsProvider.getDiagnosticInfo()
            DiagnosticLogger.d(TAG, "Credentials: ready=${credDiag.credentialsReady}, storage=${credDiag.encryptedStorageAvailable}")

            // Get network diagnostics
            val netDiag = getNetworkDiagnostics()
            DiagnosticLogger.d(TAG, "Network: internet=${netDiag.hasInternet}, wifi=${netDiag.hasWifi}")

            // Get privacy settings
            val privacySettings = privacyManager.settings.first()
            val networkEnabled = privacySettings.networkEnabled
            val searchAllowed = privacySettings.allowPodcastSearch
            DiagnosticLogger.d(TAG, "Privacy: network=$networkEnabled, search=$searchAllowed")

            // Get log stats
            val stats = DiagnosticLogger.getStats()
            val errorCount = stats[DiagnosticLogger.Level.ERROR] ?: 0
            val warningCount = stats[DiagnosticLogger.Level.WARN] ?: 0

            _state.value = _state.value.copy(
                credentialDiagnostics = credDiag,
                networkDiagnostics = netDiag,
                privacyNetworkEnabled = networkEnabled,
                privacySearchAllowed = searchAllowed,
                logText = DiagnosticLogger.getLogsAsText(),
                errorCount = errorCount,
                warningCount = warningCount,
                isRefreshing = false
            )

            DiagnosticLogger.i(TAG, "Diagnostics refresh complete")
        }
    }

    private fun getNetworkDiagnostics(): NetworkDiagnostics {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val transports = mutableListOf<String>()
        if (hasWifi) transports.add("WiFi")
        if (hasCellular) transports.add("Cellular")
        if (isVpnActive) transports.add("VPN")
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) transports.add("Ethernet")

        return NetworkDiagnostics(
            hasInternet = hasInternet,
            hasWifi = hasWifi,
            hasCellular = hasCellular,
            isVpnActive = isVpnActive,
            transportInfo = if (transports.isEmpty()) "None" else transports.joinToString(", ")
        )
    }

    fun getLogsAsText(): String {
        return DiagnosticLogger.getLogsAsText()
    }

    fun clearLogs() {
        DiagnosticLogger.clear()
        DiagnosticLogger.i(TAG, "Logs cleared by user")
        refreshDiagnostics()
    }

    fun runApiTest() {
        viewModelScope.launch {
            DiagnosticLogger.i(TAG, "=== Starting API Test ===")

            // Check credentials
            val creds = credentialsProvider.getDiagnosticInfo()
            if (!creds.credentialsReady) {
                DiagnosticLogger.e(TAG, "API Test: Credentials not ready")
                DiagnosticLogger.e(TAG, "  - Storage available: ${creds.encryptedStorageAvailable}")
                DiagnosticLogger.e(TAG, "  - Has stored key: ${creds.hasStoredApiKey}")
                DiagnosticLogger.e(TAG, "  - Has stored secret: ${creds.hasStoredApiSecret}")
                DiagnosticLogger.e(TAG, "  - BuildConfig has key: ${creds.buildConfigHasApiKey}")
                DiagnosticLogger.e(TAG, "  - Error: ${creds.initializationError ?: "none"}")
                return@launch
            }

            DiagnosticLogger.i(TAG, "API Test: Credentials OK")

            // Check network
            val net = getNetworkDiagnostics()
            if (!net.hasInternet) {
                DiagnosticLogger.e(TAG, "API Test: No internet connection")
                DiagnosticLogger.e(TAG, "  - Transport: ${net.transportInfo}")
                return@launch
            }

            DiagnosticLogger.i(TAG, "API Test: Network OK (${net.transportInfo})")

            // Check privacy settings
            val privacySettings = privacyManager.settings.first()
            if (!privacySettings.networkEnabled) {
                DiagnosticLogger.e(TAG, "API Test: Network disabled in privacy settings")
                return@launch
            }
            if (!privacySettings.allowPodcastSearch) {
                DiagnosticLogger.e(TAG, "API Test: Podcast search disabled in privacy settings")
                return@launch
            }

            DiagnosticLogger.i(TAG, "API Test: Privacy settings OK")
            DiagnosticLogger.i(TAG, "=== API Test Complete - All checks passed ===")

            refreshDiagnostics()
        }
    }
}
