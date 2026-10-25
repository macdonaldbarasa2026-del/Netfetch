package com.netfetch.app.gateway.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SocketChannel

class NetfetchDirectConnector(
    private val socketProtector: NetfetchSocketProtector
) : NetfetchSelectableConnector, NetfetchSelectableUdpConnector {

    override fun openTcpChannel(): SocketChannel =
        socketProtector.openTcpChannel()

    override fun openUdpChannel(): java.nio.channels.DatagramChannel =
        socketProtector.openUdpChannel()

    override fun connectTcp(
        destination: InetAddress,
        port: Int,
        timeoutMs: Int
    ): Socket {
        val socket = socketProtector.openTcpSocket()

        try {
            socket.connect(
                InetSocketAddress(destination, port),
                timeoutMs
            )

            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }

            if (e is java.io.IOException) {
                throw e
            }

            throw java.io.IOException(
                "Unable to connect to $destination:$port",
                e
            )
        }
    }

    override fun openUdp(): DatagramSocket {
        return socketProtector.openUdpSocket()
    }
}
