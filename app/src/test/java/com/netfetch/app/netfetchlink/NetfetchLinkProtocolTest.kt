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
