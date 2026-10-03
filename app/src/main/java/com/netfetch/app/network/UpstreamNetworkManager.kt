package com.netfetch.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Selects and tracks the best upstream Internet network.
 *
 * NetFetch clients use a local Wi-Fi Direct network, while outbound Internet
 * traffic must leave through the phone's actual Internet network.
 *
 * This class is the single authority for selecting that upstream network.
 */
class UpstreamNetworkManager(private val context: Context) {

    private val TAG = "NetFetchUpstream"

    enum class UpstreamType {
        NONE,
        WIFI,
        CELLULAR,
        OTHER
    }

    data class UpstreamState(
        val network: Network? = null,
        val type: UpstreamType = UpstreamType.NONE,
        val hasInternet: Boolean = false,
        val displayName: String = "No Internet"
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _upstreamState = MutableStateFlow(UpstreamState())
    val upstreamState: StateFlow<UpstreamState> = _upstreamState.asStateFlow()

    val currentNetwork: Network?
        get() = _upstreamState.value.network

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val selectionMutex = Mutex()

    private val availableNetworks = mutableMapOf<Network, UpstreamType>()

    private var recoveryJob: Job? = null
    private var started = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return
            val type = determineType(caps)

            Log.i(TAG, "Network available: $network type=$type")

            availableNetworks[network] = type
            reselect()
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost: $network")

            availableNetworks.remove(network)

            if (_upstreamState.value.network == network) {
                setNoInternetIfNecessary()
            }

            reselect()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            caps: NetworkCapabilities
        ) {
            val type = determineType(caps)
            val usable = isCandidate(caps)

            Log.d(
                TAG,
                "Capabilities changed: $network type=$type usable=$usable"
            )

            if (usable) {
                availableNetworks[network] = type
            } else {
                availableNetworks.remove(network)

                if (_upstreamState.value.network == network) {
                    setNoInternetIfNecessary()
                }
            }

            reselect()
        }
    }

    fun start() {
        if (started) {
            Log.d(TAG, "Already started")
            return
        }

        started = true

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                networkCallback
            )

            Log.i(TAG, "UpstreamNetworkManager started")
        } catch (e: Exception) {
            started = false
            Log.e(
                TAG,
                "Failed to register network callback: ${e.message}",
                e
            )
            return
        }

        detectCurrentNetworks()
        reselect()
    }

    fun stop() {
        if (!started) {
            return
        }

        started = false

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error unregistering network callback: ${e.message}"
            )
        }

        recoveryJob?.cancel()
        recoveryJob = null

        availableNetworks.clear()
        _upstreamState.value = UpstreamState()

        Log.i(TAG, "UpstreamNetworkManager stopped")
    }

    /**
     * Called by HotspotService after a real Internet connectivity test fails.
     *
     * This forces the manager to discard the current failed candidate and
     * search for another usable upstream instead of continuing to send new
     * connections through a dead network.
     */
    fun reportConnectivityFailure(network: Network?) {
        if (network == null) {
            reselect()
            return
        }

        Log.w(
            TAG,
            "Connectivity failure reported for $network"
        )

        availableNetworks.remove(network)

        if (_upstreamState.value.network == network) {
            _upstreamState.value = UpstreamState(
                network = null,
                type = UpstreamType.NONE,
                hasInternet = false,
                displayName = "Recovering Internet..."
            )
        }

        scheduleRecovery()
    }

    /**
     * Forces a fresh scan of currently available networks.
     */
    fun refresh() {
        if (!started) return

        detectCurrentNetworks()
        reselect()
    }

    private fun detectCurrentNetworks() {
        val current = mutableMapOf<Network, UpstreamType>()

        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network)
                ?: continue

            if (isCandidate(caps)) {
                current[network] = determineType(caps)
            }
        }

        availableNetworks.clear()
        availableNetworks.putAll(current)

        Log.d(
            TAG,
            "Detected ${current.size} usable upstream candidate(s)"
        )
    }

    private fun reselect() {
        scope.launch {
            selectionMutex.withLock {
                if (!started) return@withLock

                val current = _upstreamState.value.network

                if (current != null && isNetworkCurrentlyUsable(current)) {
                    val currentType = availableNetworks[current]
                        ?: determineType(
                            connectivityManager.getNetworkCapabilities(current)
                                ?: return@withLock
                        )

                    publishState(
                        UpstreamState(
                            network = current,
                            type = currentType,
                            hasInternet = true,
                            displayName = displayNameFor(currentType)
                        )
                    )

                    return@withLock
                }

                val chosen = chooseBestCandidate()

                if (chosen != null) {
                    val type = availableNetworks[chosen]
                        ?: UpstreamType.OTHER

                    publishState(
                        UpstreamState(
                            network = chosen,
                            type = type,
                            hasInternet = true,
                            displayName = displayNameFor(type)
                        )
                    )
                } else {
                    publishState(
                        UpstreamState(
                            network = null,
                            type = UpstreamType.NONE,
                            hasInternet = false,
                            displayName = "No Internet"
                        )
                    )
                }
            }
        }
    }

    private fun chooseBestCandidate(): Network? {
        val wifi = availableNetworks.entries.firstOrNull {
            it.value == UpstreamType.WIFI
        }?.key

        val cellular = availableNetworks.entries.firstOrNull {
            it.value == UpstreamType.CELLULAR
        }?.key

        val other = availableNetworks.entries.firstOrNull {
            it.value == UpstreamType.OTHER
        }?.key

        return wifi ?: cellular ?: other
    }

    private fun isNetworkCurrentlyUsable(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return false

        return isCandidate(caps)
    }

    private fun isCandidate(caps: NetworkCapabilities): Boolean {
        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    private fun setNoInternetIfNecessary() {
        if (_upstreamState.value.network != null) {
            _upstreamState.value = UpstreamState(
                network = null,
                type = UpstreamType.NONE,
                hasInternet = false,
                displayName = "Recovering Internet..."
            )
        }
    }

    private fun scheduleRecovery() {
        recoveryJob?.cancel()

        recoveryJob = scope.launch {
            var attempt = 0

            while (isActive && started && attempt < 5) {
                attempt++

                delay(
                    when (attempt) {
                        1 -> 500L
                        2 -> 1_000L
                        3 -> 2_000L
                        else -> 5_000L
                    }
                )

                detectCurrentNetworks()
                reselect()

                if (_upstreamState.value.network != null) {
                    Log.i(
                        TAG,
                        "Upstream recovery selected ${_upstreamState.value.network}"
                    )
                    break
                }
            }
        }
    }

    private fun publishState(newState: UpstreamState) {
        if (newState != _upstreamState.value) {
            Log.i(
                TAG,
                "Upstream changed: ${newState.displayName} (${newState.network})"
            )

            _upstreamState.value = newState
        }
    }

    private fun displayNameFor(type: UpstreamType): String {
        return when (type) {
            UpstreamType.WIFI -> "Wi-Fi Internet"
            UpstreamType.CELLULAR -> "Mobile Data Internet"
            UpstreamType.OTHER -> "Internet Available"
            UpstreamType.NONE -> "No Internet"
        }
    }

    private fun determineType(
        caps: NetworkCapabilities
    ): UpstreamType {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                UpstreamType.WIFI

            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                UpstreamType.CELLULAR

            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                UpstreamType.OTHER

            else ->
                UpstreamType.OTHER
        }
    }
}
