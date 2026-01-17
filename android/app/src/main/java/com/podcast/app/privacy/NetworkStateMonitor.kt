package com.podcast.app.privacy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 *
 * Note: Provided via PrivacyModule (not @Inject) so it can be replaced in tests
 * with a fake that doesn't register network callbacks.
 */
class NetworkStateMonitor(
    private val context: Context,
    /**
     * If true, skip registering network callbacks. Used by tests to avoid
     * TooManyRequestsException when many test classes create new instances.
     */
    private val skipCallbackRegistration: Boolean = false,
    /**
     * Initial network state for tests. If null, uses real network state.
     */
    initialState: NetworkState? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectivityManager: ConnectivityManager? by lazy {
        if (skipCallbackRegistration) {
            // Skip connectivity manager for tests
            null
        } else {
            try {
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            } catch (e: SecurityException) {
                // Permission denied - operate in offline mode
                null
            }
        }
    }

    private val _networkState = MutableStateFlow(
        initialState ?: NetworkState()
    )

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
        if (!skipCallbackRegistration) {
            // Initial state check
            updateNetworkState()
            // Register callback for ongoing monitoring
            registerNetworkCallback()
        }
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
        // In test mode, return the current state value
        if (skipCallbackRegistration) {
            return _networkState.value.isAvailable
        }

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
        // In test mode, return the current state value
        if (skipCallbackRegistration) {
            return _networkState.value.isUnmetered
        }

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
        // In test mode, return the current state value
        if (skipCallbackRegistration) {
            return _networkState.value.type
        }

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
