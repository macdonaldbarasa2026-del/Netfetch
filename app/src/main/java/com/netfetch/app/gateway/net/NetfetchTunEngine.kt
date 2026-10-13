package com.netfetch.app.gateway.net

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main userspace packet engine for Netfetch.
 *
 * TUN -> IPv4 parser -> TCP/UDP engine -> protected upstream network
 * Upstream response -> TCP/UDP engine -> TUN
 *
 * This engine currently handles IPv4 TCP and UDP.
 */
class NetfetchTunEngine(
    private val tunInterface: ParcelFileDescriptor,
    private val connector: NetfetchConnector,
    private val enableUdp: Boolean = true
) {

    private val running = AtomicBoolean(false)

    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    /*
     * Bounded TUN output queue.
     *
     * Keep memory bounded without silently dropping packets when the
     * TUN writer is temporarily behind.
     */
    private val writeQueue =
        java.util.concurrent.ArrayBlockingQueue<ByteArray>(4096)

    private val tcpEngine =
        NetfetchTcpEngine(
            connector = connector,
            writer = ::enqueue
        )

    private val udpEngine =
        NetfetchUdpEngine(
            connector = connector,
            writer = ::enqueue
        )

    private val dnsExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(16) { r ->
            Thread(r, "Netfetch-DnsResolver").apply { isDaemon = true }
        }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        readerThread = Thread(
            ::readLoop,
            "Netfetch-TunReader"
        ).also {
            it.start()
        }

        writerThread = Thread(
            ::writeLoop,
            "Netfetch-TunWriter"
        ).also {
            it.start()
        }
    }

    private fun readLoop() {
        val input = FileInputStream(
            tunInterface.fileDescriptor
        )

        val buffer = ByteArray(32 * 1024)

        try {
            while (running.get()) {
                val length = input.read(buffer)

                if (length == 0) {
                    continue
                }

                if (length < 0) break

                handlePacket(
                    buffer.copyOf(length)
                )
            }
        } catch (_: Exception) {
            if (running.get()) {
                running.set(false)
            }
        } finally {
            runCatching { input.close() }
        }
    }

    private fun handlePacket(
        packet: ByteArray
    ) {
        if (packet.size < 20) {
            return
        }

        val ip =
            Ipv4Header.parse(
                packet,
                packet.size
            ) ?: return

        when (ip.protocol) {
            PROTO_TCP -> {
                val offset = ip.headerLength
                val available =
                    ip.totalLength - offset

                if (available < 20) {
                    return
                }

                val tcp =
                    TcpHeader.parse(
                        packet,
                        offset,
                        available
                    ) ?: return

                tcpEngine.handle(
                    packet = packet,
                    ip = ip,
                    tcp = tcp
                )
            }

            PROTO_UDP -> {
                if (!enableUdp) {
                    return
                }

                val offset = ip.headerLength
                val available =
                    ip.totalLength - offset

                if (available < 8) {
                    return
                }

                val udp =
                    UdpHeader.parse(
                        packet,
                        offset,
                        available
                    ) ?: return

                if (udp.destinationPort == 53) {
                    val payloadOffset = offset + 8
                    val payloadLength = (udp.length - 8).coerceAtLeast(0)
                    if (payloadOffset + payloadLength <= packet.size && payloadLength > 0) {
                        val queryPayload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)
                        val dnsDest = PacketCodec.ipv4Address(ip.destinationIp)

                        dnsExecutor.execute {
                            val response = connector.resolveDns(dnsDest, queryPayload)
                            if (response != null) {
                                val replyPacket = PacketCodec.ipv4Udp(
                                    sourceIp = ip.destinationIp,
                                    destinationIp = ip.sourceIp,
                                    sourcePort = udp.destinationPort,
                                    destinationPort = udp.sourcePort,
                                    payload = response
                                )
                                enqueue(replyPacket)
                            } else {
                                udpEngine.handle(packet, ip, udp)
                            }
                        }
                        return
                    }
                }

                udpEngine.handle(
                    packet = packet,
                    ip = ip,
                    udp = udp
                )
            }

            else -> {
                // IPv4 protocols other than TCP/UDP are currently ignored.
            }
        }
    }

    private fun enqueue(
        packet: ByteArray
    ) {
        if (!running.get()) {
            return
        }

        /*
         * Writer callbacks run from flow workers and TCP housekeeping. Never
         * wait here: a saturated TUN writer must not serialize every flow
         * behind it. TCP retains unacknowledged segments for retransmission;
         * UDP is intentionally lossy under congestion.
         */
        writeQueue.offer(packet)
    }

    private fun writeLoop() {
        val output = FileOutputStream(
            tunInterface.fileDescriptor
        )

        try {
            while (running.get()) {
                /*
                 * Use a timed poll instead of blocking take().
                 *
                 * take() blocks indefinitely when the queue is empty.  If
                 * stop() sets running=false while the writer is parked in
                 * take(), the thread can only be woken by interrupt().  The
                 * race between interrupt() and the next queued packet makes
                 * shutdown non-deterministic.
                 *
                 * poll(100ms) times out, re-checks running.get(), and exits
                 * cleanly without depending on interrupt timing.
                 */
                val packet = writeQueue.poll(
                    100, java.util.concurrent.TimeUnit.MILLISECONDS
                ) ?: continue

                output.write(packet)
            }

            // Drain any packets already in the queue at the moment stop() was
            // called so in-flight data is not silently discarded.
            var remaining: ByteArray?
            while (writeQueue.poll().also { remaining = it } != null) {
                runCatching { output.write(remaining!!) }
            }
        } catch (_: InterruptedException) {
            // Interrupted during the timed poll — treat as stop signal.
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            if (running.get()) {
                running.set(false)
            }
        } finally {
            runCatching { output.close() }
        }
    }

    fun tcpSessionCount(): Int =
        tcpEngine.count()

    fun udpSessionCount(): Int =
        udpEngine.count()

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        tcpEngine.shutdown()
        udpEngine.closeAll()

        readerThread?.interrupt()
        writerThread?.interrupt()

        // Allow the writer thread to finish its drain loop cleanly before closing
        // the underlying file descriptor to avoid EBADF (Bad file descriptor) exceptions.
        runCatching {
            writerThread?.join(500)
        }

        readerThread = null
        writerThread = null

        dnsExecutor.shutdownNow()

        writeQueue.clear()

        runCatching {
            tunInterface.close()
        }
    }
}
