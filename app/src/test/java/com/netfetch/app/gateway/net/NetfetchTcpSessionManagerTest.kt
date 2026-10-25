package com.netfetch.app.gateway.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NetfetchTcpSessionManagerTest {

    private val dummyConnector = object : NetfetchConnector {
        override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket {
            throw UnsupportedOperationException("Dummy connector")
        }

        override fun openUdp(): java.net.DatagramSocket {
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

    @Test
    fun testAvailableReceiveWindowDefaultWhenNoSession() {
        val manager = NetfetchTcpSessionManager(dummyConnector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 9999,
            destinationIp = 0x7f000001,
            destinationPort = 8080
        )
        val window = manager.availableReceiveWindow(key)
        assertEquals(65_535, window)
        assertFalse(manager.isConnecting(key))
        manager.closeAll()
    }

    @Test
    fun testAvailableReceiveWindowScalesWithQueueCapacity() {
        val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverPort = serverSocket.localPort

        val loopbackConnector = object : NetfetchConnector {
            override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket {
                return Socket(destination, port)
            }

            override fun openUdp(): java.net.DatagramSocket {
                throw UnsupportedOperationException()
            }
        }

        val manager = NetfetchTcpSessionManager(loopbackConnector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 54321,
            destinationIp = 0x7f000001,
            destinationPort = serverPort
        )

        val connectedLatch = CountDownLatch(1)
        manager.open(
            key = key,
            destination = InetAddress.getByName("127.0.0.1"),
            destinationPort = serverPort,
            onConnected = { connectedLatch.countDown() },
            onConnectFailed = {},
            onData = { _, _ -> },
            onClosed = {}
        )

        val serverAccepted = serverSocket.accept()
        assertTrue("Session should connect", connectedLatch.await(3, TimeUnit.SECONDS))

        // When queue is empty (256 slots remaining), (256 * 1460).coerceIn(0, 65_535) returns 65,535
        val window = manager.availableReceiveWindow(key)
        assertEquals(65_535, window)

        manager.close(key)
        manager.closeAll()
        serverAccepted.close()
        serverSocket.close()
    }
}

