package com.netfetch.app.proxy

import android.net.Network
import android.util.Log
import com.netfetch.app.model.ClientDevice
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * SOCKS5 Proxy Server (Port 1080)
 *
 * Implements SOCKS5 TCP CONNECT (RFC 1928).
 *
 * UDP ASSOCIATE limitation:
 *   Android does not allow a non-root process to bind a general-purpose UDP
 *   relay socket and receive packets from arbitrary client addresses on a
 *   Wi-Fi Direct interface. UDP ASSOCIATE is therefore not implemented.
 *   Clients that require UDP (e.g. some games) should be informed that
 *   TCP-only SOCKS5 is provided and that UDP traffic will not work.
 *
 * Outbound TCP connections are bound to the selected upstream Network when
 * available, ensuring they route via Wi-Fi/Cellular rather than the
 * Wi-Fi Direct group interface.
 */
class Socks5ProxyServer(
    private val socksPort: Int = 1080,
    private val username: String? = null,
    private val password: String? = null,
    private val sessionValidator: ((String, String) -> Boolean)? = null,
    private val upstreamNetworkProvider: () -> Network? = { null },
    private val onClientActivity: (Map<String, ClientDevice>) -> Unit,
    private val onBandwidthUpdate: (uploadSpeed: Long, downloadSpeed: Long, totalUploaded: Long, totalDownloaded: Long) -> Unit
) {
    private val TAG = "NetFetchSocks5"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private var acceptThread: Thread? = null
    private val socksThreadCounter = AtomicLong(0L)
    private val socksExecutor = java.util.concurrent.ThreadPoolExecutor(
        4,
        128,
        60L,
        java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(),
        { runnable ->
            Thread(runnable, "NetFetch-Socks5-${socksThreadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        },
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    )
    private val proxyDispatcher = socksExecutor.asCoroutineDispatcher()
    private val proxyScope = CoroutineScope(proxyDispatcher + SupervisorJob())

    private val connectedClientsMap = ConcurrentHashMap<String, ClientDevice>()
    private val clientUploadBytes = ConcurrentHashMap<String, AtomicLong>()
    private val clientDownloadBytes = ConcurrentHashMap<String, AtomicLong>()
    private val clientConnectionCounts = ConcurrentHashMap<String, AtomicLong>()
    private val totalUploadCounter = AtomicLong(0L)
    private val totalDownloadCounter = AtomicLong(0L)
    private var speedMonitorJob: Job? = null
    private val clientSemaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_CLIENTS)

    companion object {
        private const val SOCKS5_VERSION = 5
        private const val AUTH_NO_AUTH = 0
        private const val AUTH_USERNAME_PASSWORD = 2
        private const val AUTH_VERSION = 1
        private const val AUTH_SUCCESS = 0
        private const val AUTH_FAILURE = 1

        private const val CMD_CONNECT = 1
        private const val CMD_UDP_ASSOCIATE = 3
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REP_SUCCESS = 0
        private const val REP_GENERAL_FAILURE = 1
        private const val REP_CMD_NOT_SUPPORTED = 7
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private const val TUNNEL_IDLE_TIMEOUT_MS = 60000
        private const val MAX_CONCURRENT_CLIENTS = 1024
        private const val MAX_CONCURRENT_PER_CLIENT = 128
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        startSpeedMonitor()

        acceptThread = Thread({
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    receiveBufferSize = 256 * 1024
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), socksPort), 1024)
                }
                Log.i(TAG, "NetFetch SOCKS5 Server listening on port $socksPort (High-Throughput Stream Routing Active)")

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        if (!clientSemaphore.tryAcquire()) {
                            Log.w(TAG, "SOCKS5 client concurrency limit reached; rejecting connection")
                            runCatching { client.close() }
                            continue
                        }
                        try {
                            proxyScope.launch {
                                try {
                                    handleSocksClient(client)
                                } finally {
                                    clientSemaphore.release()
                                }
                            }
                        } catch (e: java.util.concurrent.RejectedExecutionException) {
                            Log.w(TAG, "SOCKS5 proxy executor saturated; rejecting connection")
                            clientSemaphore.release()
                            runCatching { client.close() }
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting SOCKS5 connection: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "SOCKS5 Proxy Server failed to start: ${e.message}")
            }
        }, "NetFetch-SocksAccept").apply {
            isDaemon = true
            start()
        }
    }

    private suspend fun handleSocksClient(client: Socket) {
        val clientIp = client.inetAddress?.hostAddress ?: "Unknown"

        // Anti-Jam Protection: Prevent a single device from monopolizing SOCKS connection slots
        val currentConns = clientConnectionCounts[clientIp]?.get() ?: 0
        if (currentConns >= MAX_CONCURRENT_PER_CLIENT) {
            Log.w(TAG, "Anti-Jam: client $clientIp exceeded SOCKS limit ($currentConns); connection throttled")
            runCatching { client.close() }
            return
        }

        trackClient(clientIp)
        clientConnectionCounts.computeIfAbsent(clientIp) { AtomicLong() }.incrementAndGet()

        try {
            client.tcpNoDelay = true
            client.sendBufferSize = 256 * 1024
            client.receiveBufferSize = 256 * 1024
            client.soTimeout = READ_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // ── 1. SOCKS5 Greeting ──
            val version = input.read()
            if (version != SOCKS5_VERSION) {
                Log.w(TAG, "Unsupported SOCKS version: $version from $clientIp")
                client.close()
                return
            }

            val nMethods = input.read()
            if (nMethods <= 0) { client.close(); return }
            val methods = ByteArray(nMethods)
            readFully(input, methods)

            val cleanIp = clientIp.removePrefix("::ffff:").substringBefore("%").trim()
            val isLocalHotspotClient = cleanIp.startsWith("192.168.") ||
                    cleanIp.startsWith("10.") ||
                    cleanIp == "127.0.0.1" ||
                    cleanIp == "::1" ||
                    (cleanIp.startsWith("172.") && cleanIp.substringAfter("172.").substringBefore(".").toIntOrNull() in 16..31)

            val clientOffersNoAuth = methods.any { (it.toInt() and 0xFF) == AUTH_NO_AUTH }
            val clientOffersUserPass = methods.any { (it.toInt() and 0xFF) == AUTH_USERNAME_PASSWORD }

            val authenticationRequired =
                !username.isNullOrEmpty() && !password.isNullOrEmpty()

            val selectedMethod = when {
                clientOffersNoAuth && (isLocalHotspotClient || !authenticationRequired) -> AUTH_NO_AUTH
                clientOffersUserPass -> AUTH_USERNAME_PASSWORD
                clientOffersNoAuth -> AUTH_NO_AUTH
                else -> 0xFF
            }

            if (selectedMethod == 0xFF) {
                output.write(
                    byteArrayOf(
                        SOCKS5_VERSION.toByte(),
                        0xFF.toByte()
                    )
                )
                output.flush()
                client.close()
                return
            }

            output.write(
                byteArrayOf(
                    SOCKS5_VERSION.toByte(),
                    selectedMethod.toByte()
                )
            )
            output.flush()

            if (selectedMethod == AUTH_USERNAME_PASSWORD) {

                val authVersion = input.read()

                if (authVersion != AUTH_VERSION) {
                    client.close()
                    return
                }

                val usernameLength = input.read()

                if (usernameLength <= 0 || usernameLength > 255) {
                    client.close()
                    return
                }

                val usernameBytes = ByteArray(usernameLength)
                readFully(input, usernameBytes)

                val passwordLength = input.read()

                if (passwordLength <= 0 || passwordLength > 255) {
                    client.close()
                    return
                }

                val passwordBytes = ByteArray(passwordLength)
                readFully(input, passwordBytes)

                val suppliedUsername =
                    String(usernameBytes, Charsets.UTF_8)

                val suppliedPassword =
                    String(passwordBytes, Charsets.UTF_8)

                val authenticated =
                    if (
                        suppliedUsername == "netfetch-session" &&
                        sessionValidator != null
                    ) {
                        sessionValidator.invoke(suppliedPassword, clientIp)
                    } else {
                        suppliedUsername == username &&
                            suppliedPassword == password
                    }

                output.write(
                    byteArrayOf(
                        AUTH_VERSION.toByte(),
                        if (authenticated) {
                            AUTH_SUCCESS.toByte()
                        } else {
                            AUTH_FAILURE.toByte()
                        }
                    )
                )
                output.flush()

                if (!authenticated) {
                    Log.w(
                        TAG,
                        "SOCKS5 authentication failed from $clientIp"
                    )
                    client.close()
                    return
                }
            }

            // ── 2. SOCKS5 Request ──
            val reqVer = input.read()
            if (reqVer != SOCKS5_VERSION) { client.close(); return }

            val cmd = input.read()
            input.read() // RSV (reserved, ignore)
            val atyp = input.read()

            var targetHost = ""
            when (atyp) {
                ATYP_IPV4 -> {
                    val ipv4 = ByteArray(4)
                    readFully(input, ipv4)
                    targetHost = InetAddress.getByAddress(ipv4).hostAddress ?: ""
                }
                ATYP_DOMAIN -> {
                    val domainLen = input.read()
                    if (domainLen <= 0) { client.close(); return }
                    val domainBytes = ByteArray(domainLen)
                    readFully(input, domainBytes)
                    targetHost = String(domainBytes, Charsets.UTF_8)
                }
                ATYP_IPV6 -> {
                    val ipv6 = ByteArray(16)
                    readFully(input, ipv6)
                    targetHost = InetAddress.getByAddress(ipv6).hostAddress ?: ""
                }
                else -> {
                    sendReply(output, REP_GENERAL_FAILURE, ATYP_IPV4, ByteArray(4), 0)
                    client.close()
                    return
                }
            }

            val portHigh = input.read()
            val portLow = input.read()
            val targetPort = ((portHigh and 0xFF) shl 8) or (portLow and 0xFF)

            when (cmd) {
                CMD_CONNECT -> tunnelTcp(clientIp, client, input, output, targetHost, targetPort)
                CMD_UDP_ASSOCIATE -> handleUdpAssociate(clientIp, client, input, output)
                else -> {
                    Log.w(TAG, "Unknown SOCKS5 command: $cmd from $clientIp")
                    sendReply(output, REP_CMD_NOT_SUPPORTED, ATYP_IPV4, ByteArray(4), 0)
                    client.close()
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "SOCKS5 client finished ($clientIp): ${e.message}")
            try { client.close() } catch (_: Exception) {}
        } finally {
            val remaining = clientConnectionCounts[clientIp]?.decrementAndGet() ?: 0
            if (remaining <= 0) {
                clientConnectionCounts.remove(clientIp)
                connectedClientsMap.remove(clientIp)
                clientUploadBytes.remove(clientIp)
                clientDownloadBytes.remove(clientIp)
                com.netfetch.app.network.NetfetchDiagnostics.recordDisconnect("SOCKS5 client $clientIp disconnected")
            }
        }
    }

    private fun sendReply(
        output: OutputStream,
        rep: Int,
        atyp: Int,
        bindAddr: ByteArray,
        bindPort: Int
    ) {
        try {
            val reply = ByteArray(4 + bindAddr.size + 2)
            reply[0] = SOCKS5_VERSION.toByte()
            reply[1] = rep.toByte()
            reply[2] = 0 // RSV
            reply[3] = atyp.toByte()
            System.arraycopy(bindAddr, 0, reply, 4, bindAddr.size)
            reply[reply.size - 2] = (bindPort shr 8).toByte()
            reply[reply.size - 1] = bindPort.toByte()
            output.write(reply)
            output.flush()
        } catch (_: Exception) {}
    }

    private fun openUpstreamSocket(host: String, port: Int): Socket {
        val upstream = upstreamNetworkProvider()
            ?: com.netfetch.app.network.NetfetchUpstreamRuntime.fallbackNetwork()

        /*
         * Resolve DNS through NetFetch high-performance DNS cache & engine
         */
        val addresses = try {
            com.netfetch.app.network.NetfetchDnsEngine.resolve(host, upstream)
        } catch (_: Exception) {
            try {
                upstream?.getAllByName(host)?.toList()?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            } ?: try {
                java.net.InetAddress.getAllByName(host).toList().takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            } ?: throw java.net.UnknownHostException("No address found for $host")
        }

        var lastError: Exception? = null

        // Attempt 1: Try via selected upstream network
        for (address in addresses) {
            var socket: Socket? = null

            try {
                socket = if (upstream != null) {
                    try {
                        upstream.socketFactory.createSocket()
                    } catch (_: Exception) {
                        Socket()
                    }
                } else {
                    Socket()
                }

                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.sendBufferSize = 256 * 1024
                socket.receiveBufferSize = 256 * 1024

                if (upstream != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    runCatching { upstream.bindSocket(socket) }
                }

                com.netfetch.app.network.NetfetchUpstreamRuntime.protect(socket)

                val connectStart = android.os.SystemClock.elapsedRealtime()
                socket.connect(
                    InetSocketAddress(address, port),
                    CONNECT_TIMEOUT_MS
                )
                val elapsed = android.os.SystemClock.elapsedRealtime() - connectStart
                com.netfetch.app.network.NetfetchDiagnostics.recordTcpConnect(elapsed)

                Log.d(
                    TAG,
                    "Connected through upstream: $host/$address:$port in ${elapsed}ms"
                )

                return socket
            } catch (e: Exception) {
                lastError = e
                runCatching { socket?.close() }
                Log.d(
                    TAG,
                    "SOCKS upstream address failed $address:$port: ${e.message}"
                )
            }
        }

        // Attempt 2: If upstream socket failed, fall back to direct default routing
        for (address in addresses) {
            var directSocket: Socket? = null
            try {
                directSocket = Socket()
                directSocket.tcpNoDelay = true
                directSocket.keepAlive = true
                directSocket.sendBufferSize = 256 * 1024
                directSocket.receiveBufferSize = 256 * 1024
                com.netfetch.app.network.NetfetchUpstreamRuntime.protect(directSocket)
                directSocket.connect(
                    InetSocketAddress(address, port),
                    CONNECT_TIMEOUT_MS
                )
                Log.d(
                    TAG,
                    "SOCKS connected via direct default routing fallback: $host/$address:$port"
                )
                return directSocket
            } catch (e: Exception) {
                lastError = e
                runCatching { directSocket?.close() }
            }
        }

        throw java.io.IOException(
            "Could not connect to $host:$port",
            lastError
        )
    }

    private suspend fun tunnelTcp(
        clientIp: String,
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        host: String,
        port: Int
    ) {
        try {
            val targetSocket = openUpstreamSocket(host, port)
            // Set idle inactivity timeout: 60s of silence cleanly reclaims connection slots
            targetSocket.soTimeout = TUNNEL_IDLE_TIMEOUT_MS
            clientSocket.soTimeout = TUNNEL_IDLE_TIMEOUT_MS
            targetSocket.tcpNoDelay = true
            clientSocket.tcpNoDelay = true
            targetSocket.keepAlive = true
            clientSocket.keepAlive = true

            // Build success reply using the actual upstream address family.
            val localAddress = targetSocket.localAddress
            val localAddr = localAddress.address
            val replyAtyp = when (localAddress) {
                is Inet6Address -> ATYP_IPV6
                else -> ATYP_IPV4
            }

            val localPort = targetSocket.localPort

            sendReply(
                clientOut,
                REP_SUCCESS,
                replyAtyp,
                localAddr,
                localPort
            )

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            val job1 = proxyScope.launch {
                pipeStreams(
                    clientIp,
                    clientIn,
                    targetOut,
                    isUpload = true
                )
            }

            val job2 = proxyScope.launch {
                pipeStreams(
                    clientIp,
                    targetIn,
                    clientOut,
                    isUpload = false
                )
            }

            try {
                // Wait until either direction finishes.
                kotlinx.coroutines.selects.select<Unit> {
                    job1.onJoin { }
                    job2.onJoin { }
                }
            } finally {
                job1.cancel()
                job2.cancel()

                try {
                    clientSocket.close()
                } catch (_: Exception) {
                }

                try {
                    targetSocket.close()
                } catch (_: Exception) {
                }

                try {
                    job1.join()
                } catch (_: Exception) {
                }

                try {
                    job2.join()
                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "SOCKS5 CONNECT failed to $host:$port: ${e.message}")
            sendReply(clientOut, REP_GENERAL_FAILURE, ATYP_IPV4, ByteArray(4), 0)
        }
    }

    private suspend fun handleUdpAssociate(
        clientIp: String,
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream
    ) {
        // Android does not allow a non-root process to bind a UDP relay socket
        // on the Wi-Fi Direct group interface and receive datagrams from arbitrary
        // peer addresses. Real UDP ASSOCIATE is therefore not implemented here.
        // Return REP_CMD_NOT_SUPPORTED so clients fail fast rather than silently
        // dropping packets through a fake success path.
        Log.d(TAG, "SOCKS5 UDP ASSOCIATE rejected (not supported): $clientIp")
        sendReply(clientOut, REP_CMD_NOT_SUPPORTED, ATYP_IPV4, ByteArray(4), 0)
        runCatching { clientSocket.close() }
    }

    private fun pipeStreams(clientIp: String, input: InputStream, output: OutputStream, isUpload: Boolean) {
        com.netfetch.app.network.NetfetchBufferPool.useBuffer { buffer ->
            try {
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    if (input.available() == 0) {
                        output.flush()
                    }
                    val amount = read.toLong()

                    if (isUpload) {
                        totalUploadCounter.addAndGet(amount)
                        clientUploadBytes[clientIp]?.addAndGet(amount)
                    } else {
                        totalDownloadCounter.addAndGet(amount)
                        clientDownloadBytes[clientIp]?.addAndGet(amount)
                    }
                }
                output.flush()
            } catch (_: Exception) {}
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val read = input.read(buf, offset, buf.size - offset)
            if (read < 0) throw java.io.EOFException("Stream ended unexpectedly")
            offset += read
        }
    }

    private fun trackClient(ip: String) {
        val now = System.currentTimeMillis()
        clientUploadBytes.putIfAbsent(ip, AtomicLong(0L))
        clientDownloadBytes.putIfAbsent(ip, AtomicLong(0L))
        connectedClientsMap.computeIfAbsent(ip) {
            ClientDevice(ipAddress = ip, deviceName = "SOCKS5 Client ($ip)", connectedTimestamp = now)
        }
    }

    private fun startSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = proxyScope.launch {
            var lastUp = totalUploadCounter.get()
            var lastDown = totalDownloadCounter.get()

            while (isRunning) {
                delay(1000)
                val currUp = totalUploadCounter.get()
                val currDown = totalDownloadCounter.get()

                val upSpeed = currUp - lastUp
                val downSpeed = currDown - lastDown

                lastUp = currUp
                lastDown = currDown

                var clientsChanged = false
                for ((ip, client) in connectedClientsMap) {
                    val up = clientUploadBytes[ip]?.get() ?: 0L
                    val down = clientDownloadBytes[ip]?.get() ?: 0L
                    if (client.bytesUploaded != up || client.bytesDownloaded != down) {
                        connectedClientsMap[ip] = client.copy(
                            bytesUploaded = up,
                            bytesDownloaded = down
                        )
                        clientsChanged = true
                    }
                }

                onBandwidthUpdate(upSpeed, downSpeed, currUp, currDown)
                if (clientsChanged) {
                    onClientActivity(HashMap(connectedClientsMap))
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        speedMonitorJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        acceptThread = null
        proxyScope.cancel()
        runCatching { socksExecutor.shutdownNow() }
        connectedClientsMap.clear()
        clientConnectionCounts.clear()
        clientUploadBytes.clear()
        clientDownloadBytes.clear()
    }
}
