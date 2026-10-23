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
 * Connects directly to PayPal's REST Orders API to generate direct checkout approval links,
 * with fallback support for direct PayPal donation links.
 */
object PayPalDonationManager {

    // Live PayPal Client ID
    const val CLIENT_ID = "BAAwxO8qL41wNSMpZ3sXzElWo5uPqSQbO69rKLYVl-x0_0hE2LxBOthnysoKBd5IH5GAye5YzGE9sI-feU"
    
    // Live PayPal Secret Key
    const val SECRET_KEY = "EJ2WGxMzK2V7foNEG11YTp4OTireXWug7VD0mpWURcfDndd8IfnLlksOUMex1zc6uu6LHhTkHgLRiCZh"

    // Primary developer donation recipient
    const val PAYPAL_DONATION_EMAIL = "macdonaldbarasa2026@gmail.com"

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
     * Tries Live first, and seamlessly falls back to a direct PayPal donation web link if needed.
     */
    suspend fun createDonationOrder(amountUsd: String): Result<String> = withContext(Dispatchers.IO) {
        val parsedAmount = amountUsd.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid donation amount (minimum $0.50)."))
        }

        val formattedAmount = String.format(java.util.Locale.US, "%.2f", parsedAmount)

        // Generate direct hosted donation link as guaranteed fallback
        val directFallbackUrl = "https://www.paypal.com/donate/?business=$PAYPAL_DONATION_EMAIL&currency_code=USD&amount=$formattedAmount"

        var token: String? = null
        var activeBaseUrl = LIVE_BASE_URL

        // Attempt Live authentication first, then Sandbox
        try {
            token = fetchAccessToken(LIVE_BASE_URL)
            activeBaseUrl = LIVE_BASE_URL
        } catch (liveEx: Exception) {
            try {
                token = fetchAccessToken(SANDBOX_BASE_URL)
                activeBaseUrl = SANDBOX_BASE_URL
            } catch (sandboxEx: Exception) {
                // Return direct donation link if REST token fails (e.g. secret key mismatch)
                return@withContext Result.success(directFallbackUrl)
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
                    Result.success(directFallbackUrl)
                } else {
                    Result.success(directFallbackUrl)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.success(directFallbackUrl)
        }
    }
}
