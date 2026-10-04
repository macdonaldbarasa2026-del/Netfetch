package com.netfetch.app.netfetchlink

object NetfetchLinkProtocol {
    const val SERVICE_INSTANCE = "NetFetch"
    const val SERVICE_TYPE = "_netfetch._tcp."

    const val VERSION = "1"
    const val APP = "netfetch"

    const val KEY_VERSION = "version"
    const val KEY_APP = "app"
    const val KEY_MODE = "mode"
    const val KEY_SOCKS_PORT = "socksport"
    const val KEY_HTTP_PORT = "httpport"
    const val KEY_PAC_PORT = "pacport"
    const val KEY_SSID = "ssid"
    const val KEY_UDP = "udp"

    /** No authenticated receiver/provider UDP data plane exists yet. */
    const val UDP_TRANSPORT_AVAILABLE = false

    /** Mode values advertised in DNS-SD TXT record and sent over the link protocol. */
    const val MODE_NORMAL = "NORMAL"
    const val MODE_PRO = "PRO"

    const val DEFAULT_SOCKS_PORT = 1080
    const val DEFAULT_HTTP_PORT = 8282
    const val DEFAULT_PAC_PORT = 8283
}
