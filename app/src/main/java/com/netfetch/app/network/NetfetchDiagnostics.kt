package com.netfetch.app.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnostics and Observability Engine for Netfetch.
 *
 * Provides real measured metrics for upstream health, DNS resolution latency,
 * TCP connection establishment latency, proxy throughput, client counts,
 * and error/reconnect telemetry.
 *
 * Metrics are strictly derived from real measurements (no fabricated values).
 */
object NetfetchDiagnostics {

    data class Snapshot(
        val upstreamType: String = "None",
        val upstreamDisplayName: String = "No Internet",
        val isInternetVerified: Boolean = false,
        val lastReachabilityProbeMs: Long = 0L,

        val lastDnsLookupMs: Long = 0L,
        val averageDnsLookupMs: Long = 0L,
        val dnsCacheHits: Long = 0L,
        val dnsCacheMisses: Long = 0L,

        val lastTcpConnectMs: Long = 0L,
        val averageTcpConnectMs: Long = 0L,

        val roundTripLatencyMs: Long = 0L,

        val uploadSpeedBps: Long = 0L,
        val downloadSpeedBps: Long = 0L,
        val totalBytesUploaded: Long = 0L,
        val totalBytesDownloaded: Long = 0L,

        val activeClientCount: Int = 0,
        val totalDisconnects: Long = 0L,
        val totalReconnectAttempts: Long = 0L,
        val lastFailureReason: String = ""
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    // Internal accumulators for averages and counters
    private val dnsLookupCount = AtomicLong(0L)
    private val dnsLookupTotalMs = AtomicLong(0L)
    private val dnsCacheHitsCount = AtomicLong(0L)
    private val dnsCacheMissesCount = AtomicLong(0L)

    private val tcpConnectCount = AtomicLong(0L)
    private val tcpConnectTotalMs = AtomicLong(0L)

    private val disconnectCount = AtomicLong(0L)
    private val reconnectCount = AtomicLong(0L)

    @Volatile
    private var lastFailureMessage: String = ""

    fun recordUpstreamState(
        type: String,
        displayName: String,
        hasInternet: Boolean
    ) {
        update {
            it.copy(
                upstreamType = type,
                upstreamDisplayName = displayName,
                isInternetVerified = hasInternet
            )
        }
    }

    fun recordReachabilityProbe(durationMs: Long, success: Boolean) {
        update {
            it.copy(
                lastReachabilityProbeMs = durationMs,
                isInternetVerified = success
            )
        }
    }

    fun recordDnsLookup(durationMs: Long, isCacheHit: Boolean) {
        if (isCacheHit) {
            dnsCacheHitsCount.incrementAndGet()
        } else {
            dnsCacheMissesCount.incrementAndGet()
            val count = dnsLookupCount.incrementAndGet()
            val total = dnsLookupTotalMs.addAndGet(durationMs)
            val avg = total / count.coerceAtLeast(1L)
            update {
                it.copy(
                    lastDnsLookupMs = durationMs,
                    averageDnsLookupMs = avg,
                    dnsCacheHits = dnsCacheHitsCount.get(),
                    dnsCacheMisses = dnsCacheMissesCount.get()
                )
            }
            return
        }
        update {
            it.copy(
                dnsCacheHits = dnsCacheHitsCount.get(),
                dnsCacheMisses = dnsCacheMissesCount.get()
            )
        }
    }

    fun recordTcpConnect(durationMs: Long) {
        val count = tcpConnectCount.incrementAndGet()
        val total = tcpConnectTotalMs.addAndGet(durationMs)
        val avg = total / count.coerceAtLeast(1L)
        update {
            it.copy(
                lastTcpConnectMs = durationMs,
                averageTcpConnectMs = avg
            )
        }
    }

    fun recordRoundTripLatency(rttMs: Long) {
        update {
            it.copy(roundTripLatencyMs = rttMs)
        }
    }

    fun recordThroughput(
        upSpeed: Long,
        downSpeed: Long,
        totalUp: Long,
        totalDown: Long,
        clientCount: Int
    ) {
        update {
            it.copy(
                uploadSpeedBps = upSpeed,
                downloadSpeedBps = downSpeed,
                totalBytesUploaded = totalUp,
                totalBytesDownloaded = totalDown,
                activeClientCount = clientCount
            )
        }
    }

    fun recordDisconnect(reason: String? = null) {
        disconnectCount.incrementAndGet()
        if (!reason.isNullOrBlank()) {
            lastFailureMessage = reason
        }
        update {
            it.copy(
                totalDisconnects = disconnectCount.get(),
                lastFailureReason = lastFailureMessage
            )
        }
    }

    fun recordReconnectAttempt(reason: String? = null) {
        reconnectCount.incrementAndGet()
        if (!reason.isNullOrBlank()) {
            lastFailureMessage = reason
        }
        update {
            it.copy(
                totalReconnectAttempts = reconnectCount.get(),
                lastFailureReason = lastFailureMessage
            )
        }
    }

    fun recordFailure(reason: String) {
        lastFailureMessage = reason
        update {
            it.copy(lastFailureReason = reason)
        }
    }

    private inline fun update(transform: (Snapshot) -> Snapshot) {
        while (true) {
            val current = _snapshot.value
            val next = transform(current)
            if (_snapshot.compareAndSet(current, next)) {
                break
            }
        }
    }

    fun reset() {
        dnsLookupCount.set(0L)
        dnsLookupTotalMs.set(0L)
        dnsCacheHitsCount.set(0L)
        dnsCacheMissesCount.set(0L)
        tcpConnectCount.set(0L)
        tcpConnectTotalMs.set(0L)
        disconnectCount.set(0L)
        reconnectCount.set(0L)
        lastFailureMessage = ""
        _snapshot.value = Snapshot()
    }
}
