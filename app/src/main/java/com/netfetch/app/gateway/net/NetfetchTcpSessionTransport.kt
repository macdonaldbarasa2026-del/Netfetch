package com.netfetch.app.gateway.net

import java.net.InetAddress

interface NetfetchTcpSessionTransport {
    fun open(
        key: TcpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onConnected: (TcpFlowKey) -> Unit,
        onConnectFailed: (TcpFlowKey) -> Unit,
        onData: (TcpFlowKey, ByteArray) -> Unit,
        onClosed: (TcpFlowKey) -> Unit,
        onWriteQueueAvailable: (TcpFlowKey) -> Unit = {}
    ): Boolean

    fun write(key: TcpFlowKey, data: ByteArray): NetfetchTcpSessionManager.WriteResult
    fun close(key: TcpFlowKey)
    fun closeAll()
    fun closeSessions()
    fun count(): Int
    fun isConnecting(key: TcpFlowKey): Boolean
    fun availableReceiveWindow(key: TcpFlowKey): Int
}
