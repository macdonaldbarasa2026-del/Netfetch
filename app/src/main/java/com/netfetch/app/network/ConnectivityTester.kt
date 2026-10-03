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

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000

    private val TEST_URLS = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://cp.cloudflare.com/"
    )

    suspend fun testConnectivity(network: Network?): Boolean =
        withContext(Dispatchers.IO) {
            if (network == null) {
                Log.w(TAG, "No upstream network supplied")
                return@withContext false
            }

            /*
             * Android's VALIDATED capability is useful evidence, but we still
             * perform an actual request through the exact Network selected by
             * NetFetch.
             */
            for (testUrl in TEST_URLS) {
                if (testUrl == TEST_URLS.first()) {
                    if (testHttp(network, testUrl, require204 = true)) {
                        Log.i(TAG, "Internet validated by $testUrl")
                        return@withContext true
                    }
                } else {
                    if (testHttp(network, testUrl, require204 = false)) {
                        Log.i(TAG, "Internet validated by $testUrl")
                        return@withContext true
                    }
                }
            }

            Log.w(TAG, "All Internet validation probes failed: $network")
            false
        }

    private fun testHttp(
        network: Network,
        address: String,
        require204: Boolean
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
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "NetFetch/1.0")
            }

            val response = connection.responseCode

            val success =
                if (require204) {
                    response == HttpURLConnection.HTTP_NO_CONTENT
                } else {
                    response in 200..399
                }

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
}

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
