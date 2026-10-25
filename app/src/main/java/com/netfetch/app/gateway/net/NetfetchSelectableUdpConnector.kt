package com.netfetch.app.gateway.net

import java.nio.channels.DatagramChannel

interface NetfetchSelectableUdpConnector : NetfetchConnector {
    fun openUdpChannel(): DatagramChannel
}
