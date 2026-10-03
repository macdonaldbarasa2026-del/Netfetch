package com.netfetch.app.proxy

import android.net.Network
import android.util.Log
import com.netfetch.app.model.ClientDevice
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP/HTTPS Proxy Server (Port 8282)
 *
 * Handles both plain HTTP requests and HTTPS CONNECT tunnelling.
 * Outbound connections are bound to the upstream Network object when
 * provided, ensuring traffic routes via Wi-Fi/Mobile-Data rather than
 * the Wi-Fi Direct interface.
 *
 * Architecture note: This is a proxy, not a transparent NAT gateway.
 * Client devices must configure their HTTP proxy to point at this server.
 * Root is not required.
 */
class HttpProxyServer(
    private val port: Int = 8282,
    private val upstreamNetworkProvider: () -> Network? = { null },
    private val onClientActivity: (Map<String, ClientDevice>) -> Unit,
    private val onBandwidthUpdate: (uploadSpeed: Long, downloadSpeed: Long, totalBytes: Long) -> Unit
) {
    private val TAG = "NetFetchProxy"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connectedClientsMap = ConcurrentHashMap<String, ClientDevice>()
    private val clientUploadBytes = ConcurrentHashMap<String, AtomicLong>()
    private val clientDownloadBytes = ConcurrentHashMap<String, AtomicLong>()

    private val totalUploadCounter = AtomicLong(0L)
    private val totalDownloadCounter = AtomicLong(0L)

    private var speedMonitorJob: Job? = null

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private const val MAX_CONCURRENT_CLIENTS = 50
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        proxyScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 100)
                }
                Log.i(TAG, "NetFetch HTTP Proxy listening on port $port")

                startSpeedMonitor()

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        proxyScope.launch {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal proxy server startup error: ${e.message}", e)
            }
        }
    }

    private fun handleClientSocket(clientSocket: Socket) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "Unknown"
        trackClientConnection(clientIp)

        try {
            clientSocket.soTimeout = READ_TIMEOUT_MS
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerLines = mutableListOf<String>()
            val reader = clientIn.bufferedReader(Charsets.ISO_8859_1)
            var firstLine: String?

            try {
                firstLine = reader.readLine()
            } catch (e: Exception) {
                clientSocket.close()
                return
            }

            if (firstLine.isNullOrEmpty()) {
                clientSocket.close()
                return
            }

            headerLines.add(firstLine)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                headerLines.add(line!!)
            }

            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0]
            val target = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                val colonIdx = target.lastIndexOf(':')
                val host = if (colonIdx > 0) target.substring(0, colonIdx) else target
                val targetPort = if (colonIdx > 0) target.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
                tunnelHttps(clientIp, clientSocket, clientIn, clientOut, host, targetPort)
            } else {
                tunnelHttp(clientIp, clientSocket, clientIn, clientOut, method, target, headerLines)
            }

        } catch (e: Exception) {
            Log.d(TAG, "Client socket handler finished ($clientIp): ${e.message}")
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun openUpstreamSocket(host: String, port: Int): Socket {
        val upstream = upstreamNetworkProvider()
            ?: throw java.io.IOException("No validated upstream internet network is available")

        return try {
            val socket = upstream.socketFactory.createSocket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket
        } catch (e: Exception) {
            Log.w(TAG, "Selected upstream network connection failed for $host:$port: ${e.message}")
            throw java.io.IOException(
                "Selected upstream network could not connect to $host:$port",
                e
            )
        }
    }

    private fun tunnelHttps(
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

            val okResponse = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: NetFetch/1.0\r\n\r\n"
            clientOut.write(okResponse.toByteArray(Charsets.ISO_8859_1))
            clientOut.flush()

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
            Log.d(TAG, "HTTPS CONNECT tunnel error for $host:$port -> ${e.message}")
            try {
                val errResponse = "HTTP/1.1 502 Bad Gateway\r\nProxy-Agent: NetFetch/1.0\r\n\r\n"
                clientOut.write(errResponse.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()
            } catch (_: Exception) {}
        }
    }

    private fun tunnelHttp(
        clientIp: String,
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        method: String,
        targetUrl: String,
        headerLines: List<String>
    ) {
        try {
            var host = ""
            var port = 80

            // Extract host from Host header first
            for (h in headerLines) {
                if (h.startsWith("Host:", ignoreCase = true)) {
                    val hostVal = h.substring(5).trim()
                    if (hostVal.contains(":")) {
                        val hp = hostVal.split(":")
                        host = hp[0]
                        port = hp[1].toIntOrNull() ?: 80
                    } else {
                        host = hostVal
                    }
                    break
                }
            }

            // Fall back to parsing the URL
            if (host.isEmpty()) {
                val cleanUrl = if (targetUrl.startsWith("http://")) targetUrl.substring(7) else targetUrl
                val slashIdx = cleanUrl.indexOf("/")
                val hostPort = if (slashIdx != -1) cleanUrl.substring(0, slashIdx) else cleanUrl
                if (hostPort.contains(":")) {
                    val hp = hostPort.split(":")
                    host = hp[0]
                    port = hp[1].toIntOrNull() ?: 80
                } else {
                    host = hostPort
                }
            }

            if (host.isEmpty()) {
                clientSocket.close()
                return
            }

            val targetSocket = openUpstreamSocket(host, port)
            targetSocket.soTimeout = READ_TIMEOUT_MS

            val targetOut = targetSocket.getOutputStream()
            val targetIn = targetSocket.getInputStream()

            // Rewrite absolute URL to relative path
            val path = if (targetUrl.startsWith("http://")) {
                val afterProto = targetUrl.substring(7)
                val slashIdx = afterProto.indexOf("/")
                if (slashIdx != -1) afterProto.substring(slashIdx) else "/"
            } else {
                targetUrl
            }

            // Reconstruct HTTP/1.1 request (strip Proxy-* headers)
            val requestVersion = if (headerLines.isNotEmpty() && headerLines[0].endsWith("HTTP/1.0")) "HTTP/1.0" else "HTTP/1.1"
            val rewrittenFirstLine = "$method $path $requestVersion\r\n"
            targetOut.write(rewrittenFirstLine.toByteArray(Charsets.ISO_8859_1))
            recordBytes(clientIp, rewrittenFirstLine.length.toLong(), isUpload = true)

            for (i in 1 until headerLines.size) {
                val lineStr = headerLines[i]
                if (!lineStr.startsWith("Proxy-Connection", ignoreCase = true) &&
                    !lineStr.startsWith("Proxy-Authorization", ignoreCase = true)) {
                    val lineBytes = (lineStr + "\r\n").toByteArray(Charsets.ISO_8859_1)
                    targetOut.write(lineBytes)
                    recordBytes(clientIp, lineBytes.size.toLong(), isUpload = true)
                }
            }
            targetOut.write("\r\n".toByteArray(Charsets.ISO_8859_1))
            targetOut.flush()

            val job1 = proxyScope.launch { pipeStreams(clientIp, clientIn, targetOut, isUpload = true) }
            val job2 = proxyScope.launch { pipeStreams(clientIp, targetIn, clientOut, isUpload = false) }

            runBlocking {
                job1.join()
                job2.join()
            }

            try { targetSocket.close() } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.d(TAG, "HTTP tunnel error -> ${e.message}")
            try {
                val errResponse = "HTTP/1.1 502 Bad Gateway\r\nProxy-Agent: NetFetch/1.0\r\n\r\n"
                clientOut.write(errResponse.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()
            } catch (_: Exception) {}
        }
    }

    private fun pipeStreams(clientIp: String, input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                recordBytes(clientIp, read.toLong(), isUpload)
            }
        } catch (_: Exception) {
            // Connection closed or reset — normal termination
        }
    }

    private fun trackClientConnection(clientIp: String) {
        val now = System.currentTimeMillis()
        clientUploadBytes.putIfAbsent(clientIp, AtomicLong(0L))
        clientDownloadBytes.putIfAbsent(clientIp, AtomicLong(0L))

        connectedClientsMap.compute(clientIp) { _, existing ->
            existing?.copy(connectedTimestamp = existing.connectedTimestamp)
                ?: ClientDevice(
                    ipAddress = clientIp,
                    deviceName = resolveDeviceName(clientIp),
                    connectedTimestamp = now
                )
        }
        notifyClientsChanged()
    }

    private fun recordBytes(clientIp: String, count: Long, isUpload: Boolean) {
        if (count <= 0) return
        if (isUpload) {
            totalUploadCounter.addAndGet(count)
            clientUploadBytes[clientIp]?.addAndGet(count)
        } else {
            totalDownloadCounter.addAndGet(count)
            clientDownloadBytes[clientIp]?.addAndGet(count)
        }
        updateClientStats(clientIp)
    }

    private fun updateClientStats(clientIp: String) {
        val up = clientUploadBytes[clientIp]?.get() ?: 0L
        val down = clientDownloadBytes[clientIp]?.get() ?: 0L

        connectedClientsMap.computeIfPresent(clientIp) { _, existing ->
            existing.copy(bytesUploaded = up, bytesDownloaded = down)
        }
    }

    private fun resolveDeviceName(ip: String): String {
        return when {
            ip.startsWith("192.168.49.") -> "Client (${ip.substringAfterLast('.')})"
            ip.startsWith("192.168.") -> "Client ($ip)"
            else -> "Client ($ip)"
        }
    }

    private fun notifyClientsChanged() {
        onClientActivity(HashMap(connectedClientsMap))
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

                onBandwidthUpdate(upSpeed, downSpeed, currUp + currDown)
                notifyClientsChanged()
            }
        }
    }

    fun stop() {
        isRunning = false
        speedMonitorJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        proxyScope.cancel()
    }
}
