package com.netfetch.app.gateway.net

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadLocalRandom

/**
 * IPv4 TCP translator for the Netfetch TUN gateway.
 *
 * The client side is represented by TCP packets on the TUN interface.
 * The server side is a normal protected Android TCP socket.
 *
 * This implementation deliberately keeps TCP state in userspace:
 * - three-way handshake
 * - sequence and acknowledgement tracking
 * - bounded retransmission queue for gateway -> client data
 * - duplicate SYN handling
 * - FIN/RST handling
 * - idle flow cleanup
 */
class NetfetchTcpEngine(
    private val connector: NetfetchConnector,
    private val writer: (ByteArray) -> Unit
) {

    private companion object {
        const val RECEIVE_WINDOW = 65_535
        const val MAX_UNACKED_BYTES = 65_535
        const val MAX_UNACKED_SEGMENTS = 128
        const val MAX_TCP_PAYLOAD = 1460
        const val MAX_PENDING_UPSTREAM_BYTES = 512 * 1024

        const val RETRANSMIT_TIMEOUT_MS = 400L
        const val IDLE_TIMEOUT_MS = 90_000L
        const val HALF_CLOSED_TIMEOUT_MS = 15_000L
        const val MAX_FLOWS = 2048
        const val HOUSEKEEPING_MS = 100L

        const val TCP_FIN = TcpHeader.FIN
        const val TCP_SYN = TcpHeader.SYN
        const val TCP_RST = TcpHeader.RST
        const val TCP_ACK = TcpHeader.ACK
    }

    private data class Segment(
        val sequence: Long,
        val endSequence: Long,
        val packet: ByteArray,
        var lastSentAt: Long
    )

    private data class Flow(
        val key: TcpFlowKey,
        val clientInitialSequence: Long,
        val serverInitialSequence: Long,

        var nextClientSequence: Long,
        var nextServerSequence: Long,

        var clientWindow: Int = RECEIVE_WINDOW,

        var synSent: Boolean = false,
        var synAcknowledged: Boolean = false,
        var clientFinReceived: Boolean = false,
        var serverFinSent: Boolean = false,
        var closed: Boolean = false,

        var lastActivityAt: Long = System.currentTimeMillis(),
        var lastAckReceived: Long = 0L,
        var duplicateAckCount: Int = 0,

        val unacknowledged: java.util.ArrayDeque<Segment> =
            java.util.ArrayDeque(),

        var unacknowledgedBytes: Int = 0,
        var pendingUpstreamData: ByteArray? = null,
        var pendingClientPayload: ByteArray? = null
    )

    private val flows = ConcurrentHashMap<TcpFlowKey, Flow>()

    private val sessions =
        NetfetchTcpSessionManager(connector)

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Netfetch-TcpHousekeeper").apply {
                isDaemon = true
            }
        }

    @Volatile
    private var stopped = false

    init {
        scheduler.scheduleAtFixedRate(
            { housekeeping() },
            HOUSEKEEPING_MS,
            HOUSEKEEPING_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun handle(
        packet: ByteArray,
        ip: Ipv4Header,
        tcp: TcpHeader
    ) {
        if (stopped) {
            return
        }

        val key = TcpFlowKey(
            sourceIp = ip.sourceIp,
            sourcePort = tcp.sourcePort,
            destinationIp = ip.destinationIp,
            destinationPort = tcp.destinationPort
        )

        val payloadOffset =
            ip.headerLength + tcp.dataOffset

        val payloadLength =
            (ip.totalLength - payloadOffset).coerceAtLeast(0)

        val payload =
            if (
                payloadLength > 0 &&
                payloadOffset >= 0 &&
                payloadOffset + payloadLength <= packet.size
            ) {
                packet.copyOfRange(
                    payloadOffset,
                    payloadOffset + payloadLength
                )
            } else {
                ByteArray(0)
            }

        synchronized(this) {
            if (stopped) {
                return
            }

            if (tcp.rst) {
                closeLocked(key)
                return
            }

            if (tcp.syn && !tcp.ack) {
                handleSynLocked(key, tcp)
                return
            }

            val flow = flows[key]

            if (flow == null) {
                sendRst(
                    key = key,
                    sequence = tcp.acknowledgement,
                    acknowledgement =
                        tcp.sequence +
                            payload.size +
                            if (tcp.fin) 1 else 0
                )
                return
            }

            flow.lastActivityAt = System.currentTimeMillis()

            flow.clientWindow = tcp.window

            if (tcp.ack) {
                processAcknowledgementLocked(
                    flow = flow,
                    acknowledgement = tcp.acknowledgement
                )
            }

            if (!flow.synAcknowledged) {
                if (
                    tcp.ack &&
                    tcp.acknowledgement == flow.nextServerSequence
                ) {
                    flow.synAcknowledged = true
                } else {
                    resendSynAckLocked(flow)
                    return
                }
            }

            if (payload.isNotEmpty()) {
                handleClientPayloadLocked(
                    flow = flow,
                    sequence = tcp.sequence,
                    payload = payload
                )
            }

            if (tcp.fin) {
                handleClientFinLocked(
                    flow = flow,
                    sequence = tcp.sequence,
                    payloadLength = payload.size
                )
            } else if (payload.isEmpty()) {
                sendAckLocked(flow)
            }
        }
    }

    private fun handleSynLocked(
        key: TcpFlowKey,
        tcp: TcpHeader
    ) {
        val existing = flows[key]

        if (existing != null) {
            existing.lastActivityAt = System.currentTimeMillis()

            if (existing.synSent && !existing.synAcknowledged) {
                resendSynAckLocked(existing)
            }

            return
        }

        if (flows.size >= MAX_FLOWS) {
            val oldest = flows.values.minByOrNull { it.lastActivityAt }
            if (oldest != null) {
                closeLocked(oldest.key)
            } else {
                sendRst(
                    key = key,
                    sequence = 0,
                    acknowledgement = tcp.sequence + 1
                )
                return
            }
        }

        val serverSequence =
            ThreadLocalRandom.current()
                .nextLong(1L, 0xFFFF_FFFFL)

        val flow = Flow(
            key = key,
            clientInitialSequence = tcp.sequence,
            serverInitialSequence = serverSequence,
            nextClientSequence = tcp.sequence + 1,
            nextServerSequence = serverSequence + 1
        )

        flows[key] = flow

        val destination =
            PacketCodec.ipv4Address(key.destinationIp)

        /*
         * Upstream connection establishment is asynchronous.
         *
         * The old implementation called connector.connectTcp() directly
         * while holding this engine's synchronized lock. A slow DNS/connect
         * path could therefore stall every other TCP flow.
         */
        sessions.open(
            key = key,
            destination = destination,
            destinationPort = key.destinationPort,

            onConnected = { sessionKey ->
                synchronized(this) {
                    val connectedFlow =
                        flows[sessionKey]
                            ?: return@synchronized

                    if (
                        stopped ||
                        connectedFlow.closed
                    ) {
                        return@synchronized
                    }

                    connectedFlow.synSent = true
                    sendSynAckLocked(
                        connectedFlow
                    )

                    // Forward early client payload buffered while upstream was connecting
                    val earlyPayload = connectedFlow.pendingClientPayload
                    if (earlyPayload != null) {
                        connectedFlow.pendingClientPayload = null
                        sessions.write(sessionKey, earlyPayload)
                    }
                }
            },

            onConnectFailed = { sessionKey ->
                synchronized(this) {
                    val failedFlow =
                        flows.remove(
                            sessionKey
                        )

                    if (failedFlow != null) {
                        failedFlow.closed = true

                        sendRst(
                            key = sessionKey,
                            sequence = failedFlow.serverInitialSequence,
                            acknowledgement =
                                failedFlow.nextClientSequence
                        )
                    }
                }
            },

            onData = { sessionKey, data ->
                synchronized(this) {
                    handleUpstreamDataLocked(
                        sessionKey,
                        data
                    )
                }
            },

            onClosed = { sessionKey ->
                synchronized(this) {
                    handleUpstreamClosedLocked(
                        sessionKey
                    )
                }
            }
        )
    }

    private fun sendSynAckLocked(flow: Flow) {
        val packet =
            PacketCodec.ipv4Tcp(
                sourceIp = flow.key.destinationIp,
                destinationIp = flow.key.sourceIp,
                sourcePort = flow.key.destinationPort,
                destinationPort = flow.key.sourcePort,
                sequence = flow.serverInitialSequence,
                acknowledgement = flow.nextClientSequence,
                flags = TCP_SYN or TCP_ACK,
                window = RECEIVE_WINDOW
            )

        writer(packet)
        flow.lastActivityAt = System.currentTimeMillis()
    }

    private fun resendSynAckLocked(flow: Flow) {
        val packet =
            PacketCodec.ipv4Tcp(
                sourceIp = flow.key.destinationIp,
                destinationIp = flow.key.sourceIp,
                sourcePort = flow.key.destinationPort,
                destinationPort = flow.key.sourcePort,
                sequence = flow.serverInitialSequence,
                acknowledgement = flow.nextClientSequence,
                flags = TCP_SYN or TCP_ACK,
                window = RECEIVE_WINDOW
            )

        writer(packet)
    }

    private fun handleClientPayloadLocked(
        flow: Flow,
        sequence: Long,
        payload: ByteArray
    ) {
        if (sequence != flow.nextClientSequence) {
            sendAckLocked(flow)
            return
        }

        if (payload.size > flow.clientWindow.coerceAtLeast(0)) {
            sendAckLocked(flow)
            return
        }

        if (!flow.synSent) {
            // Upstream connection is still in flight; buffer early payload rather than sending RST
            val existing = flow.pendingClientPayload
            flow.pendingClientPayload = if (existing == null) {
                payload
            } else {
                val combined = ByteArray(existing.size + payload.size)
                System.arraycopy(existing, 0, combined, 0, existing.size)
                System.arraycopy(payload, 0, combined, existing.size, payload.size)
                combined
            }
            flow.nextClientSequence += payload.size
            flow.lastActivityAt = System.currentTimeMillis()
            return
        }

        val accepted =
            sessions.write(
                flow.key,
                payload
            )

        if (!accepted) {
            sendRst(
                key = flow.key,
                sequence = flow.nextServerSequence,
                acknowledgement =
                    sequence + payload.size
            )

            closeLocked(flow.key)
            return
        }

        flow.nextClientSequence += payload.size
        flow.lastActivityAt = System.currentTimeMillis()

        sendAckLocked(flow)
    }

    private fun handleClientFinLocked(
        flow: Flow,
        sequence: Long,
        payloadLength: Int
    ) {
        val finSequence =
            sequence + payloadLength

        if (finSequence == flow.nextClientSequence) {
            flow.nextClientSequence += 1
            flow.clientFinReceived = true

            sendAckLocked(flow)

            if (!flow.serverFinSent) {
                sessions.close(flow.key)
            }
        } else {
            sendAckLocked(flow)
        }
    }

    private fun handleUpstreamDataLocked(
        key: TcpFlowKey,
        data: ByteArray
    ) {
        val flow = flows[key] ?: return

        if ((data.isEmpty() && flow.pendingUpstreamData == null) || flow.closed) {
            return
        }

        flow.lastActivityAt = System.currentTimeMillis()

        val fullData = if (flow.pendingUpstreamData != null) {
            val pending = flow.pendingUpstreamData!!
            flow.pendingUpstreamData = null
            if (data.isNotEmpty()) {
                val combined = ByteArray(pending.size + data.size)
                System.arraycopy(pending, 0, combined, 0, pending.size)
                System.arraycopy(data, 0, combined, pending.size, data.size)
                combined
            } else {
                pending
            }
        } else {
            data
        }

        var offset = 0

        while (offset < fullData.size) {
            if (
                flow.unacknowledgedBytes >= MAX_UNACKED_BYTES ||
                flow.unacknowledged.size >= MAX_UNACKED_SEGMENTS
            ) {
                break
            }

            val available =
                MAX_UNACKED_BYTES -
                    flow.unacknowledgedBytes

            val advertisedWindow =
                flow.clientWindow
                    .coerceIn(0, RECEIVE_WINDOW)

            val availableClientWindow =
                (advertisedWindow -
                    flow.unacknowledgedBytes)
                    .coerceAtLeast(0)

            if (availableClientWindow <= 0) {
                break
            }

            val chunkSize =
                minOf(
                    fullData.size - offset,
                    available,
                    availableClientWindow,
                    MAX_TCP_PAYLOAD
                )

            if (chunkSize <= 0) {
                break
            }

            val chunk =
                fullData.copyOfRange(
                    offset,
                    offset + chunkSize
                )

            val packet =
                PacketCodec.ipv4Tcp(
                    sourceIp = key.destinationIp,
                    destinationIp = key.sourceIp,
                    sourcePort = key.destinationPort,
                    destinationPort = key.sourcePort,
                    sequence = flow.nextServerSequence,
                    acknowledgement = flow.nextClientSequence,
                    flags = TcpHeader.PUSH_ACK,
                    window = RECEIVE_WINDOW,
                    payload = chunk
                )

            val segment =
                Segment(
                    sequence = flow.nextServerSequence,
                    endSequence =
                        flow.nextServerSequence +
                            chunk.size,
                    packet = packet,
                    lastSentAt = System.currentTimeMillis()
                )

            flow.unacknowledged.addLast(segment)
            flow.unacknowledgedBytes += chunk.size
            flow.nextServerSequence += chunk.size

            writer(packet)

            offset += chunk.size
        }

        if (offset < fullData.size) {
            val remaining = fullData.size - offset
            if (remaining > MAX_PENDING_UPSTREAM_BYTES) {
                // Buffer cap exceeded: client has stopped consuming data or acknowledging packets.
                // Discarding a partial tail would corrupt byte stream ordering. Cleanly terminate with RST.
                sendRst(
                    key = key,
                    sequence = flow.nextServerSequence,
                    acknowledgement = flow.nextClientSequence
                )
                closeLocked(key)
                return
            }
            flow.pendingUpstreamData = fullData.copyOfRange(offset, fullData.size)
        }
    }

    private fun handleUpstreamClosedLocked(
        key: TcpFlowKey
    ) {
        val flow = flows[key] ?: return

        if (flow.closed || flow.serverFinSent) {
            return
        }

        flow.serverFinSent = true

        val packet =
            PacketCodec.ipv4Tcp(
                sourceIp = key.destinationIp,
                destinationIp = key.sourceIp,
                sourcePort = key.destinationPort,
                destinationPort = key.sourcePort,
                sequence = flow.nextServerSequence,
                acknowledgement = flow.nextClientSequence,
                flags = TcpHeader.FIN or TcpHeader.ACK,
                window = RECEIVE_WINDOW
            )

        val segment =
            Segment(
                sequence = flow.nextServerSequence,
                endSequence = flow.nextServerSequence + 1,
                packet = packet,
                lastSentAt = System.currentTimeMillis()
            )

        flow.unacknowledged.addLast(segment)
        flow.unacknowledgedBytes += 1
        flow.nextServerSequence += 1

        writer(packet)
    }

    private fun processAcknowledgementLocked(
        flow: Flow,
        acknowledgement: Long
    ) {
        if (acknowledgement < flow.serverInitialSequence + 1) {
            return
        }

        if (acknowledgement > flow.nextServerSequence) {
            return
        }

        // Fast Retransmit (RFC 5681): 3 duplicate ACKs trigger immediate retransmission of missing segment
        if (acknowledgement == flow.lastAckReceived && flow.unacknowledged.isNotEmpty()) {
            flow.duplicateAckCount++
            if (flow.duplicateAckCount == 3) {
                val headSegment = flow.unacknowledged.peekFirst()
                if (headSegment != null) {
                    writer(headSegment.packet)
                    headSegment.lastSentAt = System.currentTimeMillis()
                }
            }
        } else if (acknowledgement > flow.lastAckReceived) {
            flow.lastAckReceived = acknowledgement
            flow.duplicateAckCount = 0
        }

        while (true) {
            val segment =
                flow.unacknowledged.peekFirst()
                    ?: break

            if (acknowledgement <= segment.sequence) {
                break
            }

            if (acknowledgement >= segment.endSequence) {
                flow.unacknowledged.removeFirst()

                flow.unacknowledgedBytes =
                    (
                        flow.unacknowledgedBytes -
                            (segment.endSequence - segment.sequence).toInt()
                        ).coerceAtLeast(0)

                continue
            }

            val acknowledgedBytes =
                (acknowledgement - segment.sequence).toInt()

            val remainingBytes =
                (segment.endSequence - acknowledgement).toInt()

            if (remainingBytes <= 0) {
                flow.unacknowledged.removeFirst()

                flow.unacknowledgedBytes =
                    (
                        flow.unacknowledgedBytes -
                            acknowledgedBytes
                        ).coerceAtLeast(0)

                continue
            }

            val ipHeader = Ipv4Header.parse(segment.packet, segment.packet.size)
            val ipHeaderLength = ipHeader?.headerLength ?: 20
            val tcpHeader = if (ipHeader != null) {
                TcpHeader.parse(segment.packet, ipHeaderLength, segment.packet.size - ipHeaderLength)
            } else null
            val headerLength = ipHeaderLength + (tcpHeader?.dataOffset ?: 20)

            val originalPayloadLength =
                (segment.packet.size - headerLength).coerceAtLeast(0)

            if (originalPayloadLength <= 0) {
                break
            }

            val acknowledgedPayload =
                acknowledgedBytes.coerceIn(
                    0,
                    originalPayloadLength
                )

            val remainingPayload =
                segment.packet.copyOfRange(
                    headerLength + acknowledgedPayload,
                    segment.packet.size
                )

            val rebuiltPacket =
                PacketCodec.ipv4Tcp(
                    sourceIp = flow.key.destinationIp,
                    destinationIp = flow.key.sourceIp,
                    sourcePort = flow.key.destinationPort,
                    destinationPort = flow.key.sourcePort,
                    sequence = acknowledgement,
                    acknowledgement = flow.nextClientSequence,
                    flags = TCP_ACK or TcpHeader.PUSH_ACK,
                    window = RECEIVE_WINDOW,
                    payload = remainingPayload
                )

            val replacement =
                Segment(
                    sequence = acknowledgement,
                    endSequence = segment.endSequence,
                    packet = rebuiltPacket,
                    lastSentAt = System.currentTimeMillis()
                )

            flow.unacknowledged.removeFirst()
            flow.unacknowledged.addFirst(replacement)

            flow.unacknowledgedBytes =
                (
                    flow.unacknowledgedBytes -
                        acknowledgedBytes
                    ).coerceAtLeast(0)

            break
        }

        if (flow.pendingUpstreamData != null && !flow.closed) {
            handleUpstreamDataLocked(flow.key, ByteArray(0))
        }

        if (
            flow.serverFinSent &&
            acknowledgement >= flow.nextServerSequence &&
            flow.clientFinReceived
        ) {
            closeLocked(flow.key)
        }
    }

    private fun sendAckLocked(
        flow: Flow
    ) {
        val window = sessions.availableReceiveWindow(flow.key)
            .coerceIn(0, RECEIVE_WINDOW)

        writer(
            PacketCodec.ipv4Tcp(
                sourceIp = flow.key.destinationIp,
                destinationIp = flow.key.sourceIp,
                sourcePort = flow.key.destinationPort,
                destinationPort = flow.key.sourcePort,
                sequence = flow.nextServerSequence,
                acknowledgement = flow.nextClientSequence,
                flags = TCP_ACK,
                window = window
            )
        )
    }

    private fun housekeeping() {
        synchronized(this) {
            if (stopped) {
                return
            }

            val now = System.currentTimeMillis()

            flows.values.toList().forEach { flow ->
                if (flow.closed) {
                    closeLocked(flow.key)
                    return@forEach
                }

                val iterator =
                    flow.unacknowledged.iterator()

                while (iterator.hasNext()) {
                    val segment = iterator.next()

                    if (
                        now - segment.lastSentAt >=
                        RETRANSMIT_TIMEOUT_MS
                    ) {
                        writer(segment.packet)
                        segment.lastSentAt = now
                    }
                }

                val isHalfClosed = flow.clientFinReceived || flow.serverFinSent
                val maxIdle = if (isHalfClosed) HALF_CLOSED_TIMEOUT_MS else IDLE_TIMEOUT_MS
                if (now - flow.lastActivityAt >= maxIdle) {
                    closeLocked(flow.key)
                }
            }
        }
    }

    private fun sendRst(
        key: TcpFlowKey,
        sequence: Long,
        acknowledgement: Long
    ) {
        writer(
            PacketCodec.ipv4Tcp(
                sourceIp = key.destinationIp,
                destinationIp = key.sourceIp,
                sourcePort = key.destinationPort,
                destinationPort = key.sourcePort,
                sequence = sequence,
                acknowledgement = acknowledgement,
                flags = TCP_RST or TCP_ACK,
                window = 0
            )
        )
    }

    private fun closeLocked(
        key: TcpFlowKey
    ) {
        val flow = flows.remove(key)

        if (flow != null) {
            flow.closed = true
        }

        sessions.close(key)
    }

    fun close(key: TcpFlowKey) {
        synchronized(this) {
            closeLocked(key)
        }
    }

    fun closeAll() {
        synchronized(this) {
            flows.clear()
            sessions.closeAll()
        }
    }

    fun count(): Int =
        flows.size

    fun shutdown() {
        synchronized(this) {
            if (stopped) {
                return
            }

            stopped = true
            flows.clear()
            sessions.closeAll()
        }

        scheduler.shutdownNow()
    }
}
