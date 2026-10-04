package com.netfetch.app.network

import android.content.Context
import android.net.Network
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

    fun start(context: Context): UpstreamNetworkManager {
        return synchronized(this) {
            manager ?: UpstreamNetworkManager(context.applicationContext).also {
                manager = it
                it.start()
            }
        }
    }

    fun get(): UpstreamNetworkManager? = manager

    fun currentNetwork(): Network? =
        manager?.currentNetwork

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
