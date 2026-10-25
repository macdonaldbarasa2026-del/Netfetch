package com.netfetch.app.gateway.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.channels.DatagramChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class NetfetchSelectorUdpSessionManagerTest {

    @Test
    fun selectorTransportPreservesDatagramBoundaries() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val server = DatagramSocket(0, loopback)
        server.soTimeout = 3_000
        val receivedByServer = CopyOnWriteArrayList<ByteArray>()
        val receivedByClient = CopyOnWriteArrayList<ByteArray>()
        val responses = CountDownLatch(2)
        val serverThread = thread(isDaemon = true) {
            repeat(2) {
                val buffer = ByteArray(128)
                val datagram = DatagramPacket(buffer, buffer.size)
                server.receive(datagram)
                val contents = datagram.data.copyOf(datagram.length)
                receivedByServer += contents
                server.send(
                    DatagramPacket(
                        contents,
                        contents.size,
                        datagram.socketAddress
                    )
                )
            }
        }
        val connector = object : NetfetchSelectableUdpConnector {
            override fun openUdpChannel(): DatagramChannel = DatagramChannel.open()

            override fun connectTcp(
                destination: InetAddress,
                port: Int,
                timeoutMs: Int
            ): Socket = throw UnsupportedOperationException("TCP is not used")

            override fun openUdp(): DatagramSocket =
                throw UnsupportedOperationException("Selector UDP should be used")
        }
        val manager = NetfetchSelectorUdpSessionManager(connector)
        val key = UdpFlowKey(
            sourceIp = 0x7f000001,
            sourcePort = 43210,
            destinationIp = 0x7f000001,
            destinationPort = server.localPort
        )
        val expected = listOf(
            byteArrayOf(1, 2, 3),
            byteArrayOf(9, 8, 7, 6, 5)
        )

        try {
            assertTrue(
                manager.open(
                    key = key,
                    destination = loopback,
                    destinationPort = server.localPort,
                    onData = { _, data ->
                        receivedByClient += data
                        responses.countDown()
                    },
                    onClosed = {}
                )
            )

            expected.forEach { datagram ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                var accepted = false
                while (!accepted && System.nanoTime() < deadline) {
                    accepted = manager.send(key, datagram)
                    if (!accepted) Thread.sleep(5)
                }
                assertTrue("Datagram should be accepted before the deadline", accepted)
            }

            assertTrue("Both response datagrams should arrive", responses.await(4, TimeUnit.SECONDS))
            assertEquals(2, receivedByServer.size)
            assertEquals(2, receivedByClient.size)
            expected.forEachIndexed { index, datagram ->
                assertArrayEquals(datagram, receivedByServer[index])
                assertArrayEquals(datagram, receivedByClient[index])
            }
        } finally {
            manager.closeAll()
            server.close()
            serverThread.join(1_000L)
        }
    }
}
