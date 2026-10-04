package com.netfetch.app.netfetchlink

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receiver-side link manager.
 *
 * Handles the full provider discovery → Wi-Fi Direct → session lifecycle
 * for BOTH Normal (HTTP/PAC proxy) and Pro (SOCKS5/VPN) providers.
 *
 * State transitions:
 *   Idle → Searching → ProviderFound → Connecting → Authenticating
 *   → Connected (Normal: set system proxy / Pro: start VPN)
 *   → Reconnecting on failure → Searching (retry)
 *
 * The caller provides [onStateChanged] which fires on the main thread.
 * The caller is responsible for:
 *   - In Normal mode: configuring the system HTTP proxy based on Connected state.
 *   - In Pro mode: starting NetfetchReceiverVpnService with the SOCKS5 details.
 */
class NetfetchReceiverLink(
    private val context: Context,
    private val onStateChanged: (NetfetchReceiverState) -> Unit
) {
    companion object {
        private const val TAG = "NetFetchReceiverLink"

        private const val CONNECTION_INFO_RETRY_MS = 500L
        private const val MAX_CONNECTION_INFO_ATTEMPTS = 15

        private const val RECONNECT_DELAY_MS = 2_000L
        private const val CONNECTION_MONITOR_INTERVAL_MS = 2_000L
        private const val SESSION_REFRESH_INTERVAL_MS = 30_000L
    }

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE)
            as? WifiP2pManager

    private val channel =
        manager?.initialize(
            context,
            context.mainLooper,
            null
        )

    private val mainHandler =
        Handler(context.mainLooper)

    private var discovery: NetfetchReceiverDiscovery? = null

    @Volatile
    private var stopped = true

    private val reconnectScheduled =
        AtomicBoolean(false)

    @Volatile
    private var connecting = false

    @Volatile
    private var connected = false

    @Volatile
    private var activeSessionToken: String? = null

    @Volatile
    private var activeProviderAddress: String? = null

    private val sessionRefresh =
        object : Runnable {
            override fun run() {
                if (stopped || !connected) {
                    return
                }

                refreshSession()

                if (!stopped && connected) {
                    mainHandler.postDelayed(
                        this,
                        SESSION_REFRESH_INTERVAL_MS
                    )
                }
            }
        }

    private val connectionMonitor =
        object : Runnable {
            override fun run() {
                if (stopped || !connected) {
                    return
                }

                checkConnection()

                if (!stopped && connected) {
                    mainHandler.postDelayed(
                        this,
                        CONNECTION_MONITOR_INTERVAL_MS
                    )
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun start() {
        if (manager == null || channel == null) {
            onStateChanged(
                NetfetchReceiverState.Error(
                    "Wi-Fi Direct is unavailable."
                )
            )
            return
        }

        stopInternal(removeGroup = false)

        stopped = false
        connecting = false
        connected = false
        reconnectScheduled.set(false)

        searchForProvider()
    }

    @SuppressLint("MissingPermission")
    private fun searchForProvider() {
        if (stopped) return

        connecting = false

        onStateChanged(NetfetchReceiverState.Searching)

        discovery?.stop()
        discovery = null

        discovery =
            NetfetchReceiverDiscovery(
                context = context,
                onProviderFound = { provider ->
                    if (stopped) return@NetfetchReceiverDiscovery

                    if (connecting) {
                        return@NetfetchReceiverDiscovery
                    }

                    connecting = true

                    onStateChanged(
                        NetfetchReceiverState.ProviderFound(
                            deviceName = provider.device.deviceName
                                ?: "NetFetch Provider",
                            deviceAddress = provider.device.deviceAddress,
                            providerMode = provider.mode
                        )
                    )

                    connect(provider)
                },
                onError = { message ->
                    if (!stopped) {
                        Log.w(TAG, "Provider discovery error: $message")
                        scheduleReconnect("Provider discovery failed: $message")
                    }
                }
            ).also {
                it.start()
            }
    }

    @SuppressLint("MissingPermission")
    private fun connect(
        provider: NetfetchReceiverDiscovery.Provider
    ) {
        if (stopped) return

        onStateChanged(NetfetchReceiverState.Connecting)

        val wifiManager = manager
        val p2pChannel = channel

        if (wifiManager == null || p2pChannel == null) {
            scheduleReconnect("Wi-Fi Direct is unavailable.")
            return
        }

        val config =
            WifiP2pConfig().apply {
                deviceAddress = provider.device.deviceAddress
            }

        wifiManager.connect(
            p2pChannel,
            config,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct connection requested")

                    waitForConnectionInfo(
                        provider = provider,
                        attempt = 0
                    )
                }

                override fun onFailure(reason: Int) {
                    if (!stopped) {
                        Log.w(TAG, "Wi-Fi Direct connection failed: $reason")
                        scheduleReconnect("Unable to connect to provider: $reason")
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun waitForConnectionInfo(
        provider: NetfetchReceiverDiscovery.Provider,
        attempt: Int
    ) {
        if (stopped) return

        if (attempt >= MAX_CONNECTION_INFO_ATTEMPTS) {
            scheduleReconnect("Provider address was not available.")
            return
        }

        manager?.requestConnectionInfo(channel) { info ->
            if (stopped) return@requestConnectionInfo

            if (info.groupFormed) {
                val owner = info.groupOwnerAddress

                if (owner != null) {
                    val address = owner.hostAddress

                    if (!address.isNullOrBlank()) {
                        Log.i(TAG, "NetFetch provider gateway: $address")

                        openProviderSession(
                            provider = provider,
                            providerAddress = address
                        )

                        return@requestConnectionInfo
                    }
                }
            }

            mainHandler.postDelayed(
                {
                    waitForConnectionInfo(
                        provider = provider,
                        attempt = attempt + 1
                    )
                },
                CONNECTION_INFO_RETRY_MS
            )
        }
    }

    private fun openProviderSession(
        provider: NetfetchReceiverDiscovery.Provider,
        providerAddress: String
    ) {
        onStateChanged(NetfetchReceiverState.Authenticating)

        Thread(
            {
                if (stopped) return@Thread

                try {
                    Log.i(TAG, "Opening NetFetch provider session (mode=${provider.mode})")

                    val session =
                        NetfetchLinkClient()
                            .openSession(providerHost = providerAddress)

                    if (stopped) return@Thread

                    Log.i(
                        TAG,
                        "NetFetch provider session established " +
                            "(mode=${session.mode}, http=${session.httpPort})"
                    )

                    mainHandler.post {
                        if (stopped) return@post

                        connecting = false
                        connected = true
                        activeSessionToken = session.token
                        activeProviderAddress = providerAddress

                        onStateChanged(
                            NetfetchReceiverState.Connected(
                                providerAddress = providerAddress,
                                providerMode = session.mode,
                                httpPort = session.httpPort,
                                pacPort = session.pacPort,
                                socksPort = session.socksPort,
                                sessionToken = session.token
                            )
                        )

                        mainHandler.removeCallbacks(connectionMonitor)
                        mainHandler.postDelayed(
                            connectionMonitor,
                            CONNECTION_MONITOR_INTERVAL_MS
                        )

                        mainHandler.postDelayed(
                            sessionRefresh,
                            SESSION_REFRESH_INTERVAL_MS
                        )
                    }
                } catch (e: Exception) {
                    if (stopped) return@Thread

                    Log.e(
                        TAG,
                        "Unable to establish NetFetch provider session",
                        e
                    )

                    mainHandler.post {
                        if (stopped) return@post

                        scheduleReconnect(
                            "Unable to establish NetFetch session: " +
                                (e.message ?: "connection failed")
                        )
                    }
                }
            },
            "NetFetch-LinkHandshake"
        ).apply { isDaemon = true }.start()
    }

    private fun scheduleReconnect(reason: String) {
        if (stopped) return

        Log.w(TAG, "Scheduling receiver reconnect: $reason")

        discovery?.stop()
        discovery = null

        connecting = false
        connected = false
        mainHandler.removeCallbacks(connectionMonitor)
        mainHandler.removeCallbacks(sessionRefresh)

        if (!reconnectScheduled.compareAndSet(false, true)) {
            return
        }

        onStateChanged(
            NetfetchReceiverState.Reconnecting(reason)
        )

        mainHandler.postDelayed(
            {
                reconnectScheduled.set(false)

                if (!stopped) {
                    searchForProvider()
                }
            },
            RECONNECT_DELAY_MS
        )
    }

    private fun refreshSession() {
        val providerAddress = activeProviderAddress ?: return
        val token = activeSessionToken ?: return

        Thread({
            if (stopped || !connected) return@Thread

            try {
                NetfetchLinkClient().refreshSession(
                    providerHost = providerAddress,
                    token = token
                )

                Log.d(TAG, "NetFetch receiver session refreshed")
            } catch (e: Exception) {
                if (!stopped && connected) {
                    Log.w(TAG, "NetFetch receiver session refresh failed", e)

                    mainHandler.post {
                        if (!stopped && connected) {
                            connected = false
                            activeSessionToken = null
                            activeProviderAddress = null

                            scheduleReconnect(
                                "NetFetch receiver session expired or provider is unavailable."
                            )
                        }
                    }
                }
            }
        }, "NetFetch-SessionRefresh").apply { isDaemon = true }.start()
    }

    @SuppressLint("MissingPermission")
    private fun checkConnection() {
        if (stopped || !connected) return

        val wifiManager = manager
        val p2pChannel = channel

        if (wifiManager == null || p2pChannel == null) {
            scheduleReconnect("Wi-Fi Direct became unavailable.")
            return
        }

        wifiManager.requestConnectionInfo(p2pChannel) { info ->
            if (stopped || !connected) return@requestConnectionInfo

            val ownerAddress = info.groupOwnerAddress?.hostAddress

            if (!info.groupFormed || ownerAddress.isNullOrBlank()) {
                scheduleReconnect("NetFetch provider connection was lost.")
            }
        }
    }

    fun stop() {
        stopInternal(removeGroup = true)
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal(removeGroup: Boolean) {
        stopped = true
        connecting = false
        connected = false
        activeSessionToken = null
        activeProviderAddress = null
        reconnectScheduled.set(false)

        mainHandler.removeCallbacksAndMessages(null)

        discovery?.stop()
        discovery = null

        if (!removeGroup) {
            return
        }

        val wifiManager = manager
        val p2pChannel = channel

        if (wifiManager == null || p2pChannel == null) {
            return
        }

        wifiManager.removeGroup(
            p2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Receiver Wi-Fi Direct group disconnected")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Receiver group cleanup failed: $reason")
                }
            }
        )
    }
}
