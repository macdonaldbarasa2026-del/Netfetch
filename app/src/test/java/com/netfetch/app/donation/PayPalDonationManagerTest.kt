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
        PayPalDonationManager.payPalMeUsername = "macdonaldbarasa2026"
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
    fun testValidAmountGeneratesPayPalMeUrl() = runBlocking {
        val result = PayPalDonationManager.createDonationOrder("5.00")
        assertTrue(result.isSuccess)
        val url = result.getOrNull()
        assertTrue(url != null)
        assertTrue(url!!.startsWith("https://www.paypal.com/paypalme/"))
        assertTrue(url.endsWith("/5.00"))
    }

    @Test
    fun testHostedButtonUrlGeneration() = runBlocking {
        PayPalDonationManager.hostedButtonId = "ABC123XYZ"
        val result = PayPalDonationManager.createDonationOrder("10.00")
        assertTrue(result.isSuccess)
        val url = result.getOrNull()
        assertTrue(url != null)
        assertTrue(url!!.contains("hosted_button_id=ABC123XYZ"))
    }
}
