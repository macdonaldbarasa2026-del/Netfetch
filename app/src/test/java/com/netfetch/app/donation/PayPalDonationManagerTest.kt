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
        assertEquals("BAAF66-lgyO2ugHv4gI1Fp92RmyIAD5wuN5Zomp1A_K0c-uImRoi1wuQxZ-NwIQsW5NOGP3gBuY7O_oD6I", PayPalDonationManager.CLIENT_ID)
        assertEquals("EJ2WGxMzK2V7foNEG11YTp4OTireXWug7VD0mpWURcfDndd8IfnLlksOUMex1zc6uu6LHhTkHgLRiCZh", PayPalDonationManager.SECRET_KEY)
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
}
