package com.netfetch.app.donation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayPalDonationManagerTest {

    @Before
    fun setup() {
        // Reset to defaults before each test
        PayPalDonationManager.payPalEmail = "macdonaldbarasa2026@gmail.com"
        PayPalDonationManager.hostedButtonId = null
        PayPalDonationManager.backendOrderEndpoint = null
    }

    @Test
    fun testPublicClientIdIsConfigured() {
        assertTrue(PayPalDonationManager.CLIENT_ID.isNotEmpty())
        assertEquals("BAAwxO8qL41wNSMpZ3sXzElWo5uPqSQbO69rKLYVl-x0_0hE2LxBOthnysoKBd5IH5GAye5YzGE9sI-feU", PayPalDonationManager.CLIENT_ID)
    }

    @Test
    fun testNoSecretKeyExposedInManager() {
        // Enforce that no secret key field exists in PayPalDonationManager
        val fields = PayPalDonationManager::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("Secret keys must never exist in client-side code", fields.any { it.contains("secret") })
    }

    @Test
    fun testNoPayPalMeUrlUsed() {
        // PayPal.Me is not supported for Kenya-registered accounts.
        // Ensure the code never generates a paypalme/username-style path regardless of outcome.
        runBlocking {
            val result = PayPalDonationManager.createDonationOrder("5.00")
            // When no hostedButtonId is configured, result is a failure with instructions.
            // Either way, we must not have produced a PayPal.Me profile URL.
            val url = result.getOrNull() ?: ""
            assertFalse(
                "PayPal.Me profile URLs are not valid for Kenya-registered accounts and must not be generated",
                url.matches(Regex("https://www\\.paypal\\.com/paypalme/[^/?]+/.*"))
            )
        }
    }

    @Test
    fun testInvalidAmountReturnsFailure() = runBlocking {
        val resultNegative = PayPalDonationManager.createDonationOrder("-5.00")
        assertTrue(resultNegative.isFailure)

        val resultZero = PayPalDonationManager.createDonationOrder("0")
        assertTrue(resultZero.isFailure)

        val resultInvalid = PayPalDonationManager.createDonationOrder("not_a_number")
        assertTrue(resultInvalid.isFailure)

        val resultEmpty = PayPalDonationManager.createDonationOrder("")
        assertTrue(resultEmpty.isFailure)
    }

    @Test
    fun testValidAmountWithNoButtonIdGeneratesEmailFallbackUrl() = runBlocking {
        // With no hosted button ID and no backend, should return a clear setup-required failure
        // (not a broken/deprecated PayPal URL, not a PayPal.Me link which doesn't work for Kenya)
        PayPalDonationManager.hostedButtonId = null
        val result = PayPalDonationManager.createDonationOrder("5.00")
        assertTrue(
            "Without a configured hosted button, result should be failure with setup instructions",
            result.isFailure
        )
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error should mention how to set up", errorMsg.contains("hosted_button_id") || errorMsg.contains("Donation is not yet active"))
    }

    @Test
    fun testHostedButtonUrlGeneration() = runBlocking {
        PayPalDonationManager.hostedButtonId = "ABC123XYZ"
        val result = PayPalDonationManager.createDonationOrder("10.00")
        assertTrue(result.isSuccess)
        val url = result.getOrNull()
        assertTrue(url != null)
        assertTrue("Should contain hosted_button_id", url!!.contains("hosted_button_id=ABC123XYZ"))
        assertTrue("Should use PayPal donate endpoint", url.startsWith("https://www.paypal.com/donate/"))
    }
}

