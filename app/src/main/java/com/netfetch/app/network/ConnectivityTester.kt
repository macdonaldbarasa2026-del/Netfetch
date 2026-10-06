package com.netfetch.app.network

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real Internet validation for a specific Android Network.
 *
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
object ConnectivityTester {

    private const val TAG = "NetFetchConnTest"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    private val TEST_URLS = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://cp.cloudflare.com/",
        "http://www.msftconnecttest.com/connecttest.txt"
    )

    suspend fun testConnectivity(network: Network?): Boolean =
        withContext(Dispatchers.IO) {
            if (network == null) {
                // If network is null, test system-level default connectivity
                for (testUrl in TEST_URLS) {
                    if (testHttpDirect(testUrl)) {
                        Log.i(TAG, "Internet validated via default routing by $testUrl")
                        return@withContext true
                    }
                }
                return@withContext false
            }

            for (testUrl in TEST_URLS) {
                if (testHttp(network, testUrl)) {
                    Log.i(TAG, "Internet validated on network $network by $testUrl")
                    return@withContext true
                }
            }

            Log.w(TAG, "All Internet validation probes failed: $network")
            false
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
                instanceFollowRedirects = true
                useCaches = false
                doInput = true
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "Mozilla/5.0 NetFetch/1.0")
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
                instanceFollowRedirects = true
                useCaches = false
                doInput = true
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "Mozilla/5.0 NetFetch/1.0")
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

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
