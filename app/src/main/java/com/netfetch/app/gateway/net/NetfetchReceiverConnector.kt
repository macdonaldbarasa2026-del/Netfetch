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

        return socksClient.connect(
            proxyHost = providerHost,
            proxyPort = providerPort,
            username = username,
            password = password,
            destinationHost = destination.hostAddress
                ?: throw IOException("Invalid destination address"),
            destinationPort = port
        )
    }

    override fun openUdp(): DatagramSocket {
        throw IOException(
            "Receiver UDP forwarding is unavailable: provider SOCKS5 UDP ASSOCIATE is not supported"
        )
    }

    private val dnsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ByteArray>>()

    override fun resolveDns(
        destination: InetAddress,
        queryData: ByteArray
    ): ByteArray? {
        if (queryData.size < 12) return null

        val cacheKey = if (queryData.size > 2) {
            queryData.copyOfRange(2, queryData.size).contentToString()
        } else ""

        if (cacheKey.isNotEmpty()) {
            val cached = dnsCache[cacheKey]
            if (cached != null && System.currentTimeMillis() < cached.first) {
                val resp = cached.second.copyOf()
                resp[0] = queryData[0]
                resp[1] = queryData[1]
                return resp
            }
        }

        val targetDns = destination.hostAddress ?: "1.1.1.1"
        val response = queryDnsOverTcp(targetDns, queryData)
            ?: queryDnsOverTcp("1.1.1.1", queryData)
            ?: queryDnsOverTcp("8.8.8.8", queryData)

        if (response != null && cacheKey.isNotEmpty()) {
            dnsCache[cacheKey] = Pair(System.currentTimeMillis() + 60_000L, response)
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
                destinationPort = 53
            )
            socket.soTimeout = 4000
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
