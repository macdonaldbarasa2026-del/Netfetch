package com.netfetch.app.gateway.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetfetchReceiverConnectorTest {

    @Test
    fun testDnsQueryKeyEqualityAndHashCode() {
        val bytes1 = byteArrayOf(1, 2, 3, 4, 5)
        val bytes2 = byteArrayOf(1, 2, 3, 4, 5)
        val bytes3 = byteArrayOf(1, 2, 3, 4, 6)

        val key1 = NetfetchReceiverConnector.DnsQueryKey(bytes1)
        val key2 = NetfetchReceiverConnector.DnsQueryKey(bytes2)
        val key3 = NetfetchReceiverConnector.DnsQueryKey(bytes3)

        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())

        assertNotEquals(key1, key3)

        val map = HashMap<NetfetchReceiverConnector.DnsQueryKey, String>()
        map[key1] = "query1"
        assertEquals("query1", map[key2])

        map[key3] = "query3"
        assertEquals("query1", map[key1])
        assertEquals("query3", map[key3])
    }
}
