package com.netfetch.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class NetfetchDnsEngineTest {

    @Before
    fun setUp() {
        NetfetchDnsEngine.clearCache()
    }

    @Test
    fun testNumericIpv4BypassDns() {
        val result = NetfetchDnsEngine.resolve("192.168.49.1")
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("192.168.49.1", result[0].hostAddress)
    }

    @Test
    fun testNumericIpv6BypassDns() {
        val result = NetfetchDnsEngine.resolve("::1")
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(InetAddress.getByName("::1"), result[0])
    }

    @Test
    fun testWellKnownHostSeeded() {
        val googleDns = NetfetchDnsEngine.resolve("dns.google")
        assertNotNull(googleDns)
        assertTrue(googleDns.isNotEmpty())
        assertEquals("8.8.8.8", googleDns[0].hostAddress)

        val one = NetfetchDnsEngine.resolve("one.one.one.one")
        assertNotNull(one)
        assertTrue(one.isNotEmpty())
        assertEquals("1.1.1.1", one[0].hostAddress)
    }

    @Test
    fun testInvalidateOnNetworkChange() {
        // Resolve seeded host
        val before = NetfetchDnsEngine.resolve("dns.google")
        assertNotNull(before)

        // Invalidating on network change should not crash and should re-seed well-known hosts
        NetfetchDnsEngine.invalidateOnNetworkChange()
        val after = NetfetchDnsEngine.resolve("dns.google")
        assertNotNull(after)
        assertEquals("8.8.8.8", after[0].hostAddress)
    }

    @Test(expected = UnknownHostException::class)
    fun testNegativeCacheBlocksRepeatedFailures() {
        val invalidHost = "invalid.domain.that.does.not.exist.test"
        try {
            NetfetchDnsEngine.resolve(invalidHost)
        } catch (e: UnknownHostException) {
            // First failure triggers negative cache; second should fail immediately via negative cache
            NetfetchDnsEngine.resolve(invalidHost)
        }
    }
}
