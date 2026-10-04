package com.netfetch.app.gateway.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

interface NetfetchConnector {
    fun connectTcp(
        destination: InetAddress,
        port: Int,
        timeoutMs: Int = 10_000
    ): Socket

    fun openUdp(): DatagramSocket
}
