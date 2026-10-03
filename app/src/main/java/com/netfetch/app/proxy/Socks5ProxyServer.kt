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
    private val upstreamNetworkProvider: () -> Network? = { null },
    private val onClientActivity: (Map<String, ClientDevice>) -> Unit,
    private val onBandwidthUpdate: (uploadSpeed: Long, downloadSpeed: Long, totalBytes: Long) -> Unit
) {
    private val TAG = "NetFetchSocks5"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connectedClientsMap = ConcurrentHashMap<String, ClientDevice>()
    private val totalUploadCounter = AtomicLong(0L)
    private val totalDownloadCounter = AtomicLong(0L)

    companion object {
        private const val SOCKS5_VERSION = 5
        private const val AUTH_NO_AUTH = 0
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
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        proxyScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), socksPort), 100)
                }
                Log.i(TAG, "NetFetch SOCKS5 Server listening on port $socksPort")

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        proxyScope.launch { handleSocksClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting SOCKS5 connection: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS5 Proxy Server failed to start: ${e.message}")
            }
        }
    }

    private fun handleSocksClient(client: Socket) {
        val clientIp = client.inetAddress?.hostAddress ?: "Unknown"
        trackClient(clientIp)

        try {
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

            // We always use NO AUTH (0x00)
            output.write(byteArrayOf(SOCKS5_VERSION.toByte(), AUTH_NO_AUTH.toByte()))
            output.flush()

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
                CMD_UDP_ASSOCIATE -> {
                    // UDP ASSOCIATE not supported (see class-level doc)
                    Log.w(TAG, "UDP ASSOCIATE requested by $clientIp — not supported")
                    sendReply(output, REP_CMD_NOT_SUPPORTED, ATYP_IPV4, ByteArray(4), 0)
                    client.close()
                }
                else -> {
                    Log.w(TAG, "Unknown SOCKS5 command: $cmd from $clientIp")
                    sendReply(output, REP_CMD_NOT_SUPPORTED, ATYP_IPV4, ByteArray(4), 0)
                    client.close()
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "SOCKS5 client finished ($clientIp): ${e.message}")
            try { client.close() } catch (_: Exception) {}
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
        return if (upstream != null) {
            try {
                val socket = upstream.socketFactory.createSocket()
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket
            } catch (e: Exception) {
                Log.w(TAG, "SOCKS5 upstream network socket failed, falling back: ${e.message}")
                Socket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
            }
        } else {
            Socket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        }
    }

    private fun tunnelTcp(
        clientIp: String,
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        host: String,
        port: Int
    ) {
        try {
            val targetSocket = openUpstreamSocket(host, port)
            targetSocket.soTimeout = READ_TIMEOUT_MS

            // Build success reply
            val localAddr = (targetSocket.localAddress as? Inet4Address)?.address ?: ByteArray(4)
            val localPort = targetSocket.localPort
            sendReply(clientOut, REP_SUCCESS, ATYP_IPV4, localAddr, localPort)

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            val job1 = proxyScope.launch { pipeStreams(clientIp, clientIn, targetOut, isUpload = true) }
            val job2 = proxyScope.launch { pipeStreams(clientIp, targetIn, clientOut, isUpload = false) }

            runBlocking {
                job1.join()
                job2.join()
            }

            try { targetSocket.close() } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.d(TAG, "SOCKS5 CONNECT failed to $host:$port: ${e.message}")
            sendReply(clientOut, REP_GENERAL_FAILURE, ATYP_IPV4, ByteArray(4), 0)
        }
    }

    private fun pipeStreams(clientIp: String, input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                if (isUpload) totalUploadCounter.addAndGet(read.toLong())
                else totalDownloadCounter.addAndGet(read.toLong())
            }
        } catch (_: Exception) {}
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
        connectedClientsMap.computeIfAbsent(ip) {
            ClientDevice(ipAddress = ip, deviceName = "SOCKS5 Client ($ip)", connectedTimestamp = now)
        }
        onClientActivity(HashMap(connectedClientsMap))
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        proxyScope.cancel()
    }
}
