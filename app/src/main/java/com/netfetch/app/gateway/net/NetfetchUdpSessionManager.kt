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
import java.util.concurrent.ArrayBlockingQueue

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
        val readerJob: Job,
        val writerJob: Job,
        val outgoing: ArrayBlockingQueue<ByteArray>
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

            val outgoing = ArrayBlockingQueue<ByteArray>(64)
            val writerJob = scope.launch {
                try {
                    while (!socket.isClosed) {
                        val data = outgoing.take()
                        socket.send(DatagramPacket(data, data.size))
                    }
                } catch (_: Exception) {
                    close(key)
                }
            }

            val readerJob = scope.launch {
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
                readerJob = readerJob,
                writerJob = writerJob,
                outgoing = outgoing
            )

            val existing = sessions.putIfAbsent(key, session)

            if (existing != null) {
                readerJob.cancel()
                writerJob.cancel()
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

        // UDP is lossy by design. A bounded queue prevents a burst from
        // creating one coroutine and retained byte array per datagram.
        return session.outgoing.offer(data)
    }

    fun close(key: UdpFlowKey) {
        val session = sessions.remove(key) ?: return

        session.readerJob.cancel()
        session.writerJob.cancel()
        session.outgoing.clear()
        runCatching { session.socket.close() }
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
        scope.cancel()
    }

    fun count(): Int = sessions.size
}
