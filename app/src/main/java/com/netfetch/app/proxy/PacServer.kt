package com.netfetch.app.proxy

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * PAC (Proxy Auto-Configuration) Server (Port 8283)
 *
 * Serves a wpad.dat file via HTTP on the Wi-Fi Direct interface.
 *
 * The PAC script routes all traffic through the NetFetch proxy.
 * There is NO DIRECT fallback — all traffic goes through NetFetch.
 * This is intentional: if a DIRECT fallback were allowed, clients would
 * bypass the proxy for non-proxied connections, which defeats the purpose.
 *
 * PAC URL format: http://<gateway>:8283/wpad.dat
 *                 http://<gateway>:8283/proxy.pac
 *
 * The [proxyHost] should be the detected Wi-Fi Direct gateway address,
 * not a hardcoded value. It is updated dynamically from WifiDirectManager.
 */
class PacServer(
    private val pacPort: Int = 8283,
    private val proxyHost: String = "192.168.49.1",
    private val proxyPort: Int = 8282,
    private val clientAuthorizer: (String) -> Boolean = { true }
) {
    private val TAG = "NetFetchPAC"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientSemaphore = kotlinx.coroutines.sync.Semaphore(20)

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), pacPort), 50)
                }
                Log.i(TAG, "PAC server listening on port $pacPort (proxy=$proxyHost:$proxyPort)")

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        if (!clientSemaphore.tryAcquire()) {
                            runCatching { client.close() }
                            continue
                        }
                        scope.launch {
                            try {
                                handlePacClient(client)
                            } finally {
                                clientSemaphore.release()
                            }
                        }
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
            val clientIp = client.inetAddress?.hostAddress.orEmpty()
            if (!clientAuthorizer(clientIp)) {
                client.getOutputStream().write(
                    "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1)
                )
                return
            }
            client.soTimeout = 5000
            val input = client.getInputStream()
            val out: OutputStream = client.getOutputStream()

            // Consume HTTP request line & headers (we don't need them)
            try {
                val reader = input.bufferedReader(Charsets.ISO_8859_1)
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    line = reader.readLine()
                }
            } catch (_: Exception) {}

            // PAC script: all traffic goes through NetFetch proxy
            // No DIRECT fallback — explicit by design
            val pacScript = """
                function FindProxyForURL(url, host) {
                    /*
                     * NetFetch gateway/local destinations stay local.
                     * Public Internet traffic is always sent through the
                     * NetFetch HTTP proxy.
                     */
                    if (host === "$proxyHost") return "DIRECT";
                    if (isInNet(host, "127.0.0.0", "255.0.0.0")) return "DIRECT";
                    if (isInNet(host, "10.0.0.0", "255.0.0.0")) return "DIRECT";
                    if (isInNet(host, "172.16.0.0", "255.240.0.0")) return "DIRECT";
                    if (isInNet(host, "192.168.0.0", "255.255.0.0")) return "DIRECT";
                    if (isInNet(host, "169.254.0.0", "255.255.0.0")) return "DIRECT";

                    return "PROXY $proxyHost:$proxyPort";
                }
            """.trimIndent()

            val pacBytes = pacScript.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
                    "Content-Length: ${pacBytes.size}\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n\r\n"

            out.write(response.toByteArray(Charsets.ISO_8859_1))
            out.write(pacBytes)
            out.flush()
        } catch (e: Exception) {
            Log.d(TAG, "PAC client handler error: ${e.message}")
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

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
