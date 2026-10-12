package com.netfetch.app.network

import android.net.Network
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fast, Low-Latency Real Internet Validation for an Android Network.
 *
 * Checks reachability without unnecessary TLS negotiation overhead where possible,
 * using HTTP 204 endpoints with aggressive, bounded timeouts.
 */
object ConnectivityTester {

    private const val TAG = "NetFetchConnTest"

    private const val CONNECT_TIMEOUT_MS = 2_500
    private const val READ_TIMEOUT_MS = 2_500

    private val TEST_URLS = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.google.com/generate_204",
        "http://www.msftconnecttest.com/connecttest.txt",
        "https://cp.cloudflare.com/",
        "https://connectivitycheck.gstatic.com/generate_204"
    )

    suspend fun testConnectivity(network: Network?): Boolean =
        withContext(Dispatchers.IO) {
            val startTime = SystemClock.elapsedRealtime()
            var success = false

            try {
                if (network == null) {
                    for (testUrl in TEST_URLS) {
                        if (testHttpDirect(testUrl)) {
                            success = true
                            Log.i(TAG, "Internet validated via default routing by $testUrl")
                            break
                        }
                    }
                } else {
                    for (testUrl in TEST_URLS) {
                        if (testHttp(network, testUrl)) {
                            success = true
                            Log.i(TAG, "Internet validated on network $network by $testUrl")
                            break
                        }
                    }
                }
            } finally {
                val duration = SystemClock.elapsedRealtime() - startTime
                NetfetchDiagnostics.recordReachabilityProbe(duration, success)
            }

            if (!success) {
                Log.w(TAG, "All Internet validation probes failed: $network")
            }
            success
        }

    private fun testHttp(
        network: Network,
        address: String
    ): Boolean {
        var connection: HttpURLConnection? = null

        return try {
            connection =
                network.openConnection(URL(address)) as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doInput = true
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; NetFetch/1.0)")
            }

            val response = connection.responseCode
            val success = response in 200..399 || response == HttpURLConnection.HTTP_NO_CONTENT

            Log.d(
                TAG,
                "Probe $address -> HTTP $response success=$success network=$network"
            )

            success
        } catch (e: Exception) {
            Log.d(
                TAG,
                "Probe failed $address on $network: ${e.message}"
            )
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun testHttpDirect(address: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doInput = true
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; NetFetch/1.0)")
            }
            val response = connection.responseCode
            response in 200..399 || response == HttpURLConnection.HTTP_NO_CONTENT
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
