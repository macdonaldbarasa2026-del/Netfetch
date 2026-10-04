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
}
