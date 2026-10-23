package com.netfetch.app.donation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayPalDonationManagerTest {

    @Test
    fun testCredentialsAreSet() {
        assertTrue(PayPalDonationManager.CLIENT_ID.isNotEmpty())
        assertTrue(PayPalDonationManager.SECRET_KEY.isNotEmpty())
        assertEquals("BAAwxO8qL41wNSMpZ3sXzElWo5uPqSQbO69rKLYVl-x0_0hE2LxBOthnysoKBd5IH5GAye5YzGE9sI-feU", PayPalDonationManager.CLIENT_ID)
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
    fun testValidAmountReturnsUrl() = runBlocking {
        val result = PayPalDonationManager.createDonationOrder("5.00")
        assertTrue(result.isSuccess)
        val url = result.getOrNull()
        assertTrue(url != null && url.startsWith("https://"))
    }
}
