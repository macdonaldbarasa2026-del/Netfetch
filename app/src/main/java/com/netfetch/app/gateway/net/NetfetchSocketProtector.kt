package com.netfetch.app.gateway.net

import android.net.Network
import android.net.VpnService
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens upstream sockets on the selected Android Network and prevents
 * Netfetch's own upstream traffic from being captured by its VPN.
 */
class NetfetchSocketProtector(
    private val vpnService: VpnService,
    private val networkProvider: () -> Network?
) {

    fun openTcpSocket(): Socket {
        val network = networkProvider()
            ?: throw java.io.IOException("No upstream network is available")

        val socket = network.socketFactory.createSocket()

        try {
            if (!vpnService.protect(socket)) {
                throw java.io.IOException(
                    "Unable to protect upstream TCP socket"
                )
            }

            socket.tcpNoDelay = true
            socket.keepAlive = true
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }

            if (e is java.io.IOException) {
                throw e
            }

            throw java.io.IOException(
                "Failed to prepare upstream TCP socket",
                e
            )
        }
    }

    fun openUdpSocket(): DatagramSocket {
        val network = networkProvider()
            ?: throw java.io.IOException("No upstream network is available")

        val socket = DatagramSocket(null)

        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))

            // Force UDP traffic onto the selected Wi-Fi/mobile network.
            network.bindSocket(socket)

            // Prevent Netfetch's VPN from capturing its own UDP traffic.
            if (!vpnService.protect(socket)) {
                throw java.io.IOException(
                    "Unable to protect upstream UDP socket"
                )
            }

            socket.soTimeout = 120_000
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }

            if (e is java.io.IOException) {
                throw e
            }

            throw java.io.IOException(
                "Failed to prepare upstream UDP socket",
                e
            )
        }
    }
}
