package com.netfetch.app.gateway.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns direct upstream UDP flows for the TUN gateway.
 *
 * Every socket is created through NetfetchDirectConnector, so UDP traffic
 * stays on the selected Android Network and is protected from the VPN loop.
 */
class NetfetchUdpSessionManager(
    private val connector: NetfetchConnector
) {

    private data class Session(
        val key: UdpFlowKey,
        val socket: DatagramSocket,
        val job: Job
    )

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessions =
        ConcurrentHashMap<UdpFlowKey, Session>()

    companion object {
        private const val MAX_SESSIONS = 512
    }

    fun open(
        key: UdpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onData: (UdpFlowKey, ByteArray) -> Unit,
        onClosed: (UdpFlowKey) -> Unit
    ): Boolean {
        if (sessions.containsKey(key)) {
            return true
        }

        if (sessions.size >= MAX_SESSIONS) {
            return false
        }

        return try {
            val socket = connector.openUdp()

            socket.connect(
                InetSocketAddress(
                    destination,
                    destinationPort
                )
            )

            val job = scope.launch {
                val buffer = ByteArray(65_507)

                try {
                    while (!socket.isClosed) {
                        val packet = DatagramPacket(
                            buffer,
                            buffer.size
                        )

                        socket.receive(packet)

                        if (packet.length > 0) {
                            onData(
                                key,
                                packet.data.copyOf(packet.length)
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Socket closure and upstream failures end the session.
                } finally {
                    sessions.remove(key)
                    runCatching { socket.close() }
                    onClosed(key)
                }
            }

            val session = Session(
                key = key,
                socket = socket,
                job = job
            )

            val existing = sessions.putIfAbsent(key, session)

            if (existing != null) {
                job.cancel()
                runCatching { socket.close() }
                return true
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun send(
        key: UdpFlowKey,
        data: ByteArray
    ): Boolean {
        val session = sessions[key] ?: return false

        /*
         * Dispatch the send on Dispatchers.IO rather than the caller's thread
         * (which is the TUN packet reader). A slow or blocked upstream socket
         * must not stall the entire TUN reader.
         *
         * Fire-and-forget is acceptable for UDP: if the upstream is so slow
         * that it cannot keep up, the DatagramSocket's send buffer fills and
         * send() will throw, which closes the session.
         */
        scope.launch {
            try {
                session.socket.send(DatagramPacket(data, data.size))
            } catch (_: Exception) {
                close(key)
            }
        }

        return true
    }

    fun close(key: UdpFlowKey) {
        val session = sessions.remove(key) ?: return

        session.job.cancel()
        runCatching { session.socket.close() }
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
        scope.cancel()
    }

    fun count(): Int = sessions.size
}
