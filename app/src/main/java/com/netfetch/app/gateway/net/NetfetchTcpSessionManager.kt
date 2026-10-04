package com.netfetch.app.gateway.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap

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

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            try {
                /*
                 * This is the critical change:
                 * connector.connectTcp() now runs on Dispatchers.IO,
                 * never inside NetfetchTcpEngine's synchronized section.
                 */
                val socket = connector.connectTcp(
                    destination = destination,
                    port = destinationPort
                )

                socket.receiveBufferSize = SOCKET_BUFFER_SIZE
                socket.sendBufferSize = SOCKET_BUFFER_SIZE
                socket.keepAlive = true

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
                connecting.remove(key)
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

        job.start()

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
         * Never perform socket.write() here.
         *
         * The bounded queue provides backpressure and keeps packet
         * processing responsive.
         */
        val accepted =
            session.writeQueue.offer(
                data
            )

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
    }

    fun count(): Int =
        sessions.size
}
