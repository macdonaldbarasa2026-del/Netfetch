package com.netfetch.app.gateway.net

import android.net.VpnService
import com.netfetch.app.netfetchlink.NetfetchSocks5Client
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

/**
 * Receiver-side connector.
 *
 * TCP traffic goes through the discovered NetFetch provider's SOCKS5 server.
 * UDP is deliberately disabled because the current provider SOCKS5 server
 * does not implement UDP ASSOCIATE.
 */
class NetfetchReceiverConnector(
    private val vpnService: VpnService,
    private val providerHost: String,
    private val providerPort: Int,
    private val username: String,
    private val password: String
) : NetfetchConnector {

    private val socksClient = NetfetchSocks5Client(vpnService)

    override fun connectTcp(
        destination: InetAddress,
        port: Int,
        timeoutMs: Int
    ): Socket {
        if (providerHost.isBlank()) {
            throw IOException("NetFetch provider address is missing")
        }

        if (providerPort !in 1..65535) {
            throw IOException("Invalid NetFetch provider SOCKS port")
        }

        val destHost = destination.hostAddress
            ?: throw IOException("Invalid destination address")

        // If the destination is the provider host itself (e.g. link handshake, PAC or HTTP proxy port),
        // connect directly using a protected socket rather than routing through upstream SOCKS5.
        if (destHost == providerHost || destHost == "127.0.0.1") {
            val socket = Socket()
            if (!vpnService.protect(socket)) {
                throw IOException("Unable to protect direct provider connection")
            }
            socket.tcpNoDelay = true
            socket.connect(java.net.InetSocketAddress(destination, port), timeoutMs)
            return socket
        }

        return socksClient.connect(
            proxyHost = providerHost,
            proxyPort = providerPort,
            username = username,
            password = password,
            destinationHost = destHost,
            destinationPort = port
        )
    }

    override fun openUdp(): DatagramSocket {
        throw IOException(
            "Receiver UDP forwarding is unavailable: provider SOCKS5 UDP ASSOCIATE is not supported"
        )
    }

    /**
     * Content-based key for DNS query caching. Unlike [java.util.Arrays.hashCode],
     * this uses the full byte content for equality checks, preventing hash
     * collisions from returning wrong DNS responses.
     */
    internal class DnsQueryKey(val bytes: ByteArray) {
        private val hash = bytes.contentHashCode()
        override fun equals(other: Any?): Boolean =
            other is DnsQueryKey && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = hash
    }

    private val dnsCache = java.util.concurrent.ConcurrentHashMap<DnsQueryKey, Pair<Long, ByteArray>>()
    private val negativeCache = java.util.concurrent.ConcurrentHashMap<DnsQueryKey, Long>()

    override fun resolveDns(
        destination: InetAddress,
        queryData: ByteArray
    ): ByteArray? {
        if (queryData.size < 12) return null

        val cacheKey = if (queryData.size > 2) {
            DnsQueryKey(queryData.copyOfRange(2, queryData.size))
        } else null

        val now = System.currentTimeMillis()
        if (cacheKey != null) {
            val negExpiry = negativeCache[cacheKey]
            if (negExpiry != null && now < negExpiry) {
                return null
            }

            val cached = dnsCache[cacheKey]
            if (cached != null && now < cached.first) {
                val resp = cached.second.copyOf()
                resp[0] = queryData[0]
                resp[1] = queryData[1]
                return resp
            }
        }

        val targetDns = destination.hostAddress ?: "1.1.1.1"
        val serversToTry = buildList {
            add(targetDns)
            if (targetDns != "1.1.1.1") add("1.1.1.1")
            if (targetDns != "8.8.8.8") add("8.8.8.8")
        }

        var response: ByteArray? = null
        for (server in serversToTry) {
            response = queryDnsOverTcp(server, queryData)
            if (response != null) break
        }

        if (response != null && cacheKey != null) {
            if (dnsCache.size > 1000) {
                dnsCache.entries.removeIf { it.value.first <= now }
            }
            dnsCache[cacheKey] = Pair(now + 600_000L, response)
            negativeCache.remove(cacheKey)
        } else if (response == null && cacheKey != null) {
            negativeCache[cacheKey] = now + 5_000L
        }

        return response
    }

    private fun queryDnsOverTcp(dnsServerIp: String, queryData: ByteArray): ByteArray? {
        var socket: Socket? = null
        return try {
            socket = socksClient.connect(
                proxyHost = providerHost,
                proxyPort = providerPort,
                username = username,
                password = password,
                destinationHost = dnsServerIp,
                destinationPort = 53,
                connectTimeoutMs = 1500,
                soTimeoutMs = 1500
            )
            socket.soTimeout = 1500
            val out = socket.getOutputStream()
            out.write((queryData.size shr 8) and 0xFF)
            out.write(queryData.size and 0xFF)
            out.write(queryData)
            out.flush()

            val inStream = socket.getInputStream()
            val lenHigh = inStream.read()
            val lenLow = inStream.read()
            if (lenHigh < 0 || lenLow < 0) {
                return null
            }
            val respLen = (lenHigh shl 8) or lenLow
            if (respLen <= 0 || respLen > 4096) return null

            val resp = ByteArray(respLen)
            var offset = 0
            while (offset < respLen) {
                val count = inStream.read(resp, offset, respLen - offset)
                if (count < 0) break
                offset += count
            }
            if (offset == respLen) resp else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}
