package com.netfetch.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Upstream Network Manager
 *
 * Detects and tracks the best available internet-capable upstream network
 * (Wi-Fi or Cellular). The selected upstream Network object is used to bind
 * outbound proxy sockets so they route via the correct interface instead of
 * the Wi-Fi Direct group interface.
 *
 * Architecture:
 *   PHONE INTERNET (Wi-Fi / Mobile Data)
 *        |
 *        v
 *   UpstreamNetworkManager  ← selects & tracks this
 *        |
 *        v
 *   HTTP / SOCKS5 proxy (binds outbound sockets via Network.bindSocket)
 *        |
 *        v
 *   Wi-Fi Direct client device
 */
class UpstreamNetworkManager(private val context: Context) {

    private val TAG = "NetFetchUpstream"

    enum class UpstreamType { NONE, WIFI, CELLULAR, OTHER }

    data class UpstreamState(
        val network: Network? = null,
        val type: UpstreamType = UpstreamType.NONE,
        val hasInternet: Boolean = false,
        val displayName: String = "No Internet"
    )

    private val _upstreamState = MutableStateFlow(UpstreamState())
    val upstreamState: StateFlow<UpstreamState> = _upstreamState.asStateFlow()

    /** Currently selected upstream network (may be null if none available) */
    val currentNetwork: Network?
        get() = _upstreamState.value.network

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Track all available validated internet networks
    private val availableNetworks = mutableMapOf<Network, UpstreamType>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return
            val type = determineType(caps)
            Log.i(TAG, "Network available: $network type=$type")
            availableNetworks[network] = type
            updateBestNetwork()
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost: $network")
            availableNetworks.remove(network)
            updateBestNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val type = determineType(caps)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Network capabilities changed: $network type=$type hasInternet=$hasInternet")
            if (hasInternet) {
                availableNetworks[network] = type
            } else {
                availableNetworks.remove(network)
            }
            updateBestNetwork()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.i(TAG, "UpstreamNetworkManager started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }

        // Do an immediate check using the active network
        detectCurrentNetwork()
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
        availableNetworks.clear()
        _upstreamState.value = UpstreamState()
        Log.i(TAG, "UpstreamNetworkManager stopped")
    }

    private fun detectCurrentNetwork() {
        // Seed from current active networks
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet) {
                availableNetworks[network] = determineType(caps)
            }
        }
        updateBestNetwork()
    }

    private fun updateBestNetwork() {
        // Prefer Wi-Fi over Cellular
        val wifiEntry = availableNetworks.entries.firstOrNull { it.value == UpstreamType.WIFI }
        val cellEntry = availableNetworks.entries.firstOrNull { it.value == UpstreamType.CELLULAR }
        val otherEntry = availableNetworks.entries.firstOrNull { it.value == UpstreamType.OTHER }

        val chosen = wifiEntry ?: cellEntry ?: otherEntry

        val newState = if (chosen != null) {
            val displayName = when (chosen.value) {
                UpstreamType.WIFI -> "Wi-Fi Internet"
                UpstreamType.CELLULAR -> "Mobile Data Internet"
                UpstreamType.NONE -> "No Internet"
                else -> "Internet Available"
            }
            UpstreamState(
                network = chosen.key,
                type = chosen.value,
                hasInternet = true,
                displayName = displayName
            )
        } else {
            UpstreamState(
                network = null,
                type = UpstreamType.NONE,
                hasInternet = false,
                displayName = "No Internet"
            )
        }

        if (newState != _upstreamState.value) {
            Log.i(TAG, "Upstream changed: ${newState.displayName} (${newState.network})")
            _upstreamState.value = newState
        }
    }

    private fun determineType(caps: NetworkCapabilities): UpstreamType {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> UpstreamType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> UpstreamType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> UpstreamType.OTHER
            else -> UpstreamType.OTHER
        }
    }
}
