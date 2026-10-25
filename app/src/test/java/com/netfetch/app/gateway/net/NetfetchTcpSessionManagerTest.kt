package com.netfetch.app.gateway.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.SocketChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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
    fun testFullQueueDequeueSignalsWindowCapacity() {
        val queue = BoundedTcpWriteQueue(capacity = 2)
        assertTrue(queue.offer(byteArrayOf(1)))
        assertTrue(queue.offer(byteArrayOf(2)))
        assertFalse(queue.offer(byteArrayOf(3)))

        val dequeued = queue.poll(timeoutMillis = 0L)

        assertTrue(dequeued?.wasFull == true)
        assertEquals(1, queue.remainingCapacity())
        queue.close()
    }

    @Test
    fun testRetiredConnectionDuringSetupIsNotPublished() {
        val setupStarted = CountDownLatch(1)
        val releaseSetup = CountDownLatch(1)
        val socketClosed = CountDownLatch(1)
        val socket = object : Socket() {
            private val closed = AtomicBoolean(false)

            override fun setReceiveBufferSize(size: Int) = Unit
            override fun setSendBufferSize(size: Int) = Unit
            override fun setKeepAlive(on: Boolean) = Unit
            override fun setTcpNoDelay(on: Boolean) = Unit

            override fun getOutputStream(): OutputStream {
                setupStarted.countDown()
                releaseSetup.await(3, TimeUnit.SECONDS)
                return ByteArrayOutputStream()
            }

            override fun getInputStream(): InputStream =
                ByteArrayInputStream(byteArrayOf())

            override fun close() {
                if (closed.compareAndSet(false, true)) {
                    socketClosed.countDown()
                }
            }
        }
        val connector = object : NetfetchConnector {
            override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket =
                socket

            override fun openUdp(): java.net.DatagramSocket {
                throw UnsupportedOperationException("Not used by this test")
            }
        }
        val manager = NetfetchTcpSessionManager(connector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 12345,
            destinationIp = 0x7f000001,
            destinationPort = 80
        )
        val connected = CountDownLatch(1)

        manager.open(
            key = key,
            destination = InetAddress.getByName("127.0.0.1"),
            destinationPort = 80,
            onConnected = { connected.countDown() },
            onConnectFailed = {},
            onData = { _, _ -> },
            onClosed = {}
        )

        try {
            assertTrue("Socket setup should start", setupStarted.await(3, TimeUnit.SECONDS))
            manager.closeSessions()
            releaseSetup.countDown()

            assertTrue("Retired socket should be closed", socketClosed.await(3, TimeUnit.SECONDS))
            assertFalse("Retired connection must not be published", connected.await(100, TimeUnit.MILLISECONDS))
            assertEquals(0, manager.count())
            assertFalse(manager.isConnecting(key))
        } finally {
            releaseSetup.countDown()
            manager.closeAll()
        }
    }

    @Test
    fun testSelectorTransportForwardsOrderedBytes() {
        val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val expected = ByteArray(1460 * 64) { (it % 251).toByte() }
        val received = CountDownLatch(1)
        val actual = ByteArrayOutputStream(expected.size)
        val serverThread = thread(isDaemon = true) {
            serverSocket.accept().use { accepted ->
                val buffer = ByteArray(8192)
                while (actual.size() < expected.size) {
                    val count = accepted.getInputStream().read(buffer)
                    if (count < 0) break
                    actual.write(buffer, 0, count)
                }
                received.countDown()
            }
        }
        val connector = object : NetfetchSelectableConnector {
            override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket =
                throw UnsupportedOperationException("Selector transport should open a channel")

            override fun openTcpChannel(): SocketChannel = SocketChannel.open()

            override fun openUdp(): java.net.DatagramSocket =
                throw UnsupportedOperationException("UDP is not used by this test")
        }
        val manager = NetfetchSelectorTcpSessionManager(connector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 54322,
            destinationIp = 0x7f000001,
            destinationPort = serverSocket.localPort
        )
        val connected = CountDownLatch(1)

        try {
            assertTrue(
                manager.open(
                    key = key,
                    destination = InetAddress.getByName("127.0.0.1"),
                    destinationPort = serverSocket.localPort,
                    onConnected = { connected.countDown() },
                    onConnectFailed = {},
                    onData = { _, _ -> },
                    onClosed = {}
                )
            )
            assertTrue("Selector connect should complete", connected.await(3, TimeUnit.SECONDS))

            for (offset in expected.indices step 1460) {
                val end = (offset + 1460).coerceAtMost(expected.size)
                assertEquals(
                    NetfetchTcpSessionManager.WriteResult.ACCEPTED,
                    manager.write(key, expected.copyOfRange(offset, end))
                )
            }

            assertTrue("Server should receive all bytes", received.await(5, TimeUnit.SECONDS))
            assertArrayEquals(expected, actual.toByteArray())
        } finally {
            manager.closeAll()
            serverSocket.close()
            serverThread.join(1_000L)
        }
    }

    @Test
    fun testSelectorBackpressureResumesInOrderAndRetiresOnHandover() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val serverSocket = ServerSocket(0, 1, loopback)
        val accepted = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val receivedAll = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val targetBytes = java.util.concurrent.atomic.AtomicInteger()
        val actual = ByteArrayOutputStream()
        val serverThread = thread(isDaemon = true) {
            serverSocket.accept().use { socket ->
                socket.soTimeout = 10_000
                accepted.countDown()
                allowRead.await(5, TimeUnit.SECONDS)
                val buffer = ByteArray(8192)
                while (actual.size() < targetBytes.get()) {
                    val count = socket.getInputStream().read(buffer)
                    if (count < 0) break
                    actual.write(buffer, 0, count)
                }
                receivedAll.countDown()
                if (socket.getInputStream().read() < 0) {
                    remoteClosed.countDown()
                }
            }
        }
        val connector = object : NetfetchSelectableConnector {
            override fun openTcpChannel(): SocketChannel =
                SocketChannel.open().apply { socket().sendBufferSize = 1024 }

            override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket =
                throw UnsupportedOperationException("Selector transport should open a channel")

            override fun openUdp(): java.net.DatagramSocket =
                throw UnsupportedOperationException("UDP is not used by this test")
        }
        val manager = NetfetchSelectorTcpSessionManager(connector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 54323,
            destinationIp = 0x7f000001,
            destinationPort = serverSocket.localPort
        )
        val connected = CountDownLatch(1)
        val queueAvailable = CountDownLatch(1)
        val expected = ByteArrayOutputStream()

        try {
            assertTrue(
                manager.open(
                    key = key,
                    destination = loopback,
                    destinationPort = serverSocket.localPort,
                    onConnected = { connected.countDown() },
                    onConnectFailed = {},
                    onData = { _, _ -> },
                    onClosed = {},
                    onWriteQueueAvailable = { queueAvailable.countDown() }
                )
            )
            assertTrue("Server should accept the connection", accepted.await(3, TimeUnit.SECONDS))
            assertTrue("Selector connect should complete", connected.await(3, TimeUnit.SECONDS))

            var queueFull = false
            for (segment in 0 until 8_192) {
                val payload = ByteArray(1460) { index ->
                    ((segment + index) % 251).toByte()
                }
                when (manager.write(key, payload)) {
                    NetfetchTcpSessionManager.WriteResult.ACCEPTED -> expected.write(payload)
                    NetfetchTcpSessionManager.WriteResult.QUEUE_FULL -> {
                        queueFull = true
                        break
                    }
                    NetfetchTcpSessionManager.WriteResult.NO_SESSION -> {
                        throw AssertionError("TCP session retired before queue saturation")
                    }
                }
            }
            assertTrue("A stalled peer should exercise bounded TCP backpressure", queueFull)
            targetBytes.set(expected.size())
            allowRead.countDown()

            assertTrue("Draining the full queue should wake the TCP engine", queueAvailable.await(5, TimeUnit.SECONDS))
            assertTrue("Peer should receive all accepted bytes", receivedAll.await(10, TimeUnit.SECONDS))
            assertArrayEquals(expected.toByteArray(), actual.toByteArray())

            manager.closeSessions()
            assertTrue("Handover cleanup should close the upstream socket", remoteClosed.await(3, TimeUnit.SECONDS))
            assertEquals(0, manager.count())
        } finally {
            allowRead.countDown()
            manager.closeAll()
            serverSocket.close()
            serverThread.join(1_000L)
        }
    }

    @Test
    fun testWriteToNonExistentSessionReturnsNoSession() {
        val manager = NetfetchTcpSessionManager(dummyConnector)
        val key = TcpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 1234,
            destinationIp = 0x7f000001,
            destinationPort = 80
        )

        val result = manager.write(key, byteArrayOf(1, 2, 3))
        assertEquals(
            NetfetchTcpSessionManager.WriteResult.NO_SESSION,
            result
        )
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
