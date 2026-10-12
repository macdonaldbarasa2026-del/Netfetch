package com.netfetch.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetfetchDiagnosticsTest {

    @Before
    fun setUp() {
        NetfetchDiagnostics.reset()
    }

    @Test
    fun testInitialSnapshotHasZeroMetrics() {
        val snapshot = NetfetchDiagnostics.snapshot.value
        assertNotNull(snapshot)
        assertEquals(0L, snapshot.lastDnsLookupMs)
        assertEquals(0L, snapshot.averageDnsLookupMs)
        assertEquals(0L, snapshot.lastTcpConnectMs)
        assertEquals(0L, snapshot.uploadSpeedBps)
        assertEquals(0L, snapshot.downloadSpeedBps)
        assertEquals(0L, snapshot.totalBytesUploaded)
        assertEquals(0L, snapshot.totalBytesDownloaded)
        assertEquals(0, snapshot.activeClientCount)
        assertEquals(0L, snapshot.totalDisconnects)
        assertEquals(0L, snapshot.totalReconnectAttempts)
    }

    @Test
    fun testRecordDnsLookupDuration() {
        NetfetchDiagnostics.recordDnsLookup(50L, isCacheHit = false)
        NetfetchDiagnostics.recordDnsLookup(10L, isCacheHit = true)

        val snapshot = NetfetchDiagnostics.snapshot.value
        assertEquals(50L, snapshot.lastDnsLookupMs)
        assertEquals(1L, snapshot.dnsCacheMisses)
        assertEquals(1L, snapshot.dnsCacheHits)
    }

    @Test
    fun testRecordConnectEstablishment() {
        NetfetchDiagnostics.recordTcpConnect(120L)
        val snapshot = NetfetchDiagnostics.snapshot.value
        assertEquals(120L, snapshot.lastTcpConnectMs)
        assertEquals(120L, snapshot.averageTcpConnectMs)

        NetfetchDiagnostics.recordTcpConnect(80L)
        val updated = NetfetchDiagnostics.snapshot.value
        assertEquals(80L, updated.lastTcpConnectMs)
        assertEquals(100L, updated.averageTcpConnectMs)
    }

    @Test
    fun testThroughputCounters() {
        NetfetchDiagnostics.recordThroughput(
            upSpeed = 5000L,
            downSpeed = 15000L,
            totalUp = 1024L,
            totalDown = 4096L,
            clientCount = 3
        )

        val snapshot = NetfetchDiagnostics.snapshot.value
        assertEquals(5000L, snapshot.uploadSpeedBps)
        assertEquals(15000L, snapshot.downloadSpeedBps)
        assertEquals(1024L, snapshot.totalBytesUploaded)
        assertEquals(4096L, snapshot.totalBytesDownloaded)
        assertEquals(3, snapshot.activeClientCount)
    }

    @Test
    fun testDisconnectAndReconnectCounters() {
        NetfetchDiagnostics.recordDisconnect("Client closed stream")
        NetfetchDiagnostics.recordReconnectAttempt("Upstream switched to cellular")
        NetfetchDiagnostics.recordReconnectAttempt("Retry backoff jittered")

        val snapshot = NetfetchDiagnostics.snapshot.value
        assertEquals(1L, snapshot.totalDisconnects)
        assertEquals(2L, snapshot.totalReconnectAttempts)
        assertEquals("Retry backoff jittered", snapshot.lastFailureReason)
    }

    @Test
    fun testUpstreamReachability() {
        NetfetchDiagnostics.recordUpstreamState(
            type = "WIFI",
            displayName = "Home-5G",
            hasInternet = true
        )
        NetfetchDiagnostics.recordReachabilityProbe(
            durationMs = 85L,
            success = true
        )

        val snapshot = NetfetchDiagnostics.snapshot.value
        assertEquals("WIFI", snapshot.upstreamType)
        assertEquals("Home-5G", snapshot.upstreamDisplayName)
        assertTrue(snapshot.isInternetVerified)
        assertEquals(85L, snapshot.lastReachabilityProbeMs)
    }
}
