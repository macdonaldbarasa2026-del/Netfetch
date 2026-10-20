package com.netfetch.app.netfetchlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class NetfetchLinkProtocolTest {

    @Test
    fun testProtocolConstants() {
        assertEquals("1", NetfetchLinkProtocol.VERSION)
        assertEquals("netfetch", NetfetchLinkProtocol.APP)
        assertEquals("NORMAL", NetfetchLinkProtocol.MODE_NORMAL)
        assertEquals("PRO", NetfetchLinkProtocol.MODE_PRO)
        assertEquals(8282, NetfetchLinkProtocol.DEFAULT_HTTP_PORT)
        assertEquals(8283, NetfetchLinkProtocol.DEFAULT_PAC_PORT)
        assertEquals(1080, NetfetchLinkProtocol.DEFAULT_SOCKS_PORT)
        assertFalse(NetfetchLinkProtocol.UDP_TRANSPORT_AVAILABLE)
    }

    @Test
    fun testSessionValidationLifecycle() {
        val server = NetfetchLinkServer(
            mode = NetfetchLinkProtocol.MODE_NORMAL,
            socksPort = 1080,
            httpPort = 8282,
            pacPort = 8283
        )

        // Non-existent token fails
        assertFalse(server.validateSession("non-existent-token", "192.168.49.2"))
        assertFalse(server.isClientAuthorized("192.168.49.2"))

        // Create token for client address
        val createTokenMethod = NetfetchLinkServer::class.java.getDeclaredMethod("createToken", String::class.java)
        createTokenMethod.isAccessible = true
        val token = createTokenMethod.invoke(server, "192.168.49.2") as String
        assertEquals(64, token.length)

        // Validate with exact address
        assertTrue(server.validateSession(token, "192.168.49.2"))
        assertTrue(server.isClientAuthorized("192.168.49.2"))

        // Validate with IPv4-mapped IPv6 address (dual-stack socket format)
        assertTrue(server.validateSession(token, "::ffff:192.168.49.2"))
        assertTrue(server.isClientAuthorized("::ffff:192.168.49.2"))

        // Reject different client address
        assertFalse(server.validateSession(token, "192.168.49.9"))
    }

    @Test
    fun testClientServerEndToEndHandshakeAndRefresh() {
        val testPort = java.net.ServerSocket(0).use { it.localPort }
        val server = NetfetchLinkServer(
            mode = NetfetchLinkProtocol.MODE_PRO,
            socksPort = 1080,
            httpPort = 8282,
            pacPort = 8283,
            port = testPort
        )
        server.start()

        try {
            // Allow server thread to bind
            Thread.sleep(150)

            val client = NetfetchLinkClient()
            val session = client.openSession("127.0.0.1", port = testPort)

            assertEquals(64, session.token.length)
            assertEquals(NetfetchLinkProtocol.MODE_PRO, session.mode)
            assertEquals(8282, session.httpPort)
            assertEquals(8283, session.pacPort)
            assertEquals(1080, session.socksPort)

            assertTrue(server.validateSession(session.token, "127.0.0.1"))
            assertTrue(server.validateSession(session.token, "::ffff:127.0.0.1"))

            // Refresh session
            client.refreshSession("127.0.0.1", session.token, port = testPort)
            assertTrue(server.validateSession(session.token, "127.0.0.1"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun testReadBoundedLineWithinBounds() {
        val input = "NETFETCH/1 HELLO\r\n"
        val reader = BufferedReader(StringReader(input))
        val method = NetfetchLinkServer::class.java.getDeclaredMethod(
            "readBoundedLine",
            BufferedReader::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        val server = NetfetchLinkServer(NetfetchLinkProtocol.MODE_NORMAL)

        val result = method.invoke(server, reader, 2048) as String
        assertEquals("NETFETCH/1 HELLO", result)
    }

    @Test(expected = Exception::class)
    fun testReadBoundedLineExceedsLimit() {
        val longString = "A".repeat(3000) + "\n"
        val reader = BufferedReader(StringReader(longString))
        val method = NetfetchLinkServer::class.java.getDeclaredMethod(
            "readBoundedLine",
            BufferedReader::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        val server = NetfetchLinkServer(NetfetchLinkProtocol.MODE_NORMAL)

        try {
            method.invoke(server, reader, 2048)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
