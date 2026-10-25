package com.netfetch.app.gateway.net

import java.net.InetAddress

interface NetfetchUdpSessionTransport {
    fun open(
        key: UdpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onData: (UdpFlowKey, ByteArray) -> Unit,
        onClosed: (UdpFlowKey) -> Unit
    ): Boolean

    fun send(key: UdpFlowKey, data: ByteArray): Boolean
    fun close(key: UdpFlowKey)
    fun closeAll()
    fun closeSessions()
    fun count(): Int
}
