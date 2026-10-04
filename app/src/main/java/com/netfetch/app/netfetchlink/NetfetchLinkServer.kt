package com.netfetch.app.netfetchlink

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provider-side session handshake server (port 8290).
 *
 * Unified for Normal and Pro modes.
 *
 * Protocol:
 *   Client → NETFETCH/1 HELLO
 *   Server → NETFETCH/1 OK
 *   Server → SESSION <token>
 *   Server → MODE <NORMAL|PRO>
 *   Server → HTTP_PORT <port>
 *   Server → PAC_PORT <port>
 *   Server → SOCKS_PORT <port>   (Pro only)
 *   Server → END
 *
 *   Client → NETFETCH/1 REFRESH <token>
 *   Server → NETFETCH/1 REFRESHED
 *
 * Threads: incoming connections are handed off to a bounded thread pool
 * so a slow client cannot stall other connections.
 */
class NetfetchLinkServer(
    private val mode: String,
    private val socksPort: Int = NetfetchLinkProtocol.DEFAULT_SOCKS_PORT,
    private val httpPort: Int = NetfetchLinkProtocol.DEFAULT_HTTP_PORT,
    private val pacPort: Int = NetfetchLinkProtocol.DEFAULT_PAC_PORT
) {
    companion object {
        private const val TAG = "NetFetchLinkServer"
        const val PORT = 8290
        private const val TOKEN_BYTES = 32
        private const val TOKEN_TTL_MS = 60_000L

        /** Maximum simultaneously active link-handshake threads. */
        private const val HANDLER_THREADS = 8
    }

    private data class Session(
        val token: String,
        val clientAddress: String,
        @Volatile var expiresAt: Long
    )

    private val running = AtomicBoolean(false)
    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, Session>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    private var listenerThread: Thread? = null

    /**
     * Bounded thread pool for client handlers.
     * Prevents unbounded thread creation when many receivers connect simultaneously.
     */
    private val handlerPool = Executors.newFixedThreadPool(HANDLER_THREADS) { r ->
        Thread(r, "NetFetch-LinkHandler").apply { isDaemon = true }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        listenerThread = Thread(
            { runServer() },
            "NetFetch-LinkServer"
        ).also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun runServer() {
        try {
            ServerSocket(PORT).use { server ->
                serverSocket = server

                while (running.get()) {
                    cleanupExpiredSessions()

                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.w(TAG, "Accept failed", e)
                        }
                        continue
                    }

                    if (running.get()) {
                        handlerPool.execute { handle(socket) }
                    } else {
                        runCatching { socket.close() }
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                Log.e(TAG, "Link server failed", e)
            }
        } finally {
            serverSocket = null
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            try {
                it.soTimeout = 5_000

                val reader = BufferedReader(
                    InputStreamReader(it.getInputStream(), Charsets.UTF_8)
                )

                val writer = PrintWriter(
                    it.getOutputStream(),
                    true
                )

                val firstLine = reader.readLine()
                    ?: run {
                        writer.println("NETFETCH/1 ERROR Empty request")
                        return
                    }

                if (firstLine.startsWith("NETFETCH/1 REFRESH ")) {
                    val token = firstLine
                        .removePrefix("NETFETCH/1 REFRESH ")
                        .trim()

                    if (validateSession(token, it.inetAddress.hostAddress.orEmpty())) {
                        writer.println("NETFETCH/1 REFRESHED")
                    } else {
                        writer.println("NETFETCH/1 ERROR Invalid or expired session")
                    }

                    return
                }

                if (firstLine != "NETFETCH/1 HELLO") {
                    writer.println("NETFETCH/1 ERROR Invalid request")
                    return
                }

                val token = createToken(it.inetAddress.hostAddress.orEmpty())

                writer.println("NETFETCH/1 OK")
                writer.println("SESSION $token")
                writer.println("MODE $mode")
                writer.println("HTTP_PORT $httpPort")
                writer.println("PAC_PORT $pacPort")

                // Only send SOCKS_PORT in Pro mode.
                if (mode == NetfetchLinkProtocol.MODE_PRO) {
                    writer.println("SOCKS_PORT $socksPort")
                }

                writer.println("END")
            } catch (e: Exception) {
                Log.w(TAG, "Link client handler failed", e)
            }
        }
    }

    private fun createToken(clientAddress: String): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)

        val token = bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

        sessions[token] = Session(
            token = token,
            clientAddress = clientAddress,
            expiresAt = System.currentTimeMillis() + TOKEN_TTL_MS
        )

        return token
    }

    fun validateSession(token: String, clientAddress: String? = null): Boolean {
        val session = sessions[token]
            ?: return false

        val now = System.currentTimeMillis()

        if (now > session.expiresAt) {
            sessions.remove(token, session)
            return false
        }

        // A link token is issued after the Wi-Fi Direct peer has connected.
        // Binding it to that peer prevents a token observed on the local link
        // from being replayed by a different downstream device.
        if (clientAddress != null && clientAddress != session.clientAddress) {
            return false
        }

        // Sliding expiry: active receivers keep their session alive.
        session.expiresAt = now + TOKEN_TTL_MS

        return true
    }

    /** Authorizes Normal HTTP/PAC traffic after a successful link handshake. */
    fun isClientAuthorized(clientAddress: String): Boolean {
        if (clientAddress.isBlank()) return false
        val now = System.currentTimeMillis()
        return sessions.values.any { session ->
            session.clientAddress == clientAddress && now <= session.expiresAt
        }
    }

    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now > it.value.expiresAt }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        sessions.clear()

        runCatching {
            serverSocket?.close()
        }

        serverSocket = null

        listenerThread?.interrupt()
        listenerThread = null

        handlerPool.shutdownNow()
    }
}
