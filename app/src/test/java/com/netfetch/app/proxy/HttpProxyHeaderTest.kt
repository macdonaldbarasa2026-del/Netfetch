package com.netfetch.app.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class HttpProxyHeaderTest {

    private fun parseHeaders(rawHttp: String): List<String> {
        val bytes = rawHttp.toByteArray(Charsets.ISO_8859_1)
        val input = java.io.BufferedInputStream(ByteArrayInputStream(bytes), 4096)
        val maxHeaderBytes = 64 * 1024

        if (input.markSupported()) {
            input.mark(maxHeaderBytes)
            val chunk = ByteArray(4096)
            val headerBytes = java.io.ByteArrayOutputStream()
            var totalRead = 0
            var foundEnd = false
            var p3 = -1; var p2 = -1; var p1 = -1

            while (totalRead < maxHeaderBytes) {
                val toRead = minOf(chunk.size, maxHeaderBytes - totalRead)
                val n = input.read(chunk, 0, toRead)
                if (n <= 0) break

                for (i in 0 until n) {
                    val b = chunk[i].toInt() and 0xFF
                    headerBytes.write(b)
                    if (p3 == '\r'.code && p2 == '\n'.code && p1 == '\r'.code && b == '\n'.code) {
                        foundEnd = true
                        totalRead += (i + 1)
                        break
                    }
                    p3 = p2
                    p2 = p1
                    p1 = b
                }

                if (foundEnd) {
                    input.reset()
                    var skipped = 0L
                    while (skipped < totalRead) {
                        val s = input.skip(totalRead - skipped)
                        if (s <= 0) {
                            if (input.read() == -1) break
                            skipped++
                        } else {
                            skipped += s
                        }
                    }
                    break
                } else {
                    totalRead += n
                }
            }

            if (headerBytes.size() == 0) return emptyList()
            val headerText = headerBytes.toString(Charsets.ISO_8859_1.name())
            if (!headerText.endsWith("\r\n\r\n")) {
                throw IOException("Incomplete HTTP header block")
            }
            return headerText
                .removeSuffix("\r\n\r\n")
                .split("\r\n")
                .filter { it.isNotEmpty() }
        }

        return emptyList()
    }

    @Test
    fun testStandardGetRequestHeaders() {
        val raw = "GET /index.html HTTP/1.1\r\nHost: example.com\r\nUser-Agent: Netfetch/1.0\r\n\r\n"
        val headers = parseHeaders(raw)
        assertEquals(3, headers.size)
        assertEquals("GET /index.html HTTP/1.1", headers[0])
        assertEquals("Host: example.com", headers[1])
        assertEquals("User-Agent: Netfetch/1.0", headers[2])
    }

    @Test
    fun testPostRequestPreservesBodyStream() {
        val raw = "POST /api/upload HTTP/1.1\r\nHost: example.com\r\nContent-Length: 5\r\n\r\nHELLO"
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val input = java.io.BufferedInputStream(ByteArrayInputStream(bytes), 4096)
        val maxHeaderBytes = 64 * 1024

        input.mark(maxHeaderBytes)
        val chunk = ByteArray(4096)
        val headerBytes = java.io.ByteArrayOutputStream()
        var totalRead = 0
        var foundEnd = false
        var p3 = -1; var p2 = -1; var p1 = -1

        while (totalRead < maxHeaderBytes) {
            val toRead = minOf(chunk.size, maxHeaderBytes - totalRead)
            val n = input.read(chunk, 0, toRead)
            if (n <= 0) break

            for (i in 0 until n) {
                val b = chunk[i].toInt() and 0xFF
                headerBytes.write(b)
                if (p3 == '\r'.code && p2 == '\n'.code && p1 == '\r'.code && b == '\n'.code) {
                    foundEnd = true
                    totalRead += (i + 1)
                    break
                }
                p3 = p2
                p2 = p1
                p1 = b
            }

            if (foundEnd) {
                input.reset()
                var skipped = 0L
                while (skipped < totalRead) {
                    val s = input.skip(totalRead - skipped)
                    if (s <= 0) {
                        if (input.read() == -1) break
                        skipped++
                    } else {
                        skipped += s
                    }
                }
                break
            } else {
                totalRead += n
            }
        }

        val headerText = headerBytes.toString(Charsets.ISO_8859_1.name())
        assertTrue(headerText.endsWith("\r\n\r\n"))

        // Read remaining body from input
        val bodyBytes = ByteArray(5)
        val bodyRead = input.read(bodyBytes)
        assertEquals(5, bodyRead)
        assertEquals("HELLO", String(bodyBytes, Charsets.ISO_8859_1))
    }

    @Test
    fun testIncompleteHeaderThrowsException() {
        val raw = "GET /index.html HTTP/1.1\r\nHost: example.com\r\n"
        try {
            parseHeaders(raw)
            fail("Expected IOException for incomplete header")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Incomplete"))
        }
    }

    @Test
    fun testConnectTunnelHeader() {
        val raw = "CONNECT www.google.com:443 HTTP/1.1\r\nHost: www.google.com:443\r\nProxy-Connection: keep-alive\r\n\r\n"
        val headers = parseHeaders(raw)
        assertEquals(3, headers.size)
        assertEquals("CONNECT www.google.com:443 HTTP/1.1", headers[0])
        assertEquals("Host: www.google.com:443", headers[1])
        assertEquals("Proxy-Connection: keep-alive", headers[2])
    }
}
