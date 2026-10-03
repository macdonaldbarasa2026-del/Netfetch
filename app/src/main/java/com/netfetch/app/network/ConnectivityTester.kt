package com.netfetch.app.network

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ConnectivityTester
 *
 * Tests whether the selected upstream network actually has internet access
 * by making a lightweight HTTP HEAD request bound to that network.
 *
 * This is separate from Android's NET_CAPABILITY_VALIDATED because validated
 * just means Android thinks the network is working — it may still have a
 * captive portal or be flaky. We do a real round-trip to confirm.
 */
object ConnectivityTester {
    private val TAG = "NetFetchConnTest"
    private val TEST_URL = "http://connectivitycheck.gstatic.com/generate_204"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    /**
     * Tests internet connectivity on the given upstream network.
     * Returns true if the network can successfully reach the internet.
     * If [network] is null, uses the device default network.
     */
    suspend fun testConnectivity(network: Network? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(TEST_URL)
            val connection = if (network != null) {
                network.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            connection.apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
            }

            try {
                val responseCode = connection.responseCode
                // 204 = No Content (Google's connectivity check)
                // 200 = OK
                val result = responseCode in 200..299
                Log.i(TAG, "Connectivity test result: $responseCode -> $result (network=$network)")
                result
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity test failed: ${e.message}")
            false
        }
    }
}
