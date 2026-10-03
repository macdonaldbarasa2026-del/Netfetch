package com.netfetch.app.network

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Performs a real Internet reachability test on a specific upstream Network.
 *
 * The request is explicitly opened through the supplied Network so NetFetch
 * never accidentally validates the phone's default network instead of the
 * network currently selected as the upstream.
 */
object ConnectivityTester {

    private const val TAG = "NetFetchConnTest"

    private const val TEST_URL =
        "http://connectivitycheck.gstatic.com/generate_204"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    suspend fun testConnectivity(network: Network?): Boolean =
        withContext(Dispatchers.IO) {
            if (network == null) {
                Log.w(TAG, "Connectivity test skipped: no upstream network")
                return@withContext false
            }

            var connection: HttpURLConnection? = null

            try {
                val url = URL(TEST_URL)

                connection = network.openConnection(url) as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Connection", "close")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = connection.responseCode

                /*
                 * The Google connectivity endpoint is expected to return
                 * HTTP 204 with an empty body.
                 *
                 * Requiring 204 prevents a captive portal or arbitrary HTTP
                 * 2xx response from being treated as confirmed Internet.
                 */
                val result = responseCode == HttpURLConnection.HTTP_NO_CONTENT

                Log.i(
                    TAG,
                    "Connectivity test: HTTP $responseCode -> $result (network=$network)"
                )

                result
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Connectivity test failed on $network: ${e.message}"
                )
                false
            } finally {
                connection?.disconnect()
            }
        }
}
