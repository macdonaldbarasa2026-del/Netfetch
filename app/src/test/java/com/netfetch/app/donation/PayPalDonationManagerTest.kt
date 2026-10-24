package com.netfetch.app.donation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayPalDonationManagerTest {

    @Test
    fun testPaymentUrlMatchesVerifiedNcpLink() {
        val expectedUrl = "https://www.paypal.com/ncp/payment/SVSVDRDVSPU9L"
        assertEquals(expectedUrl, PayPalDonationManager.PAYMENT_URL)
        assertEquals(expectedUrl, PayPalDonationManager.getDonationUrl())
    }

    @Test
    fun testNoSecretKeyExposedInManager() {
        // Enforce that no secret key field exists in PayPalDonationManager
        val fields = PayPalDonationManager::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("Secret keys must never exist in client-side code", fields.any { it.contains("secret") })
    }

    @Test
    fun testNoBrokenOrDeprecatedEndpointsUsed() {
        val url = PayPalDonationManager.getDonationUrl()
        // Must be the working NCP endpoint
        assertTrue("URL must use PayPal NCP payment endpoint", url.startsWith("https://www.paypal.com/ncp/payment/"))
        // Must not use deprecated/broken formats
        assertFalse("Must not use PayPal.Me", url.contains("paypalme"))
        assertFalse("Must not use deprecated donate query parameter", url.contains("donate/?hosted_button_id="))
        assertFalse("Must not use charity cmd=_donations format", url.contains("cmd=_donations"))
    }
}
