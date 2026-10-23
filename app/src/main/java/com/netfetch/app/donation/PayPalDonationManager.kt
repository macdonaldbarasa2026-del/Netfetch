package com.netfetch.app.donation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Handles PayPal Donation checkout flows for users who appreciate NetFetch app creation.
 * Connects directly to PayPal's REST Orders API to generate direct checkout approval links.
 */
object PayPalDonationManager {

    const val CLIENT_ID = "BAAF66-lgyO2ugHv4gI1Fp92RmyIAD5wuN5Zomp1A_K0c-uImRoi1wuQxZ-NwIQsW5NOGP3gBuY7O_oD6I"
    const val SECRET_KEY = "EJ2WGxMzK2V7foNEG11YTp4OTireXWug7VD0mpWURcfDndd8IfnLlksOUMex1zc6uu6LHhTkHgLRiCZh"

    private const val LIVE_BASE_URL = "https://api-m.paypal.com"
    private const val SANDBOX_BASE_URL = "https://api-m.sandbox.paypal.com"

    private fun base64Encode(input: String): String {
        return try {
            android.util.Base64.encodeToString(input.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Obtains an OAuth2 Bearer token from PayPal.
     */
    private fun fetchAccessToken(baseUrl: String): String {
        val url = URL("$baseUrl/v1/oauth2/token")
        val conn = url.openConnection() as HttpsURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.doInput = true

            val authHeader = "Basic " + base64Encode("$CLIENT_ID:$SECRET_KEY").trim()
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write("grant_type=client_credentials")
                writer.flush()
            }

            val statusCode = conn.responseCode
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (statusCode in 200..299) {
                val json = JSONObject(responseText)
                json.getString("access_token")
            } else {
                throw IllegalStateException("PayPal auth failed ($statusCode): $responseText")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Creates a PayPal order for the chosen donation amount and returns the checkout approval URL.
     * Supports both Sandbox and Live PayPal accounts automatically.
     */
    suspend fun createDonationOrder(amountUsd: String): Result<String> = withContext(Dispatchers.IO) {
        val parsedAmount = amountUsd.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid donation amount (minimum $0.50)."))
        }

        val formattedAmount = String.format(java.util.Locale.US, "%.2f", parsedAmount)

        // Try sandbox first (matches user credentials), fallback to live
        var token: String? = null
        var activeBaseUrl = SANDBOX_BASE_URL

        try {
            token = fetchAccessToken(SANDBOX_BASE_URL)
            activeBaseUrl = SANDBOX_BASE_URL
        } catch (sandboxEx: Exception) {
            try {
                token = fetchAccessToken(LIVE_BASE_URL)
                activeBaseUrl = LIVE_BASE_URL
            } catch (liveEx: Exception) {
                return@withContext Result.failure(
                    Exception("Failed to authenticate with PayPal: ${sandboxEx.message ?: liveEx.message}")
                )
            }
        }

        try {
            val url = URL("$activeBaseUrl/v2/checkout/orders")
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.doInput = true

                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                val payload = JSONObject().apply {
                    put("intent", "CAPTURE")
                    val purchaseUnits = JSONArray().apply {
                        val unit = JSONObject().apply {
                            put("description", "NetFetch App Creation & Developer Support Donation")
                            put("amount", JSONObject().apply {
                                put("currency_code", "USD")
                                put("value", formattedAmount)
                            })
                        }
                        put(unit)
                    }
                    put("purchase_units", purchaseUnits)
                    put("application_context", JSONObject().apply {
                        put("brand_name", "NetFetch App")
                        put("user_action", "PAY_NOW")
                    })
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val statusCode = conn.responseCode
                val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

                if (statusCode in 200..299) {
                    val json = JSONObject(responseText)
                    val links = json.optJSONArray("links")
                    if (links != null) {
                        for (i in 0 until links.length()) {
                            val link = links.getJSONObject(i)
                            val rel = link.optString("rel")
                            if (rel.equals("approve", ignoreCase = true) || rel.equals("payer-action", ignoreCase = true)) {
                                val approveUrl = link.optString("href")
                                if (approveUrl.isNotEmpty()) {
                                    return@withContext Result.success(approveUrl)
                                }
                            }
                        }
                    }
                    Result.failure(Exception("PayPal response did not contain an approval link."))
                } else {
                    Result.failure(Exception("PayPal checkout creation error ($statusCode): $responseText"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
