package com.netfetch.app.netfetchlink

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Receiver-side client for the NetFetch link-handshake protocol.
 *
 * Works with both Normal and Pro providers.
 */
class NetfetchLinkClient {

    companion object {
        private const val PORT = NetfetchLinkServer.PORT
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
    }

    /**
     * Session returned after a successful HELLO handshake.
     *
     * [mode] is one of [NetfetchLinkProtocol.MODE_NORMAL] or [NetfetchLinkProtocol.MODE_PRO].
     * [socksPort] is only meaningful when mode == PRO.
     * [httpPort] and [pacPort] are meaningful in both modes.
     */
    data class Session(
        val token: String,
        val mode: String,
        val httpPort: Int,
        val pacPort: Int,
        val socksPort: Int
    )

    fun refreshSession(
        providerHost: String,
        token: String
    ) {
        if (providerHost.isBlank()) {
            throw IOException("Provider address is missing")
        }

        if (token.length != 64) {
            throw IOException("Invalid NetFetch session token")
        }

        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(providerHost, PORT),
                CONNECT_TIMEOUT_MS
            )

            socket.soTimeout = READ_TIMEOUT_MS

            val reader = BufferedReader(
                InputStreamReader(
                    socket.getInputStream(),
                    Charsets.UTF_8
                )
            )

            val writer = PrintWriter(
                socket.getOutputStream(),
                true
            )

            writer.println("NETFETCH/1 REFRESH $token")

            val response = reader.readLine()
                ?: throw IOException("Provider closed the refresh connection")

            if (response != "NETFETCH/1 REFRESHED") {
                throw IOException(
                    if (response.startsWith("NETFETCH/1 ERROR ")) {
                        response.removePrefix("NETFETCH/1 ERROR ")
                    } else {
                        "Provider rejected session refresh"
                    }
                )
            }
        }
    }

    fun openSession(
        providerHost: String
    ): Session {
        if (providerHost.isBlank()) {
            throw IOException("Provider address is missing")
        }

        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(providerHost, PORT),
                CONNECT_TIMEOUT_MS
            )

            socket.soTimeout = READ_TIMEOUT_MS

            val reader = BufferedReader(
                InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
            )

            val writer = PrintWriter(
                socket.getOutputStream(),
                true
            )

            writer.println("NETFETCH/1 HELLO")

            val status = reader.readLine()
                ?: throw IOException("Provider closed the link connection")

            if (status != "NETFETCH/1 OK") {
                throw IOException("Provider rejected NetFetch link request")
            }

            var token: String? = null
            var mode = NetfetchLinkProtocol.MODE_PRO
            var httpPort = NetfetchLinkProtocol.DEFAULT_HTTP_PORT
            var pacPort = NetfetchLinkProtocol.DEFAULT_PAC_PORT
            var socksPort = NetfetchLinkProtocol.DEFAULT_SOCKS_PORT

            while (true) {
                val line = reader.readLine()
                    ?: throw IOException("Incomplete NetFetch link response")

                when {
                    line == "END" -> break

                    line.startsWith("SESSION ") -> {
                        token = line.removePrefix("SESSION ").trim()
                    }

                    line.startsWith("MODE ") -> {
                        mode = line.removePrefix("MODE ").trim()
                    }

                    line.startsWith("HTTP_PORT ") -> {
                        httpPort = line.removePrefix("HTTP_PORT ")
                            .trim()
                            .toIntOrNull()
                            ?: throw IOException("Invalid provider HTTP port")
                    }

                    line.startsWith("PAC_PORT ") -> {
                        pacPort = line.removePrefix("PAC_PORT ")
                            .trim()
                            .toIntOrNull()
                            ?: throw IOException("Invalid provider PAC port")
                    }

                    line.startsWith("SOCKS_PORT ") -> {
                        socksPort = line.removePrefix("SOCKS_PORT ")
                            .trim()
                            .toIntOrNull()
                            ?: throw IOException("Invalid provider SOCKS port")
                    }

                    line.startsWith("ERROR ") -> {
                        throw IOException(
                            "Provider link error: " +
                                line.removePrefix("ERROR ").trim()
                        )
                    }
                }
            }

            val sessionToken = token
                ?: throw IOException("Provider did not issue a session token")

            if (sessionToken.length != 64) {
                throw IOException("Invalid NetFetch session token")
            }

            if (httpPort !in 1..65535) {
                throw IOException("Invalid provider HTTP port")
            }

            if (pacPort !in 1..65535) {
                throw IOException("Invalid provider PAC port")
            }

            if (
                mode == NetfetchLinkProtocol.MODE_PRO &&
                socksPort !in 1..65535
            ) {
                throw IOException("Invalid provider SOCKS port")
            }

            return Session(
                token = sessionToken,
                mode = mode,
                httpPort = httpPort,
                pacPort = pacPort,
                socksPort = socksPort
            )
        }
    }
}
