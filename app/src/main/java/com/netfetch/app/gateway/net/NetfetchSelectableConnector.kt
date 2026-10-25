package com.netfetch.app.gateway.net

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

interface NetfetchSelectableConnector : NetfetchConnector {
    fun openTcpChannel(): SocketChannel

    fun tcpConnectAddress(
        destination: InetAddress,
        port: Int
    ): InetSocketAddress = InetSocketAddress(destination, port)

    fun createTcpHandshake(
        destination: InetAddress,
        port: Int
    ): NetfetchTcpChannelHandshake? = null
}
