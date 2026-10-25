package com.netfetch.app.gateway.net

import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ArrayBlockingQueue
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Selector-driven UDP transport. Each queued item remains one datagram; queue
 * saturation drops new datagrams rather than blocking TUN packet processing.
 */
class NetfetchSelectorUdpSessionManager(
    private val connector: NetfetchSelectableUdpConnector
) : NetfetchUdpSessionTransport {

    private companion object {
        const val MAX_SESSIONS = 512
        const val DATAGRAM_QUEUE_CAPACITY = 64
        const val MAX_COMMANDS_PER_ITERATION = 256
        const val MAX_READS_PER_EVENT = 32
        const val MAX_WRITES_PER_EVENT = 32
        const val MAX_SELECTOR_EVENTS_PER_ITERATION = 256
        const val SELECT_TIMEOUT_MS = 100L
        const val MAX_DATAGRAM_SIZE = 65_507
        const val MAX_SESSION_BUFFER_BYTES = 256 * 1024
        const val MAX_TOTAL_BUFFER_BYTES = 16 * 1024 * 1024
        const val UDP_IDLE_TIMEOUT_MS = 120_000L
        const val TAG = "NetfetchUdpSelector"
    }

    private class Pending(
        val key: UdpFlowKey,
        val destination: InetAddress,
        val destinationPort: Int,
        val onData: (UdpFlowKey, ByteArray) -> Unit,
        val onClosed: (UdpFlowKey) -> Unit
    ) {
        val cancelled = AtomicBoolean(false)
        @Volatile var channel: DatagramChannel? = null
    }

    private class Session(
        val pending: Pending,
        val channel: DatagramChannel,
        val outgoing: DatagramQueue
    ) {
        val readBuffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE)
        var selectionKey: SelectionKey? = null
        @Volatile
        var currentWrite: ByteBuffer? = null
        @Volatile
        var lastActivityNanos: Long = System.nanoTime()
    }

    private class DatagramQueue(
        private val reserveGlobal: (Int) -> Boolean,
        private val releaseGlobal: (Int) -> Unit
    ) {
        private val lock = Any()
        private val packets = ArrayDeque<ByteArray>()
        private var bufferedBytes = 0
        private var closed = false

        fun offer(data: ByteArray): Boolean = synchronized(lock) {
            if (
                closed ||
                packets.size >= DATAGRAM_QUEUE_CAPACITY ||
                bufferedBytes + data.size > MAX_SESSION_BUFFER_BYTES ||
                !reserveGlobal(data.size)
            ) {
                return@synchronized false
            }
            val copy = data.copyOf()
            packets.addLast(copy)
            bufferedBytes += copy.size
            true
        }

        fun poll(): ByteArray? = synchronized(lock) {
            if (closed) null else packets.pollFirst()
        }

        fun isNotEmpty(): Boolean = synchronized(lock) {
            !closed && packets.isNotEmpty()
        }

        fun written(size: Int) = synchronized(lock) {
            if (!closed) {
                bufferedBytes -= size
                releaseGlobal(size)
            }
        }

        fun close() = synchronized(lock) {
            if (closed) return@synchronized
            closed = true
            packets.clear()
            releaseGlobal(bufferedBytes)
            bufferedBytes = 0
        }
    }

    private val selector = Selector.open()
    private val lifecycleLock = Any()
    private val pendingSessions = ConcurrentHashMap<UdpFlowKey, Pending>()
    private val sessions = ConcurrentHashMap<UdpFlowKey, Session>()
    private val commands = ArrayBlockingQueue<() -> Unit>(MAX_SESSIONS)
    private val totalBufferedBytes = AtomicLong()
    private val running = AtomicBoolean(true)
    private val selectorThread =
        Thread(::runSelector, "NetFetch-UdpSelector").apply {
            isDaemon = true
        }

    init {
        selectorThread.start()
    }

    override fun open(
        key: UdpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onData: (UdpFlowKey, ByteArray) -> Unit,
        onClosed: (UdpFlowKey) -> Unit
    ): Boolean {
        val pending = Pending(key, destination, destinationPort, onData, onClosed)
        synchronized(lifecycleLock) {
            if (!running.get()) return false
            if (sessions.containsKey(key) || pendingSessions.containsKey(key)) return true
            if (sessions.size + pendingSessions.size >= MAX_SESSIONS) return false
            pendingSessions[key] = pending
        }

        if (!commands.offer { establish(pending) }) {
            synchronized(lifecycleLock) {
                pendingSessions.remove(key, pending)
                pending.cancelled.set(true)
            }
            return false
        }
        selector.wakeup()
        return true
    }

    override fun send(key: UdpFlowKey, data: ByteArray): Boolean {
        if (data.isEmpty() || data.size > MAX_DATAGRAM_SIZE) return false
        val session = sessions[key] ?: return false
        if (!session.outgoing.offer(data)) return false
        session.lastActivityNanos = System.nanoTime()
        selector.wakeup()
        return true
    }

    override fun close(key: UdpFlowKey) {
        val (pending, session) = synchronized(lifecycleLock) {
            val connecting = pendingSessions.remove(key)
            connecting?.cancelled?.set(true)
            val active = sessions.remove(key)
            active?.outgoing?.close()
            pending to active
        }
        pending?.channel?.let { runCatching { it.close() } }
        session?.let {
            closeChannel(it)
            notifyClosed(it.pending)
        }
        selector.wakeup()
    }

    override fun closeAll() {
        closeSessions()
        if (!running.compareAndSet(true, false)) return
        selector.wakeup()
        if (Thread.currentThread() !== selectorThread) {
            runCatching { selectorThread.join(2_000L) }
        }
    }

    override fun closeSessions() {
        val (pending, active) = synchronized(lifecycleLock) {
            val pendingSnapshot = pendingSessions.values.toList()
            val activeSnapshot = sessions.values.toList()
            pendingSessions.clear()
            sessions.clear()
            pendingSnapshot.forEach { it.cancelled.set(true) }
            activeSnapshot.forEach { it.outgoing.close() }
            commands.clear()
            pendingSnapshot to activeSnapshot
        }
        pending.forEach { it.channel?.let { channel -> runCatching { channel.close() } } }
        active.forEach {
            closeChannel(it)
            notifyClosed(it.pending)
        }
        selector.wakeup()
    }

    override fun count(): Int = sessions.size

    private fun runSelector() {
        try {
            while (running.get()) {
                drainCommands()
                refreshWriteInterests()
                if (selector.selectedKeys().isEmpty()) {
                    selector.select(SELECT_TIMEOUT_MS)
                }
                val selected = selector.selectedKeys().iterator()
                var events = 0
                while (selected.hasNext() && events < MAX_SELECTOR_EVENTS_PER_ITERATION) {
                    val selectionKey = selected.next()
                    selected.remove()
                    events++
                    if (!selectionKey.isValid) continue
                    val session = selectionKey.attachment() as? Session ?: continue
                    try {
                        if (selectionKey.isReadable) readAvailable(session)
                        if (selectionKey.isValid && selectionKey.isWritable) writeAvailable(session)
                    } catch (error: Exception) {
                        Log.e(TAG, "UDP selector event failed; retiring the affected flow", error)
                        retire(session)
                    }
                }
                expireIdleSessions()
            }
        } catch (error: IOException) {
            if (running.get()) {
                Log.e(TAG, "UDP selector stopped unexpectedly", error)
                closeAll()
            }
        } catch (error: Exception) {
            if (running.get()) {
                Log.e(TAG, "UDP selector failed unexpectedly", error)
                closeAll()
            }
        } finally {
            running.set(false)
            closeSessions()
            runCatching { selector.close() }
        }
    }

    private fun drainCommands() {
        repeat(MAX_COMMANDS_PER_ITERATION) {
            val command = commands.poll() ?: return
            try {
                command()
            } catch (error: Exception) {
                Log.e(TAG, "UDP selector command failed", error)
            }
        }
    }

    private fun establish(pending: Pending) {
        if (!isCurrent(pending)) return
        var channel: DatagramChannel? = null
        try {
            channel = connector.openUdpChannel()
            channel.configureBlocking(false)
            pending.channel = channel
            if (!isCurrent(pending)) {
                channel.close()
                return
            }
            channel.connect(InetSocketAddress(pending.destination, pending.destinationPort))
            val session = Session(
                pending,
                channel,
                DatagramQueue(::reserveBytes, ::releaseBytes)
            )
            synchronized(lifecycleLock) {
                if (!isCurrentLocked(pending) || !channel.isOpen) {
                    channel.close()
                    return
                }
                val key = channel.register(selector, SelectionKey.OP_READ, session)
                session.selectionKey = key
                pendingSessions.remove(pending.key, pending)
                sessions[pending.key] = session
            }
        } catch (error: Exception) {
            runCatching { channel?.close() }
            val removed = synchronized(lifecycleLock) {
                pendingSessions.remove(pending.key, pending)
            }
            if (removed) {
                Log.e(TAG, "Unable to establish UDP flow", error)
                notifyClosed(pending)
            }
        }
    }

    private fun readAvailable(session: Session) {
        val buffer = session.readBuffer
        repeat(MAX_READS_PER_EVENT) {
            buffer.clear()
            val count = session.channel.read(buffer)
            if (count == 0) return
            buffer.flip()
            val data = ByteArray(count)
            buffer.get(data)
            session.lastActivityNanos = System.nanoTime()
            session.pending.onData(session.pending.key, data)
        }
    }

    private fun writeAvailable(session: Session) {
        for (write in 0 until MAX_WRITES_PER_EVENT) {
            val buffer = session.currentWrite
                ?: session.outgoing.poll()?.let(ByteBuffer::wrap)
                ?: break
            session.currentWrite = buffer
            val datagramSize = buffer.remaining()
            val written = session.channel.write(buffer)
            if (written == 0) return
            if (buffer.hasRemaining()) {
                throw IOException(
                    "Datagram channel consumed $written of $datagramSize bytes"
                )
            }
            session.currentWrite = null
            session.outgoing.written(datagramSize)
        }
        updateInterest(session)
    }

    private fun refreshWriteInterests() {
        sessions.values.forEach { session ->
            if (session.currentWrite == null && session.outgoing.isNotEmpty()) {
                try {
                    updateInterest(session)
                } catch (_: CancelledKeyException) {
                    // The flow is being retired by a concurrent close.
                }
            }
        }
    }

    private fun updateInterest(session: Session) {
        val key = session.selectionKey ?: return
        if (!key.isValid || sessions[session.pending.key] !== session) return
        val hasQueuedDatagrams = session.currentWrite != null || session.outgoing.isNotEmpty()
        key.interestOps(
            SelectionKey.OP_READ or (if (hasQueuedDatagrams) SelectionKey.OP_WRITE else 0)
        )
    }

    private fun retire(session: Session) {
        val removed = synchronized(lifecycleLock) {
            sessions.remove(session.pending.key, session)
        }
        closeChannel(session)
        if (removed) notifyClosed(session.pending)
    }

    private fun closeChannel(session: Session) {
        session.outgoing.close()
        session.currentWrite = null
        session.selectionKey?.cancel()
        runCatching { session.channel.close() }
    }

    private fun expireIdleSessions() {
        val cutoff = System.nanoTime() -
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(UDP_IDLE_TIMEOUT_MS)
        sessions.values
            .filter { it.lastActivityNanos <= cutoff }
            .forEach(::retire)
    }

    private fun reserveBytes(size: Int): Boolean {
        while (true) {
            val current = totalBufferedBytes.get()
            if (current + size > MAX_TOTAL_BUFFER_BYTES) return false
            if (totalBufferedBytes.compareAndSet(current, current + size)) return true
        }
    }

    private fun releaseBytes(size: Int) {
        if (size > 0) totalBufferedBytes.addAndGet(-size.toLong())
    }

    private fun isCurrent(pending: Pending): Boolean =
        synchronized(lifecycleLock) { isCurrentLocked(pending) }

    private fun isCurrentLocked(pending: Pending): Boolean =
        running.get() &&
            !pending.cancelled.get() &&
            pendingSessions[pending.key] === pending

    private fun notifyClosed(pending: Pending) {
        try {
            pending.onClosed(pending.key)
        } catch (error: Exception) {
            Log.e(TAG, "UDP flow cleanup callback failed", error)
        }
    }
}
