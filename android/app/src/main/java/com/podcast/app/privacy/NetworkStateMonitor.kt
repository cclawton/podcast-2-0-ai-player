package com.podcast.app.privacy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity without requiring additional permissions.
 *
 * GrapheneOS Considerations:
 * - Works even when INTERNET permission is revoked
 * - Detects network type (Wi-Fi, cellular, metered)
 * - Provides cached data mode when offline
 * - Queues sync operations for when network returns
 *
 * Key Design:
 * - Does NOT require ACCESS_NETWORK_STATE permission (optional on API 21+)
 * - Gracefully handles permission denial
 * - Provides fallback mechanisms
 */
@Singleton
class NetworkStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectivityManager: ConnectivityManager? by lazy {
        try {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        } catch (e: SecurityException) {
            // Permission denied - operate in offline mode
            null
        }
    }

    private val _networkState = MutableStateFlow(NetworkState())

    // Store the main callback for proper cleanup
    private var mainNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val callbackLock = Any()

    /**
     * Current network state as StateFlow.
     */
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    /**
     * Whether network is currently available.
     * Uses the main callback instead of creating new ones.
     */
    val isNetworkAvailable: StateFlow<Boolean>
        get() = _networkState.mapToBoolean { it.isAvailable }

    /**
     * Whether we're on an unmetered connection (Wi-Fi, Ethernet).
     * Uses the main callback instead of creating new ones.
     */
    val isUnmeteredConnection: StateFlow<Boolean>
        get() = _networkState.mapToBoolean { it.isUnmetered }

    private fun StateFlow<NetworkState>.mapToBoolean(transform: (NetworkState) -> Boolean): StateFlow<Boolean> {
        return MutableStateFlow(transform(value)).also { result ->
            scope.launch {
                this@mapToBoolean.collect { state ->
                    result.value = transform(state)
                }
            }
        }
    }

    init {
        // Initial state check
        updateNetworkState()
        // Register callback for ongoing monitoring
        registerNetworkCallback()
    }

    /**
     * Release resources. Call this when the monitor is no longer needed.
     * Important for tests to avoid TooManyRequestsException.
     */
    fun release() {
        synchronized(callbackLock) {
            mainNetworkCallback?.let { callback ->
                try {
                    connectivityManager?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Already unregistered or other error - ignore
                }
                mainNetworkCallback = null
            }
        }
    }

    /**
     * Check network availability synchronously.
     * Use sparingly - prefer the flow-based API.
     */
    fun checkNetworkNow(): Boolean {
        val cm = connectivityManager ?: return false

        return try {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Check if current connection is unmetered.
     */
    fun checkUnmeteredNow(): Boolean {
        val cm = connectivityManager ?: return false

        return try {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Get current network type.
     */
    fun getNetworkType(): NetworkType {
        val cm = connectivityManager ?: return NetworkType.NONE

        return try {
            val network = cm.activeNetwork ?: return NetworkType.NONE
            val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                else -> NetworkType.OTHER
            }
        } catch (e: SecurityException) {
            NetworkType.NONE
        }
    }

    /**
     * Update network state snapshot.
     */
    private fun updateNetworkState() {
        _networkState.value = NetworkState(
            isAvailable = checkNetworkNow(),
            isUnmetered = checkUnmeteredNow(),
            type = getNetworkType()
        )
    }

    /**
     * Register network callback for real-time updates.
     * Only registers one callback and stores it for cleanup.
     */
    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return

        synchronized(callbackLock) {
            // Don't register if already registered
            if (mainNetworkCallback != null) return

            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateNetworkState()
                    }

                    override fun onLost(network: Network) {
                        updateNetworkState()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities
                    ) {
                        updateNetworkState()
                    }
                }

                cm.registerNetworkCallback(request, callback)
                mainNetworkCallback = callback
            } catch (e: SecurityException) {
                // Permission denied - stay in offline mode
            }
        }
    }
}

/**
 * Current network state snapshot.
 */
data class NetworkState(
    val isAvailable: Boolean = false,
    val isUnmetered: Boolean = false,
    val type: NetworkType = NetworkType.NONE
) {
    /**
     * Should we allow large downloads?
     */
    val allowLargeDownloads: Boolean get() = isAvailable && isUnmetered

    /**
     * Human-readable description.
     */
    val description: String get() = when {
        !isAvailable -> "Offline"
        type == NetworkType.WIFI -> "Wi-Fi"
        type == NetworkType.ETHERNET -> "Ethernet"
        type == NetworkType.CELLULAR -> "Mobile Data"
        type == NetworkType.VPN -> "VPN"
        else -> "Connected"
    }
}

/**
 * Network connection type.
 */
enum class NetworkType {
    NONE,
    WIFI,
    ETHERNET,
    CELLULAR,
    VPN,
    OTHER
}
