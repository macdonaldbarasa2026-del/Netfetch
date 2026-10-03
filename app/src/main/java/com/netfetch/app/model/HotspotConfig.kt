package com.netfetch.app.model

enum class TetherMode(val displayName: String, val description: String) {
    NORMAL("Normal Mode", "Standard HTTP/HTTPS Proxy (Port 8282) - For Web, Streaming & Videos"),
    PRO("Pro Mode (Advanced)", "SOCKS5 & Transparent Tunneling (Port 1080) - Real Unblocked Internet for All Apps & Games")
}

data class HotspotConfig(
    val ssid: String = "DIRECT-NetFetch-AccessPoint",
    val passphrase: String = "netfetch8282",
    val hostIp: String = "192.168.49.1",
    val proxyPort: Int = 8282,
    val socksPort: Int = 1080,
    val socksUsername: String = "netfetch",
    val socksPassword: String = "netfetch1080",
    val pacPort: Int = 8283,
    val mode: TetherMode = TetherMode.NORMAL,
    val bandPreference: BandPreference = BandPreference.AUTO,
    val maxConnectedClients: Int = 10,
    val autoStartOnBoot: Boolean = false
)
