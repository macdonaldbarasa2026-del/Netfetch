package com.netfetch.app.gateway.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class BoundedTcpWriteQueue(
    private val capacity: Int
) {
    data class Dequeued(
        val data: ByteArray,
        val wasFull: Boolean
    )

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val entries = ArrayDeque<ByteArray>()
    private var closed = false

    fun offer(data: ByteArray): Boolean = lock.withLock {
        if (closed || entries.size == capacity) {
            return false
        }

        entries.addLast(data)
        notEmpty.signal()
        true
    }

    @Throws(InterruptedException::class)
    fun poll(timeoutMillis: Long): Dequeued? = lock.withLock {
        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (entries.isEmpty() && !closed && remainingNanos > 0L) {
            remainingNanos = notEmpty.awaitNanos(remainingNanos)
        }

        if (entries.isEmpty()) {
            return null
        }

        val wasFull = entries.size == capacity
        Dequeued(entries.removeFirst(), wasFull)
    }

    fun remainingCapacity(): Int = lock.withLock {
        capacity - entries.size
    }

    fun close() {
        lock.withLock {
            closed = true
            entries.clear()
            notEmpty.signalAll()
        }
    }
}

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
) : NetfetchTcpSessionTransport {

    enum class WriteResult {
        ACCEPTED,
        QUEUE_FULL,
        NO_SESSION
    }

    private companion object {
        const val WRITE_QUEUE_CAPACITY = 256
        const val SOCKET_BUFFER_SIZE = 256 * 1024
    }

    private class Session(
        val key: TcpFlowKey,
        val socket: Socket,
        val output: java.io.OutputStream,
        val writeQueue: BoundedTcpWriteQueue
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
    private val lifecycleLock = Any()

    override fun open(
        key: TcpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onConnected: (TcpFlowKey) -> Unit,
        onConnectFailed: (TcpFlowKey) -> Unit,
        onData: (TcpFlowKey, ByteArray) -> Unit,
        onClosed: (TcpFlowKey) -> Unit,
        onWriteQueueAvailable: (TcpFlowKey) -> Unit = {}
    ): Boolean {

        // Register the connecting marker before this coroutine can run. A
        // retransmitted SYN must observe it instead of starting a duplicate
        // outbound connection.
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var socket: Socket? = null
            var session: Session? = null
            val currentJob = currentCoroutineContext()[Job]
            try {
                /*
                 * This is the critical change:
                 * connector.connectTcp() now runs on Dispatchers.IO,
                 * never inside NetfetchTcpEngine's synchronized section.
                 */
                val activeSocket = connector.connectTcp(
                    destination = destination,
                    port = destinationPort
                )
                socket = activeSocket
                currentCoroutineContext().ensureActive()

                activeSocket.receiveBufferSize = SOCKET_BUFFER_SIZE
                activeSocket.sendBufferSize = SOCKET_BUFFER_SIZE
                activeSocket.keepAlive = true

                runCatching {
                    activeSocket.tcpNoDelay = true
                }

                val output = activeSocket.getOutputStream()
                val writeQueue = BoundedTcpWriteQueue(WRITE_QUEUE_CAPACITY)
                val newSession = Session(
                    key = key,
                    socket = activeSocket,
                    output = output,
                    writeQueue = writeQueue
                )
                session = newSession

                newSession.writerJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        while (isActive && !activeSocket.isClosed) {
                            val dequeued = writeQueue.poll(100L) ?: continue
                            output.write(dequeued.data)
                            if (dequeued.wasFull) {
                                onWriteQueueAvailable(key)
                            }
                            if (writeQueue.remainingCapacity() == WRITE_QUEUE_CAPACITY) {
                                output.flush()
                            }
                        }
                    } catch (_: Exception) {
                        /*
                         * Reader/close path owns final cleanup.
                         */
                    }
                }

                newSession.readerJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    val buffer = ByteArray(64 * 1024)

                    try {
                        val input = activeSocket.getInputStream()

                        while (!activeSocket.isClosed) {
                            val count = input.read(buffer)

                            if (count < 0) {
                                break
                            }

                            if (count == 0) {
                                continue
                            }

                            onData(key, buffer.copyOf(count))
                        }
                    } catch (_: Exception) {
                        /*
                         * Session termination is handled below.
                         */
                    } finally {
                        synchronized(lifecycleLock) {
                            sessions.remove(key, newSession)
                        }
                        newSession.writeQueue.close()
                        newSession.writerJob.cancel()
                        runCatching { activeSocket.close() }
                        onClosed(key)
                    }
                }

                val published = synchronized(lifecycleLock) {
                    if (
                        currentJob?.isActive == true &&
                        connecting[key] === currentJob &&
                        sessions.putIfAbsent(key, newSession) == null
                    ) {
                        connecting.remove(key, currentJob)
                        true
                    } else {
                        false
                    }
                }
                if (!published) {
                    newSession.writeQueue.close()
                    newSession.readerJob.cancel()
                    newSession.writerJob.cancel()
                    runCatching { activeSocket.close() }
                    return@launch
                }

                newSession.writerJob.start()
                newSession.readerJob.start()

                // Publish the SYN-ACK only once both workers exist; an early
                // client payload can then never race a partially-built session.
                onConnected(key)

            } catch (_: Exception) {
                val removed = synchronized(lifecycleLock) {
                    val removedConnecting = currentJob != null &&
                        connecting.remove(key, currentJob)
                    val removedSession = session?.let { sessions.remove(key, it) } == true
                    session?.writeQueue?.close()
                    removedConnecting || removedSession
                }
                session?.readerJob?.cancel()
                session?.writerJob?.cancel()
                runCatching { socket?.close() }
                if (removed && currentJob?.isActive == true) {
                    onConnectFailed(key)
                }
            }
        }

        val registration = synchronized(lifecycleLock) {
            when {
                sessions.containsKey(key) -> 1
                connecting.containsKey(key) -> 2
                else -> {
                    connecting[key] = job
                    0
                }
            }
        }
        if (registration != 0) {
            job.cancel()
            if (registration == 1) {
                onConnected(key)
            }
            return true
        }

        try {
            job.start()
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            val removed = synchronized(lifecycleLock) {
                connecting.remove(key, job)
            }
            if (removed) {
                onConnectFailed(key)
            }
            return false
        }

        return true
    }

    override fun write(
        key: TcpFlowKey,
        data: ByteArray
    ): WriteResult {
        val session = sessions[key] ?: return WriteResult.NO_SESSION

        /*
         * Pure non-blocking offer:
         * Never block while holding NetfetchTcpEngine's synchronized lock.
         * A full queue is backpressure, not a reason to discard the session.
         */
        val accepted = session.writeQueue.offer(data)
        if (accepted) {
            return WriteResult.ACCEPTED
        }
        return if (sessions[key] === session) {
            WriteResult.QUEUE_FULL
        } else {
            WriteResult.NO_SESSION
        }
    }

    override fun close(
        key: TcpFlowKey
    ) {
        val (connectingJob, session) = synchronized(lifecycleLock) {
            val job = connecting.remove(key)
            val removedSession = sessions.remove(key)
            removedSession?.writeQueue?.close()
            job to removedSession
        }
        connectingJob?.cancel()
        session?.readerJob?.cancel()
        session?.writerJob?.cancel()
        session?.let { runCatching { it.socket.close() } }
    }

    override fun closeAll() {
        closeSessions()

        // Cancel and join children before cancelling root scope and shutting down executor
        val rootJob = scope.coroutineContext[Job]
        val children = rootJob?.children?.toList() ?: emptyList()
        children.forEach { it.cancel() }

        runBlocking {
            withTimeoutOrNull(2000L) {
                children.forEach { runCatching { it.join() } }
            }
        }

        scope.cancel()
        runCatching { sessionExecutor.shutdownNow() }
    }

    override fun closeSessions() {
        val (jobs, retiredSessions) = synchronized(lifecycleLock) {
            val connectingJobs = connecting.values.toList()
            val currentSessions = sessions.values.toList()
            connecting.clear()
            sessions.clear()
            currentSessions.forEach { it.writeQueue.close() }
            connectingJobs to currentSessions
        }
        jobs.forEach { it.cancel() }
        retiredSessions.forEach { session ->
            session.readerJob.cancel()
            session.writerJob.cancel()
            runCatching { session.socket.close() }
        }
    }

    override fun count(): Int =
        sessions.size

    override fun isConnecting(key: TcpFlowKey): Boolean =
        connecting.containsKey(key)

    override fun availableReceiveWindow(key: TcpFlowKey): Int {
        val session = sessions[key] ?: return 65_535
        val remaining = session.writeQueue.remainingCapacity()
        // Each entry in writeQueue holds at most one client TCP payload segment (up to 1460 bytes MSS).
        // The advertised receive window accurately reflects the buffer space available in writeQueue
        // (bounded by the 16-bit max TCP window of 65,535 bytes).
        // This provides proper TCP flow control: when the queue is nearly full, the advertised window
        // shrinks proportionally to prevent packet drops and RST disconnects.
        return (remaining * 1460).coerceIn(0, 65_535)
    }
}
