package com.netfetch.app.netfetchlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.channels.SocketChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class NetfetchSocks5ChannelHandshakeTest {

    @Test
    fun selectorHandshakeAuthenticatesAndRequestsDestination() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverFailure = AtomicReference<Throwable?>(null)
        val serverDone = CountDownLatch(1)
        val serverThread = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())

                    assertArrayEquals(byteArrayOf(5, 2, 0, 2), readExact(input, 4))
                    output.write(byteArrayOf(5, 2))
                    output.flush()

                    assertArrayEquals(
                        byteArrayOf(1, 4, 'u'.code.toByte(), 's'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte(), 4,
                            'p'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 's'.code.toByte()),
                        readExact(input, 11)
                    )
                    output.write(byteArrayOf(1, 0))
                    output.flush()

                    assertArrayEquals(byteArrayOf(5, 1, 0, 1), readExact(input, 4))
                    assertArrayEquals(byteArrayOf(1, 2, 3, 4, 0x01, 0xbb.toByte()), readExact(input, 6))
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                    output.flush()
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            } finally {
                serverDone.countDown()
            }
        }
        val channel = SocketChannel.open()

        try {
            channel.connect(java.net.InetSocketAddress("127.0.0.1", server.localPort))
            val handshake = NetfetchSocks5ChannelHandshake(
                username = "user",
                password = "pass",
                destination = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4)),
                destinationPort = 443
            )

            assertTrue("SOCKS5 handshake should complete", handshake.advance(channel))
            assertTrue("Fake SOCKS server should finish", serverDone.await(2, TimeUnit.SECONDS))
            serverFailure.get()?.let { throw AssertionError("Fake SOCKS server failed", it) }
        } finally {
            channel.close()
            server.close()
            serverThread.join(1_000L)
        }
    }

    private fun readExact(input: DataInputStream, size: Int): ByteArray {
        val data = ByteArray(size)
        input.readFully(data)
        return data
    }
}
