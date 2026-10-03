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
import java.util.concurrent.ConcurrentHashMap

/**
 * Single authority for Netfetch's real upstream Internet connection.
 *
 * A network is NOT considered ready merely because Android reports
 * NET_CAPABILITY_INTERNET. Netfetch performs an actual reachability test
 * before publishing that network as Internet-ready.
 *
 * Selection policy:
 *  1. Prefer Wi-Fi.
 *  2. Prefer Android-validated networks.
 *  3. Verify actual Internet reachability.
 *  4. Use cellular/other only when they pass verification.
 *
 * Failed networks are temporarily cooled down so a dead network cannot
 * immediately get rediscovered and selected again.
 */
class UpstreamNetworkManager(private val context: Context) {

    private companion object {
        const val TAG = "NetFetchUpstream"

        const val RECOVERY_MAX_ATTEMPTS = 6

        const val FAILURE_COOLDOWN_MS = 15_000L

        const val VALIDATION_TIMEOUT_MS = 8_000L

        const val RECOVERY_DELAY_FIRST_MS = 500L
        const val RECOVERY_DELAY_SECOND_MS = 1_000L
        const val RECOVERY_DELAY_THIRD_MS = 2_000L
        const val RECOVERY_DELAY_LATER_MS = 5_000L
    }

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
        context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

    private val _upstreamState =
        MutableStateFlow(UpstreamState())

    val upstreamState: StateFlow<UpstreamState> =
        _upstreamState.asStateFlow()

    val currentNetwork: Network?
        get() = _upstreamState.value.network

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val selectionMutex = Mutex()

    /*
     * Keep only one pending selection request. Android can emit several
     * callbacks during one connectivity transition, so callers should
     * schedule through requestReselect() instead of queueing separate
     * selection jobs.
     */
    private var selectionJob: Job? = null

    /*
     * Network callbacks can arrive on Android callback threads while
     * discovery/recovery is running on Dispatchers.IO.
     *
     * ConcurrentHashMap prevents unsafe concurrent access.
     */
    private val availableNetworks =
        ConcurrentHashMap<Network, UpstreamType>()

    /*
     * A network that has just failed a real Internet test is temporarily
     * excluded. This prevents:
     *
     * failed network
     * -> remove
     * -> Android still reports INTERNET capability
     * -> rediscover
     * -> immediately select same dead network
     */
    private val failedUntil =
        ConcurrentHashMap<Network, Long>()

    private var recoveryJob: Job? = null

    @Volatile
    private var started = false

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                if (!started) return

                val caps =
                    connectivityManager.getNetworkCapabilities(network)
                        ?: return

                val type = determineType(caps)

                Log.i(
                    TAG,
                    "Network available: $network type=$type"
                )

                availableNetworks[network] = type

                requestReselect()
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Network lost: $network")

                availableNetworks.remove(network)
                failedUntil.remove(network)

                if (_upstreamState.value.network == network) {
                    publishState(
                        UpstreamState(
                            displayName = "Recovering Internet..."
                        )
                    )
                }

                scheduleRecovery()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                if (!started) return

                val type = determineType(caps)

