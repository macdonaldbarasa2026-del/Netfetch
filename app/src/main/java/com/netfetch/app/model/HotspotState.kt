package com.netfetch.app.model

sealed class HotspotState {
    object Idle : HotspotState()
    object Starting : HotspotState()
    data class Active(
        val config: HotspotConfig,
        val connectedClients: List<ClientDevice> = emptyList(),
        val downloadSpeedBps: Long = 0L,
        val uploadSpeedBps: Long = 0L,
        val totalBytesTransferred: Long = 0L
    ) : HotspotState()
    data class Error(val errorMessage: String) : HotspotState()
}
