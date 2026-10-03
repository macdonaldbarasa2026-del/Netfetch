package com.netfetch.app.gateway.net

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates IPv4 UDP packets from the TUN interface into direct
 * upstream UDP traffic and writes the responses back into TUN.
 */
class NetfetchUdpEngine(
    private val connector: NetfetchDirectConnector,
    private val writer: (ByteArray) -> Unit
) {

    private val sessions =
        ConcurrentHashMap<UdpFlowKey, Boolean>()

    private val manager =
        NetfetchUdpSessionManager(connector)

    companion object {
        private const val MAX_SESSIONS = 512
    }

    fun handle(
        packet: ByteArray,
        ip: Ipv4Header,
        udp: UdpHeader
    ) {
        val key = UdpFlowKey(
            sourceIp = ip.sourceIp,
            sourcePort = udp.sourcePort,
            destinationIp = ip.destinationIp,
            destinationPort = udp.destinationPort
        )

        val payloadOffset =
            ip.headerLength + 8

        val payloadLength =
            (udp.length - 8).coerceAtLeast(0)

        if (payloadOffset + payloadLength > packet.size) {
            return
        }

        val payload =
            packet.copyOfRange(
                payloadOffset,
                payloadOffset + payloadLength
            )

        if (!sessions.containsKey(key)) {
            if (sessions.size >= MAX_SESSIONS) {
                return
            }

            val destination =
                PacketCodec.ipv4Address(
                    key.destinationIp
                )

            val opened =
                manager.open(
                    key = key,
                    destination = destination,
                    destinationPort = key.destinationPort,
                    onData = { sessionKey, response ->
                        handleResponse(
                            sessionKey,
                            response
                        )
                    },
                    onClosed = { sessionKey ->
                        sessions.remove(sessionKey)
                    }
                )

            if (!opened) {
                return
            }

            sessions[key] = true
        }

        if (payload.isNotEmpty()) {
            manager.send(
                key = key,
                data = payload
            )
        }
    }

    private fun handleResponse(
        key: UdpFlowKey,
        data: ByteArray
    ) {
        val packet =
            PacketCodec.ipv4Udp(
                sourceIp = key.destinationIp,
                destinationIp = key.sourceIp,
                sourcePort = key.destinationPort,
                destinationPort = key.sourcePort,
                payload = data
            )

        writer(packet)
    }

    fun close(key: UdpFlowKey) {
        sessions.remove(key)
        manager.close(key)
    }

    fun closeAll() {
        sessions.clear()
        manager.closeAll()
    }

    fun count(): Int = sessions.size
}
