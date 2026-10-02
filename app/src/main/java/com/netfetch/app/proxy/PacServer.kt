package com.netfetch.app.proxy

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class PacServer(
    private val pacPort: Int = 8283,
    private val proxyHost: String = "192.168.49.1",
    private val proxyPort: Int = 8282
) {
    private val TAG = "NetFetchPAC"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(pacPort, 50, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "PAC Auto-Config Server listening on port $pacPort")

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        scope.launch { handlePacClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "PAC client accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PAC Server failed to start: ${e.message}")
            }
        }
    }

    private fun handlePacClient(client: Socket) {
        try {
            val out: OutputStream = client.getOutputStream()
            val pacScript = """
                function FindProxyForURL(url, host) {
                    return "PROXY $proxyHost:$proxyPort; DIRECT";
                }
            """.trimIndent()

            val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
                    "Content-Length: ${pacScript.length}\r\n" +
                    "Connection: close\r\n\r\n" +
                    pacScript

            out.write(response.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
    }
}
