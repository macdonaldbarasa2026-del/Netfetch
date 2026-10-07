package com.netfetch.app.model

import com.netfetch.app.network.UpstreamNetworkManager

sealed class HotspotState {
    object Idle : HotspotState()
    object Starting : HotspotState()
    data class Active(
        val config: HotspotConfig,
        val connectedClients: List<ClientDevice> = emptyList(),
        val downloadSpeedBps: Long = 0L,
        val uploadSpeedBps: Long = 0L,
        val totalBytesTransferred: Long = 0L,
        val totalBytesUploaded: Long = 0L,
        val totalBytesDownloaded: Long = 0L,
        val upstreamState: UpstreamNetworkManager.UpstreamState = UpstreamNetworkManager.UpstreamState(),
        val internetVerified: Boolean = false,
        val gatewayAddress: String = "192.168.49.1"
    ) : HotspotState()
    data class Error(val errorMessage: String) : HotspotState()
}
