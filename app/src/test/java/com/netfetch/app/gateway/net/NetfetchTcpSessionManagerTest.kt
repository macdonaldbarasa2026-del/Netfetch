package com.netfetch.app.gateway.net

import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.InetAddress
import java.net.Socket

class NetfetchTcpSessionManagerTest {

    private val dummyConnector = object : NetfetchConnector {
        override suspend fun connectTcp(destination: InetAddress, port: Int): Socket {
            throw UnsupportedOperationException("Dummy connector")
        }
    }

    @Test
    fun testWriteToNonExistentSessionReturnsFalse() {
        val manager = NetfetchTcpSessionManager(dummyConnector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 1234,
            destinationIp = 0x7f000001,
            destinationPort = 80
        )

        val written = manager.write(key, byteArrayOf(1, 2, 3))
        assertFalse(written)
        manager.closeAll()
    }

    @Test
    fun testCloseNonExistentSessionDoesNotThrow() {
        val manager = NetfetchTcpSessionManager(dummyConnector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 5555,
            destinationIp = 0x7f000001,
            destinationPort = 443
        )

        manager.close(key)
        manager.closeAll()
    }
}
