package com.netfetch.app.gateway.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns real upstream TCP sockets created for TUN flows.
 *
 * Important performance rules:
 * - TCP connect NEVER blocks the TUN packet-processing thread.
 * - TCP writes NEVER block the TUN packet-processing thread.
 * - Every flow has a bounded write queue.
 * - Slow upstream connections therefore cannot stall unrelated flows.
 */
class NetfetchTcpSessionManager(
    private val connector: NetfetchConnector
) {

    private companion object {
        const val WRITE_QUEUE_CAPACITY = 64
        const val SOCKET_BUFFER_SIZE = 256 * 1024
        /**
         * How long write() waits for a slot in the write queue when it is
         * momentarily full before giving up and closing the stalled session.
         * 50 ms absorbs micro-bursts; a consistently-full queue indicates
         * a genuinely stuck upstream socket.
         */
        const val WRITE_OFFER_TIMEOUT_MS = 15L
    }

    private class Session(
        val key: TcpFlowKey,
        val socket: Socket,
        val output: java.io.OutputStream,
        val writeQueue: ArrayBlockingQueue<ByteArray>
    ) {
        lateinit var readerJob: Job
        lateinit var writerJob: Job
    }

    private val sessionThreadCounter = AtomicLong(0L)

    /*
     * Bounded thread pool for upstream TCP session readers and writers.
     *
     * Each session creates 2 coroutines (reader + writer) that block on IO.
     * Cap: 256 threads ≈ 128 concurrent upstream TCP sessions.
     *
     * Critical: We use AbortPolicy rather than CallerRunsPolicy. Under
     * CallerRunsPolicy, an executor saturation would force the caller thread
     * (the TUN reader thread, inside NetfetchTcpEngine's synchronized lock)
     * to execute connector.connectTcp() and socket.read(), completely freezing
     * the entire TUN interface and all concurrent flows. AbortPolicy safely
     * rejects the new session, triggering clean TCP RST/onConnectFailed.
     */
    private val sessionExecutor = java.util.concurrent.ThreadPoolExecutor(
        4,   // core threads — always kept alive
        256, // max threads
        60L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(),
        { runnable ->
            Thread(runnable, "NetFetch-TunTcp-${sessionThreadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        },
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    )

    private val scope =
        CoroutineScope(SupervisorJob() + sessionExecutor.asCoroutineDispatcher())

    private val sessions =
        ConcurrentHashMap<TcpFlowKey, Session>()

    /**
     * Connections that are currently being established.
     *
     * This prevents duplicate upstream connects when the client retransmits
     * its SYN while the first upstream connection is still being created.
     */
    private val connecting =
        ConcurrentHashMap<TcpFlowKey, Job>()

    fun open(
        key: TcpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onConnected: (TcpFlowKey) -> Unit,
        onConnectFailed: (TcpFlowKey) -> Unit,
        onData: (TcpFlowKey, ByteArray) -> Unit,
        onClosed: (TcpFlowKey) -> Unit
    ): Boolean {

        if (sessions.containsKey(key)) {
            onConnected(key)
            return true
        }

        if (connecting.containsKey(key)) {
            return true
        }

        // Register the connecting marker before this coroutine can run. A
        // retransmitted SYN must observe it instead of starting a duplicate
        // outbound connection.
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var socket: Socket? = null
            try {
                /*
                 * This is the critical change:
                 * connector.connectTcp() now runs on Dispatchers.IO,
                 * never inside NetfetchTcpEngine's synchronized section.
                 */
                socket = connector.connectTcp(
                    destination = destination,
                    port = destinationPort
                )
                val activeSocket = socket

                activeSocket.receiveBufferSize = SOCKET_BUFFER_SIZE
                activeSocket.sendBufferSize = SOCKET_BUFFER_SIZE
                activeSocket.keepAlive = true

                runCatching {
                    socket.tcpNoDelay = true
                }

                val output = socket.getOutputStream()

                val queue =
                    ArrayBlockingQueue<ByteArray>(
                        WRITE_QUEUE_CAPACITY
                    )

                val session = Session(
                    key = key,
                    socket = socket,
                    output = output,
                    writeQueue = queue
                )

                val existing = sessions.putIfAbsent(key, session)
                if (existing != null) {
                    runCatching { socket.close() }
                    return@launch
                }

                connecting.remove(key)

                session.writerJob = scope.launch {
                    try {
                        while (!socket.isClosed) {
                            val data = queue.take()
                            output.write(data)
                        }
                    } catch (_: Exception) {
                        /*
                         * Reader/close path owns final cleanup.
                         */
                    }
                }

                session.readerJob = scope.launch {
                    val buffer = ByteArray(64 * 1024)

                    try {
                        val input =
                            socket.getInputStream()

                        while (!socket.isClosed) {
                            val count = input.read(buffer)

                            if (count < 0) {
                                break
                            }

                            if (count == 0) {
                                continue
                            }

                            onData(
                                key,
                                buffer.copyOf(count)
                            )
                        }
                    } catch (_: Exception) {
                        /*
                         * Session termination is handled below.
                         */
                    } finally {
                        sessions.remove(key, session)
                        connecting.remove(key)

                        session.writerJob.cancel()

                        runCatching {
                            socket.close()
                        }

                        onClosed(key)
                    }
                }

                // Publish the SYN-ACK only once both workers exist; an early
                // client payload can then never race a partially-built session.
                onConnected(key)

            } catch (_: Exception) {
                sessions.remove(key)
                connecting.remove(key)
                runCatching { socket?.close() }
                onConnectFailed(key)
            }
        }

        val existing =
            connecting.putIfAbsent(
                key,
                job
            )

        if (existing != null) {
            job.cancel()
            return true
        }

        try {
            job.start()
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            connecting.remove(key)
            onConnectFailed(key)
            return false
        }

        return true
    }

    fun write(
        key: TcpFlowKey,
        data: ByteArray
    ): Boolean {
        val session =
            sessions[key]
                ?: return false

        /*
         * Prefer a non-blocking offer first (fast path for the common case).
         *
         * If the queue is momentarily full — e.g. the upstream socket is
         * briefly behind — wait up to WRITE_OFFER_TIMEOUT_MS before giving
         * up. This absorbs micro-bursts without immediately tearing down the
         * session. Only a persistent stall (upstream truly stuck) will close.
         *
         * Never call socket.write() here: that would block the TUN reader
         * thread and stall every other active flow.
         */
        if (session.writeQueue.offer(data)) {
            return true
        }

        val accepted = try {
            session.writeQueue.offer(data, WRITE_OFFER_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!accepted) {
            close(key)
            return false
        }

        return true
    }

    fun close(
        key: TcpFlowKey
    ) {
        connecting.remove(key)?.cancel()

        val session =
            sessions.remove(key)
                ?: return

        session.readerJob.cancel()
        session.writerJob.cancel()

        session.writeQueue.clear()

        runCatching {
            session.socket.close()
        }
    }

    fun closeAll() {
        connecting.values
            .toList()
            .forEach { it.cancel() }

        connecting.clear()

        sessions.keys
            .toList()
            .forEach(::close)

        scope.cancel()
        runCatching { sessionExecutor.shutdownNow() }
    }

    fun count(): Int =
        sessions.size
}
