package com.netfetch.app.proxy

import android.util.Log
import com.netfetch.app.model.ClientDevice
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Socks5ProxyServer(
    private val socksPort: Int = 1080,
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

    fun start() {
        if (isRunning) return
        isRunning = true

        proxyScope.launch {
            try {
                serverSocket = ServerSocket(socksPort, 100, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "NetFetch Pro Mode SOCKS5 Server listening on port $socksPort")

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
            client.soTimeout = 30000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // 1. SOCKS5 Greeting / Handshake
            val version = input.read()
            if (version != 5) {
                client.close()
                return
            }

            val nMethods = input.read()
            if (nMethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nMethods)
            input.read(methods)

            // NO AUTHENTICATION REQUIRED (0x00)
            output.write(byteArrayOf(5, 0))
            output.flush()

            // 2. SOCKS5 Request
            val reqVer = input.read()
            val cmd = input.read() // 1 = CONNECT, 3 = UDP ASSOCIATE
            val rsv = input.read()
            val atyp = input.read()

            if (reqVer != 5) {
                client.close()
                return
            }

            var targetHost = ""
            var targetPort = 0

            when (atyp) {
                1 -> { // IPv4 (4 bytes)
                    val ipv4 = ByteArray(4)
                    input.read(ipv4)
                    targetHost = InetAddress.getByAddress(ipv4).hostAddress ?: ""
                }
                3 -> { // Domain Name
                    val domainLen = input.read()
                    if (domainLen <= 0) {
                        client.close()
                        return
                    }
                    val domainBytes = ByteArray(domainLen)
                    input.read(domainBytes)
                    targetHost = String(domainBytes, Charsets.UTF_8)
                }
                4 -> { // IPv6 (16 bytes)
                    val ipv6 = ByteArray(16)
                    input.read(ipv6)
                    targetHost = InetAddress.getByAddress(ipv6).hostAddress ?: ""
                }
                else -> {
                    client.close()
                    return
                }
            }

            val portBuf = ByteArray(2)
            input.read(portBuf)
            targetPort = ((portBuf[0].toInt() and 0xFF) shl 8) or (portBuf[1].toInt() and 0xFF)

            if (cmd == 1) { // CONNECT
                tunnelTcp(clientIp, client, input, output, targetHost, targetPort)
            } else {
                // Command not supported response (0x07)
                output.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))
                output.flush()
                client.close()
            }

        } catch (e: Exception) {
            Log.d(TAG, "SOCKS5 client socket finished: ${e.message}")
            try { client.close() } catch (_: Exception) {}
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
            val targetSocket = Socket(host, port)
            targetSocket.soTimeout = 30000

            // Send SOCKS5 Success Response (0x00)
            val localAddr = targetSocket.localAddress.address
            val resp = ByteArray(6 + localAddr.size)
            resp[0] = 5 // Version
            resp[1] = 0 // Success
            resp[2] = 0 // Reserved
            resp[3] = if (localAddr.size == 4) 1 else 4 // ATYP
            System.arraycopy(localAddr, 0, resp, 4, localAddr.size)
            val p = targetSocket.localPort
            resp[resp.size - 2] = (p shr 8).toByte()
            resp[resp.size - 1] = p.toByte()

            clientOut.write(resp)
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
            Log.d(TAG, "SOCKS5 CONNECT failed to $host:$port: ${e.message}")
            try {
                // Connection refused (0x05)
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
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
                if (isUpload) totalUploadCounter.addAndGet(read.toLong())
                else totalDownloadCounter.addAndGet(read.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun trackClient(ip: String) {
        val now = System.currentTimeMillis()
        connectedClientsMap.computeIfAbsent(ip) {
            ClientDevice(ipAddress = ip, deviceName = "Pro Client ($ip)", connectedTimestamp = now)
        }
        onClientActivity(HashMap(connectedClientsMap))
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        proxyScope.cancel()
    }
}
