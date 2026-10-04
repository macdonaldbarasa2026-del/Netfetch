package com.netfetch.app.netfetchlink

sealed class NetfetchReceiverState {

    data object Idle : NetfetchReceiverState()

    data object Searching : NetfetchReceiverState()

    data class ProviderFound(
        val deviceName: String,
        val deviceAddress: String
    ) : NetfetchReceiverState()

    data object Connecting : NetfetchReceiverState()

    data class Connected(
        val providerAddress: String,
        val socksPort: Int,
        val sessionToken: String
    ) : NetfetchReceiverState()

    data class Error(
        val message: String
    ) : NetfetchReceiverState()
}
