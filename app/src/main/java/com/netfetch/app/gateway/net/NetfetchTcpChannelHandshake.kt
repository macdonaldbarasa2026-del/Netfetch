package com.netfetch.app.gateway.net

import java.nio.channels.SocketChannel

interface NetfetchTcpChannelHandshake {
    val interestOps: Int
    val timeoutMillis: Long
        get() = 10_000L

    /** Returns true when the channel is ready to carry application data. */
    fun advance(channel: SocketChannel): Boolean
}
