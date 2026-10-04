package com.netfetch.app.netfetchlink

import android.net.VpnService
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class NetfetchSocks5Client(
    private val vpnService: VpnService
) {

    companion object {
        private const val VERSION = 5
        private const val AUTH_USERNAME_PASSWORD = 2
        private const val AUTH_NONE = 0
        private const val AUTH_VERSION = 1
        private const val CONNECT = 1

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 30_000
    }

    fun connect(
        proxyHost: String,
        proxyPort: Int,
        username: String,
        password: String,
        destinationHost: String,
        destinationPort: Int
    ): Socket {

        val socket = Socket()

        try {
            /*
             * Critical:
             * The SOCKS connection itself must bypass the receiver VPN,
             * otherwise it would be routed back into the TUN interface.
             */
            if (!vpnService.protect(socket)) {
                throw IOException(
                    "Unable to protect NetFetch provider connection"
                )
            }

            socket.connect(
                InetSocketAddress(
                    proxyHost,
                    proxyPort
                ),
                CONNECT_TIMEOUT_MS
            )

            socket.soTimeout = SO_TIMEOUT_MS

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            /*
             * SOCKS5 greeting.
             */
            output.write(
                byteArrayOf(
                    VERSION.toByte(),
                    1,
                    AUTH_USERNAME_PASSWORD.toByte()
                )
            )
            output.flush()

            val version = input.read()
            val method = input.read()

            if (version != VERSION) {
                throw IOException(
                    "Invalid SOCKS5 version: $version"
                )
            }

            if (method != AUTH_USERNAME_PASSWORD &&
                method != AUTH_NONE
            ) {
                throw IOException(
                    "Provider rejected SOCKS5 authentication"
                )
            }

            /*
             * Authenticate when the provider requires credentials.
             */
            if (method == AUTH_USERNAME_PASSWORD) {
                val usernameBytes =
                    username.toByteArray(Charsets.UTF_8)

                val passwordBytes =
                    password.toByteArray(Charsets.UTF_8)

                require(usernameBytes.size <= 255) {
                    "SOCKS username is too long"
                }

                require(passwordBytes.size <= 255) {
                    "SOCKS password is too long"
                }

                output.write(
                    byteArrayOf(
                        AUTH_VERSION.toByte(),
                        usernameBytes.size.toByte()
                    )
                )
                output.write(usernameBytes)

                output.write(
                    passwordBytes.size
                )
                output.write(passwordBytes)
                output.flush()

                val authVersion = input.read()
                val authStatus = input.read()

                if (authVersion != AUTH_VERSION ||
                    authStatus != 0
                ) {
                    throw IOException(
                        "NetFetch provider authentication failed"
                    )
                }
            }

            /*
             * SOCKS5 CONNECT request.
             *
             * Use domain addressing so the provider performs DNS resolution
             * on the provider's actual upstream network.
             */
            val hostBytes =
                destinationHost.toByteArray(Charsets.UTF_8)

            require(hostBytes.isNotEmpty()) {
                "Destination host is empty"
            }

            require(hostBytes.size <= 255) {
                "Destination host is too long"
            }

            output.write(
                byteArrayOf(
                    VERSION.toByte(),
                    CONNECT.toByte(),
                    0,
                    3,
                    hostBytes.size.toByte()
                )
            )

            output.write(hostBytes)

            output.write(
                (destinationPort shr 8) and 0xFF
            )
            output.write(
                destinationPort and 0xFF
            )
            output.flush()

            val replyVersion = input.read()
            val replyCode = input.read()

            if (replyVersion != VERSION) {
                throw IOException(
                    "Invalid SOCKS5 reply version"
                )
            }

            if (replyCode != 0) {
                throw IOException(
                    "Provider SOCKS5 CONNECT failed: $replyCode"
                )
            }

            /*
             * Consume the SOCKS5 BND.ADDR/BND.PORT response.
             */
            val reserved = input.read()
            val addressType = input.read()

            if (reserved < 0 || addressType < 0) {
                throw IOException(
                    "Incomplete SOCKS5 response"
                )
            }

            when (addressType) {
                1 -> {
                    readFully(input, 4)
                }

                3 -> {
                    val length = input.read()

                    if (length <= 0) {
                        throw IOException(
                            "Invalid SOCKS5 domain response"
                        )
                    }

                    readFully(input, length)
                }

                4 -> {
                    readFully(input, 16)
                }

                else -> {
                    throw IOException(
                        "Unknown SOCKS5 address type: $addressType"
                    )
                }
            }

            readFully(input, 2)

            /*
             * Once CONNECT succeeds, remove the read timeout so long-lived
             * streams are not disconnected by an idle socket timeout.
             */
            socket.soTimeout = 0

            return socket

        } catch (e: Exception) {
            runCatching {
                socket.close()
            }

            if (e is IOException) {
                throw e
            }

            throw IOException(
                "Unable to connect through NetFetch provider",
                e
            )
        }
    }

    private fun readFully(
        input: java.io.InputStream,
        size: Int
    ): ByteArray {
        val data = ByteArray(size)
        var offset = 0

        while (offset < size) {
            val count =
                input.read(
                    data,
                    offset,
                    size - offset
                )

            if (count < 0) {
                throw IOException(
                    "Unexpected end of SOCKS5 response"
                )
            }

            offset += count
        }

        return data
    }
}
