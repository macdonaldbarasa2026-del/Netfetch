package com.netfetch.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide owner for the single Netfetch upstream network manager.
 *
 * HotspotService owns the lifecycle. VpnGatewayService and proxy components
 * read the same selected Network so all outbound traffic uses one authority.
 */
object NetfetchUpstreamRuntime {

    @Volatile
    private var manager: UpstreamNetworkManager? = null

    @Volatile
    private var appContext: Context? = null

    fun start(context: Context): UpstreamNetworkManager {
        return synchronized(this) {
            appContext = context.applicationContext
            manager ?: UpstreamNetworkManager(context.applicationContext).also {
                manager = it
                it.start()
            }
        }
    }

    fun get(): UpstreamNetworkManager? = manager

    fun currentNetwork(): Network? {
        val selected = manager?.currentNetwork
        if (selected != null) return selected

        // Fallback to active network if manager is still validating
        return fallbackNetwork()
    }

    fun fallbackNetwork(): Network? {
        val ctx = appContext ?: return null
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork
            if (active != null) {
                val caps = cm.getNetworkCapabilities(active)
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    val lp = cm.getLinkProperties(active)
                    val iface = lp?.interfaceName?.lowercase() ?: ""
                    if (!iface.startsWith("p2p") && !iface.contains("p2p")) {
                        return active
                    }
                }
            }
            // Check all networks if activeNetwork is null or p2p
            cm?.allNetworks?.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net)
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    val lp = cm.getLinkProperties(net)
                    val iface = lp?.interfaceName?.lowercase() ?: ""
                    !iface.startsWith("p2p") && !iface.contains("p2p")
                } else false
            }
        } catch (_: Exception) {
            null
        }
    }

    fun state(): StateFlow<UpstreamNetworkManager.UpstreamState>? =
        manager?.upstreamState

    @Volatile
    private var socketProtector: ((java.net.Socket) -> Boolean)? = null

    @Volatile
    private var datagramSocketProtector: ((java.net.DatagramSocket) -> Boolean)? = null

    fun setSocketProtector(protector: ((java.net.Socket) -> Boolean)?) {
        socketProtector = protector
    }

    fun setDatagramSocketProtector(protector: ((java.net.DatagramSocket) -> Boolean)?) {
        datagramSocketProtector = protector
    }

    fun protect(socket: java.net.Socket): Boolean {
        return socketProtector?.invoke(socket) ?: true
    }

    fun protect(socket: java.net.DatagramSocket): Boolean {
        return datagramSocketProtector?.invoke(socket) ?: true
    }

    fun stop(owner: UpstreamNetworkManager? = null) {
        synchronized(this) {
            val current = manager ?: return

            if (owner != null && current !== owner) {
                return
            }

            current.stop()
            manager = null
            socketProtector = null
            datagramSocketProtector = null
        }
    }
}