                if (isCandidate(caps)) {
                    availableNetworks[network] = type

                    /*
                     * A capability change can mean the network recovered.
                     * Give it a fresh validation attempt.
                     */
                    if (
                        caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        )
                    ) {
                        failedUntil.remove(network)
                    }
                } else {
                    availableNetworks.remove(network)

                    if (_upstreamState.value.network == network) {
                        publishState(
                            UpstreamState(
                                displayName = "Recovering Internet..."
                            )
                        )
                    }
                }

                Log.d(
                    TAG,
                    "Capabilities changed: $network " +
                        "type=$type " +
                        "internet=" +
                        caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                        ) +
                        "validated=" +
                        caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        )
                )

                requestReselect()
            }
        }

    fun start() {
        if (started) {
            Log.d(TAG, "Already started")
            return
        }

        started = true

        try {
            val request =
                NetworkRequest.Builder()
                    .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    .build()

            connectivityManager.registerNetworkCallback(
                request,
                networkCallback
            )

            Log.i(
                TAG,
                "UpstreamNetworkManager started"
            )
        } catch (e: Exception) {
            started = false

            Log.e(
                TAG,
                "Failed to register network callback",
                e
            )

            publishState(
                UpstreamState(
                    displayName = "No Internet"
                )
            )

            return
        }

        detectCurrentNetworks()
        requestReselect()
    }

    fun stop() {
        if (!started) return

        started = false

        try {
            connectivityManager.unregisterNetworkCallback(
                networkCallback
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error unregistering network callback: ${e.message}"
            )
        }

        recoveryJob?.cancel()
        recoveryJob = null

        selectionJob?.cancel()
        selectionJob = null

        availableNetworks.clear()
        failedUntil.clear()

        publishState(UpstreamState())

        Log.i(
            TAG,
            "UpstreamNetworkManager stopped"
        )
    }

    /**
     * Reports a real connectivity failure for the selected network.
     *
     * The failed network enters a short cooldown and is not immediately
     * eligible for reselection. Recovery then searches for another network
     * and validates it before publishing Internet-ready state.
     */
    fun reportConnectivityFailure(network: Network?) {
        if (!started) return

        if (network == null) {
            scheduleRecovery()
            return
        }

        val until =
            System.currentTimeMillis() +
                FAILURE_COOLDOWN_MS

        failedUntil[network] = until

        Log.w(
            TAG,
            "Connectivity failure reported for $network; " +
                "cooling down for ${FAILURE_COOLDOWN_MS}ms"
        )

        if (_upstreamState.value.network == network) {
            publishState(
                UpstreamState(
                    displayName = "Recovering Internet..."
                )
            )
        }

        scheduleRecovery()
    }

    /**
     * Forces a fresh discovery pass.
     */
    fun refresh() {
        if (!started) return

        detectCurrentNetworks()
        requestReselect()
    }

    private fun detectCurrentNetworks() {
        if (!started) return

        val now = System.currentTimeMillis()
        val current =
            mutableMapOf<Network, UpstreamType>()

        for (network in connectivityManager.allNetworks) {
            val caps =
                connectivityManager.getNetworkCapabilities(network)
                    ?: continue

            if (!isCandidate(caps)) {
                continue
            }

            /*
             * Do not resurrect a recently failed network just because
             * Android still reports NET_CAPABILITY_INTERNET.
             */
            val failedUntilTime =
                failedUntil[network]

            if (
                failedUntilTime != null &&
                failedUntilTime > now
            ) {
                continue
            }

            if (
                failedUntilTime != null &&
                failedUntilTime <= now
            ) {
                failedUntil.remove(network)
            }

            current[network] =
                determineType(caps)
        }

        availableNetworks.clear()
        availableNetworks.putAll(current)

        Log.d(
            TAG,
            "Detected ${current.size} upstream candidate(s)"
        )
    }

    private fun requestReselect() {
        if (!started) return

        /*
         * Do not cancel an active validation just because Android emitted
         * another callback. Several callbacks can belong to the same
         * connectivity transition.
         *
         * Keep one selection operation in flight at a time.
         */
        if (selectionJob?.isActive == true) {
            return
        }

        selectionJob = scope.launch {
            try {
                reselect()
            } finally {
                selectionJob = null
            }
        }
    }

    private suspend fun reselect() {
        selectionMutex.withLock {
                if (!started) return@withLock

                /*
                 * Never keep advertising the current network forever.
                 * Revalidate it whenever the selection process runs.
                 */
                val current =
                    _upstreamState.value.network

                if (
                    current != null &&
                    !isCoolingDown(current) &&
                    isNetworkCandidate(current)
                ) {
                    val currentType =
                        availableNetworks[current]
                            ?: determineType(
                                connectivityManager
                                    .getNetworkCapabilities(current)
                                    ?: return@withLock
                            )

                    val validated =
                        validateNetwork(current)

                    if (validated) {
                        publishState(
                            UpstreamState(
                                network = current,
                                type = currentType,
                                hasInternet = true,
                                displayName =
                                    displayNameFor(
                                        currentType
                                    )
                            )
                        )

                        recoveryJob?.cancel()
                        recoveryJob = null

                        return@withLock
                    }

                    Log.w(
                        TAG,
                        "Current upstream failed validation: $current"
                    )

                    markFailed(current)

                    publishState(
                        UpstreamState(
                            displayName =
                                "Recovering Internet..."
                        )
                    )
                }

                /*
                 * Candidates are ordered by transport and Android's
                 * validation state, then each candidate receives a real
                 * Internet test.
                 */
                val candidates =
                    chooseBestCandidates()

                for (network in candidates) {
                    if (!started) {
                        return@withLock
                    }

                    if (isCoolingDown(network)) {
                        continue
                    }

                    val caps =
                        connectivityManager
                            .getNetworkCapabilities(network)
                            ?: continue

                    if (!isCandidate(caps)) {
                        continue
                    }

                    Log.i(
                        TAG,
                        "Validating upstream candidate: $network"
                    )

                    if (!validateNetwork(network)) {
                        Log.w(
                            TAG,
                            "Candidate failed Internet validation: $network"
                        )

                        markFailed(network)
                        continue
                    }

                    val type =
                        determineType(caps)

                    publishState(
                        UpstreamState(
                            network = network,
                            type = type,
                            hasInternet = true,
                            displayName =
                                displayNameFor(type)
                        )
                    )

                    Log.i(
                        TAG,
                        "Internet-ready upstream selected: " +
                            "$network type=$type"
                    )

                    recoveryJob?.cancel()
                    recoveryJob = null

                    return@withLock
                }
                publishState(
                    UpstreamState(
                        displayName = "No Internet"
                    )
                )
            }
        }

    private fun chooseBestCandidates(): List<Network> {
        val now = System.currentTimeMillis()

        return availableNetworks
            .entries
            .asSequence()
            .filter { entry ->
                val failedUntilTime =
                    failedUntil[entry.key]

                failedUntilTime == null ||
                    failedUntilTime <= now
            }
            .sortedWith(
                compareByDescending<
                    Map.Entry<Network, UpstreamType>
                > {
                    transportPriority(it.value)
                }.thenByDescending {
                    isAndroidValidated(it.key)
                }
            )
            .map { it.key }
            .toList()
    }

    private fun transportPriority(
        type: UpstreamType
    ): Int {
        return when (type) {
            UpstreamType.WIFI -> 3
            UpstreamType.CELLULAR -> 2
            UpstreamType.OTHER -> 1
            UpstreamType.NONE -> 0
        }
    }

    private fun isAndroidValidated(
        network: Network
    ): Boolean {
        val caps =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }

    private suspend fun validateNetwork(
        network: Network
    ): Boolean {
        val caps =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        if (!isCandidate(caps)) {
            return false
        }

        /*
         * Android's VALIDATED capability means the framework has already
         * successfully validated real Internet access on this exact network.
         *
         * Do not reject a working Wi-Fi connection just because one of our
         * own probe URLs is unavailable, filtered, redirected, or slow.
         */
        if (
            caps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        ) {
            Log.i(
                TAG,
                "Android reports upstream VALIDATED: $network"
            )
            return true
        }

        /*
         * If Android has not validated the network yet, perform our own
         * reachability test as a fallback.
         */
        return try {
            kotlinx.coroutines.withTimeout(
                VALIDATION_TIMEOUT_MS
            ) {
                ConnectivityTester.testConnectivity(network)
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Upstream validation failed for $network: ${e.message}"
            )
            false
        }
    }

    private fun isNetworkCandidate(
        network: Network
    ): Boolean {
        val caps =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        return isCandidate(caps)
    }

    private fun isCandidate(
        caps: NetworkCapabilities
    ): Boolean {
        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    private fun isCoolingDown(
        network: Network
    ): Boolean {
        val until =
            failedUntil[network]
            ?: return false

        if (until <= System.currentTimeMillis()) {
            failedUntil.remove(network)
            return false
        }

        return true
    }

    private fun markFailed(
        network: Network
    ) {
        failedUntil[network] =
            System.currentTimeMillis() +
                FAILURE_COOLDOWN_MS
    }

    private fun scheduleRecovery() {
        if (!started) return

        recoveryJob?.cancel()

        recoveryJob =
            scope.launch {
                var attempt = 0

                while (
                    isActive &&
                    started &&
                    attempt < RECOVERY_MAX_ATTEMPTS
                ) {
                    attempt++

                    val delayMs =
                        when (attempt) {
                            1 -> RECOVERY_DELAY_FIRST_MS
                            2 -> RECOVERY_DELAY_SECOND_MS
                            3 -> RECOVERY_DELAY_THIRD_MS
                            else -> RECOVERY_DELAY_LATER_MS
                        }

                    delay(delayMs)

                    if (!started) break

                    detectCurrentNetworks()
                    requestReselect()
                    selectionJob?.join()

                    if (
                        _upstreamState.value.network != null &&
                        _upstreamState.value.hasInternet
                    ) {
                        Log.i(
                            TAG,
                            "Upstream recovery succeeded on attempt $attempt"
                        )
                        break
                    }

                    Log.w(
                        TAG,
                        "Upstream recovery attempt " +
                            "$attempt/$RECOVERY_MAX_ATTEMPTS did not succeed"
                    )
                }
            }
    }

    private fun publishState(
        newState: UpstreamState
    ) {
        if (newState == _upstreamState.value) {
            return
        }

        Log.i(
            TAG,
            "Upstream state: ${newState.displayName} " +
                "network=${newState.network} " +
                "internet=${newState.hasInternet}"
        )

        _upstreamState.value = newState
    }

    private fun displayNameFor(
        type: UpstreamType
    ): String {
        return when (type) {
            UpstreamType.WIFI ->
                "Wi-Fi Internet"

            UpstreamType.CELLULAR ->
                "Mobile Data Internet"

            UpstreamType.OTHER ->
                "Internet Available"

            UpstreamType.NONE ->
                "No Internet"
        }
    }

    private fun determineType(
        caps: NetworkCapabilities
    ): UpstreamType {
        return when {
            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) ->
                UpstreamType.WIFI

            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) ->
                UpstreamType.CELLULAR

            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET
            ) ->
                UpstreamType.OTHER

            else ->
                UpstreamType.OTHER
        }
    }



}

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
