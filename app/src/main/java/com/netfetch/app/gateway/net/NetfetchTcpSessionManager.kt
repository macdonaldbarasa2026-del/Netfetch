package com.netfetch.app.gateway.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns real upstream TCP sockets created for TUN flows.
 *
 * The manager deliberately does not silently fall back to the default
 * Android network. Every connection must use NetfetchDirectConnector.
 */
class NetfetchTcpSessionManager(
    private val connector: NetfetchDirectConnector
) {

    private data class Session(
        val key: TcpFlowKey,
        val socket: Socket,
        val output: java.io.OutputStream,
        val job: Job
    )

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessions =
        ConcurrentHashMap<TcpFlowKey, Session>()

    fun open(
        key: TcpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onData: (TcpFlowKey, ByteArray) -> Unit,
        onClosed: (TcpFlowKey) -> Unit
    ): Boolean {
        if (sessions.containsKey(key)) {
            return true
        }

        return try {
            val socket = connector.connectTcp(
                destination = destination,
                port = destinationPort
            )

            // Larger buffers help sustain high-throughput transfers through
            // the userspace TUN gateway.
            socket.receiveBufferSize = 256 * 1024
            socket.sendBufferSize = 256 * 1024
            socket.keepAlive = true

            val output = socket.getOutputStream()

            val job = scope.launch {
                val buffer = ByteArray(64 * 1024)

                try {
                    val input = socket.getInputStream()

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
                    // Session termination is handled below.
                } finally {
                    sessions.remove(key)
                    runCatching { socket.close() }
                    onClosed(key)
                }
            }

            val session = Session(
                key = key,
                socket = socket,
                output = output,
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

    fun write(
        key: TcpFlowKey,
        data: ByteArray
    ): Boolean {
        val session = sessions[key] ?: return false

        return try {
            session.output.write(data)
            true
        } catch (_: Exception) {
            close(key)
            false
        }
    }

    fun close(key: TcpFlowKey) {
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
