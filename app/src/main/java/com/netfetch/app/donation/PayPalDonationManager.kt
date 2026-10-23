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
 * - For direct mobile donations, generates safe hosted PayPal links (Hosted Buttons)
 *   that do not require backend infrastructure or expose private merchant credentials.
 * - If a dedicated backend server is deployed, delegates order creation to the backend endpoint
 *   where private credentials reside securely in server environment variables.
 *
 * KENYA ACCOUNT RESTRICTIONS (important for this account's PayPal registration country):
 * - PayPal.Me (paypal.com/paypalme/username) is NOT available for Kenya-registered accounts.
 *   Using it causes "We can't find this profile." — do not use PayPal.Me for this account.
 * - The ?cmd=_donations charity donate URL requires verified nonprofit status and causes
 *   "This organization's account is inactive" for standard business accounts.
 * - Recommended approach: PayPal Hosted Button (created in your PayPal dashboard Business Tools
 *   → Accept Payments → Donate Button → open amount). Set hostedButtonId below.
 *   Fallback: direct PayPal send page pre-filled with your PayPal email address.
 */
object PayPalDonationManager {

    // Public PayPal Client ID (safe to include in client — not a secret).
    // Used only for informational purposes; actual order creation is handled by the backend.
    const val CLIENT_ID = "BAAwxO8qL41wNSMpZ3sXzElWo5uPqSQbO69rKLYVl-x0_0hE2LxBOthnysoKBd5IH5GAye5YzGE9sI-feU"

    // ── STEP 1 (CONFIGURED): PayPal Hosted Donate Button ID ──
    // Created in PayPal Business dashboard (Pay & Get Paid → PayPal Buttons → Donate)
    // Verified against PayPal's donation node web server endpoint.
    var hostedButtonId: String? = "SVSVDRDVSPU9L"

    // Developer's PayPal-registered email address (used as a universal send-money fallback).
    // This must match exactly the email registered on your PayPal account.
    // It is not a secret — donors will see it on the PayPal send page.
    var payPalEmail: String = "macdonaldbarasa2026@gmail.com"

    // Optional custom backend order creation endpoint (e.g. "https://api.yourdomain.com/donations/create-order")
    // Leave null to run in client-only mode (no backend required).
    var backendOrderEndpoint: String? = null

    /**
     * Resolves the donation checkout URL for the chosen donation amount.
     *
     * Priority order:
     *   1. Backend server endpoint (if configured and deployed)
     *   2. PayPal Hosted Button (if hostedButtonId is set — preferred for Kenya accounts)
     *   3. PayPal direct send-money page pre-filled with the developer's email (universal fallback)
     *
     * NOTE: PayPal.Me links are intentionally removed. PayPal.Me is not available for accounts
     * registered in Kenya and causes "We can't find this profile" errors.
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

        // 2. If a hosted button ID is configured, use PayPal Hosted Button URL.
        //    This is the recommended method for Kenya-registered PayPal accounts.
        //    Create the button at: paypal.com → Business Tools → Accept Payments → Donate Button
        val buttonId = hostedButtonId?.trim()
        if (!buttonId.isNullOrEmpty()) {
            val encodedButton = URLEncoder.encode(buttonId, "UTF-8")
            val hostedUrl = "https://www.paypal.com/donate/?hosted_button_id=$encodedButton"
            return@withContext Result.success(hostedUrl)
        }

        // 3. Fallback: PayPal Hosted Button not yet configured.
        //    Return a clear failure with instructions so the user sees what's needed.
        //    This prevents sending users to a broken or deprecated PayPal URL.
        //    To fix: create a Hosted Donate Button in your PayPal Business dashboard and set hostedButtonId above.
        Result.failure(
            IllegalStateException(
                "Donation is not yet active. To enable donations: log into paypal.com → " +
                    "Business Tools → Accept Payments → Donate Button → create an open-amount button → " +
                    "copy the hosted_button_id → set PayPalDonationManager.hostedButtonId in the app."
            )
        )
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
