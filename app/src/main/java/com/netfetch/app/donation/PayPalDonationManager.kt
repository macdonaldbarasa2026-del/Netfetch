package com.netfetch.app.donation

/**
 * Handles PayPal Donation checkout flows securely for NetFetch.
 *
 * Uses PayPal's verified No-Code Payment (NCP) checkout:
 * https://www.paypal.com/ncp/payment/SVSVDRDVSPU9L
 *
 * SECURITY ARCHITECTURE:
 * - NO private PayPal credentials, client secrets, or server backend required.
 * - Opens PayPal's hosted checkout directly in the donor's default browser or PayPal app.
 * - Eliminates obsolete endpoints (PayPal.Me, charity cmd=_donations, server APIs).
 */
object PayPalDonationManager {

    /**
     * Verified PayPal NCP direct checkout link.
     */
    const val PAYMENT_URL: String = "https://www.paypal.com/ncp/payment/SVSVDRDVSPU9L"

    /**
     * Returns the verified donation checkout URL.
     */
    fun getDonationUrl(): String = PAYMENT_URL
}
