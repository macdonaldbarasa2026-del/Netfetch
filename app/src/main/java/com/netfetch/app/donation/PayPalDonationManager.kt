package com.netfetch.app.donation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles PayPal Donation checkout flows securely for NetFetch.
 *
 * SECURITY ARCHITECTURE:
 * - NO private PayPal Secret Keys are stored in the Android client application.
 * - For direct mobile donations, generates safe hosted PayPal links (PayPal.me / Hosted Buttons)
 *   that do not require backend infrastructure or expose private merchant credentials.
 * - If a dedicated backend server is deployed, delegates order creation to the backend endpoint
 *   where private credentials reside securely in server environment variables.
 */
object PayPalDonationManager {

    // Public Client ID (public identifier for PayPal integrations, safe on client)
    const val CLIENT_ID = "BAAwxO8qL41wNSMpZ3sXzElWo5uPqSQbO69rKLYVl-x0_0hE2LxBOthnysoKBd5IH5GAye5YzGE9sI-feU"

    // Default PayPal username or PayPal.me identifier
    // For individual creators/developers, PayPal.me handles donations seamlessly without
    // the "organization's account is inactive" error seen on unregistered charity endpoints.
    var payPalMeUsername: String = "macdonaldbarasa2026"

    // Optional PayPal Hosted Button ID (generated via PayPal Merchant Dashboard)
    var hostedButtonId: String? = null

    // Optional custom backend order creation endpoint (e.g. "https://api.yourdomain.com/donations/create-order")
    // Keep null/empty if running client-only standalone mode
    var backendOrderEndpoint: String? = null

    /**
     * Resolves the donation checkout URL for the chosen donation amount.
     * Validates amount format and returns a safe URL.
     */
    suspend fun createDonationOrder(amountUsd: String): Result<String> = withContext(Dispatchers.IO) {
        val parsedAmount = amountUsd.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid donation amount (e.g. 5.00)."))
        }

        val formattedAmount = String.format(java.util.Locale.US, "%.2f", parsedAmount)

        // 1. If a backend server endpoint is configured, delegate order creation to the backend
        val backendUrl = backendOrderEndpoint?.trim()
        if (!backendUrl.isNullOrEmpty()) {
            return@withContext createBackendOrder(backendUrl, formattedAmount)
        }

        // 2. If a hosted button ID is configured, use PayPal Hosted Button URL
        val buttonId = hostedButtonId?.trim()
        if (!buttonId.isNullOrEmpty()) {
            val encodedButton = URLEncoder.encode(buttonId, "UTF-8")
            val hostedUrl = "https://www.paypal.com/donate/?hosted_button_id=$encodedButton"
            return@withContext Result.success(hostedUrl)
        }

        // 3. Use PayPal.me direct support link
        // Unlike "paypal.com/donate/?business=email" (which fails with "organization inactive" for non-charity accounts),
        // PayPal.me works reliably for personal and business accounts worldwide with zero secret key leakage.
        val username = payPalMeUsername.trim().removePrefix("@")
        val cleanUser = if (username.isNotEmpty()) username else "macdonaldbarasa2026"
        val paypalMeUrl = "https://www.paypal.com/paypalme/$cleanUser/$formattedAmount"

        Result.success(paypalMeUrl)
    }

    private fun createBackendOrder(backendUrl: String, amount: String): Result<String> {
        return try {
            val url = URL(backendUrl)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true
                conn.doInput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                val payload = JSONObject().apply {
                    put("amount", amount)
                    put("currency", "USD")
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(payload.toString())
                    it.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

                if (code in 200..299) {
                    val json = JSONObject(responseText)
                    val approvalUrl = json.optString("approvalUrl").ifEmpty { json.optString("approveUrl") }
                    if (approvalUrl.isNotEmpty()) {
                        Result.success(approvalUrl)
                    } else {
                        Result.failure(Exception("Backend did not return an approvalUrl: $responseText"))
                    }
                } else {
                    Result.failure(Exception("Backend donation request failed ($code): $responseText"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
