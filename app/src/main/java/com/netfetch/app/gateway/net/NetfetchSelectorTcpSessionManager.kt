package com.netfetch.app.gateway.net

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.CancelledKeyException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Selector-driven TCP socket transport. TCP packet sequencing and retransmission
 * remain owned by [NetfetchTcpEngine]; this class only manages protected channels.
 */
class NetfetchSelectorTcpSessionManager(
    private val connector: NetfetchSelectableConnector
) : NetfetchTcpSessionTransport {

    private companion object {
        const val WRITE_QUEUE_CAPACITY = 256
        const val READ_BUFFER_SIZE = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val SELECT_TIMEOUT_MS = 100L
        const val MAX_COMMANDS_PER_ITERATION = 256
        const val MAX_READS_PER_EVENT = 16
        const val MAX_SELECTOR_EVENTS_PER_ITERATION = 256
        const val MAX_SESSIONS = 2048
        const val MAX_PENDING_COMMANDS = 2048
    }

    private class Pending(
        val key: TcpFlowKey,
        val onConnected: (TcpFlowKey) -> Unit,
        val onConnectFailed: (TcpFlowKey) -> Unit,
        val onData: (TcpFlowKey, ByteArray) -> Unit,
        val onClosed: (TcpFlowKey) -> Unit,
        val onWriteQueueAvailable: (TcpFlowKey) -> Unit,
        val destination: InetAddress,
        val destinationPort: Int,
        @Volatile var deadlineNanos: Long
    ) {
        val cancelled = AtomicBoolean(false)
        @Volatile var channel: SocketChannel? = null
        var handshake: NetfetchTcpChannelHandshake? = null
    }

    private class Session(
        val pending: Pending,
        val channel: SocketChannel,
        val writeQueue: BoundedTcpWriteQueue
    ) {
        val readBuffer: ByteBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE)
        var selectionKey: SelectionKey? = null
        var currentWrite: ByteBuffer? = null
    }

    private val selector = Selector.open()
    private val lifecycleLock = Any()
    private val sessions = ConcurrentHashMap<TcpFlowKey, Session>()
    private val connecting = ConcurrentHashMap<TcpFlowKey, Pending>()
    private val commands = ArrayBlockingQueue<() -> Unit>(MAX_PENDING_COMMANDS)
    private val running = AtomicBoolean(true)
    private val selectorThread = Thread(::runSelector, "NetFetch-TcpSelector").apply {
        isDaemon = true
    }

    init {
        selectorThread.start()
    }

    override fun open(
        key: TcpFlowKey,
        destination: InetAddress,
        destinationPort: Int,
        onConnected: (TcpFlowKey) -> Unit,
        onConnectFailed: (TcpFlowKey) -> Unit,
        onData: (TcpFlowKey, ByteArray) -> Unit,
        onClosed: (TcpFlowKey) -> Unit,
        onWriteQueueAvailable: (TcpFlowKey) -> Unit
    ): Boolean {
        val pending = Pending(
            key = key,
            onConnected = onConnected,
            onConnectFailed = onConnectFailed,
            onData = onData,
            onClosed = onClosed,
            onWriteQueueAvailable = onWriteQueueAvailable,
            destination = destination,
            destinationPort = destinationPort,
            deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS)
        )

        val registration = synchronized(lifecycleLock) {
            when {
                !running.get() -> 3
                sessions.containsKey(key) -> 1
                connecting.containsKey(key) -> 2
                sessions.size + connecting.size >= MAX_SESSIONS -> 4
                else -> {
                    connecting[key] = pending
                    0
                }
            }
        }
        when (registration) {
            1 -> {
                onConnected(key)
                return true
            }
            2 -> return true
            3 -> {
                onConnectFailed(key)
                return false
            }
            4 -> {
                onConnectFailed(key)
                return false
            }
        }

        if (!commands.offer(command@{
            if (!isCurrent(pending)) return@command
            var openedChannel: SocketChannel? = null
            try {
                val channel = connector.openTcpChannel()
                openedChannel = channel
                channel.configureBlocking(false)
                pending.channel = channel
                if (!isCurrent(pending)) {
                    channel.close()
                    return@command
                }

                val connectedImmediately =
                    channel.connect(
                        connector.tcpConnectAddress(destination, destinationPort)
                    )
                if (connectedImmediately) {
                    startHandshake(pending, channel)
                } else {
                    synchronized(lifecycleLock) {
                        if (!isCurrentLocked(pending)) {
                            channel.close()
                            return@synchronized
                        }
                        channel.register(selector, SelectionKey.OP_CONNECT, pending)
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e(
                    "NetfetchTcpSelector",
                    "Unable to start TCP channel connection",
                    error
                )
                runCatching { openedChannel?.close() }
                fail(pending)
            }
        })) {
            fail(pending)
            return false
        }
        selector.wakeup()
        return true
    }

    override fun write(
        key: TcpFlowKey,
        data: ByteArray
    ): NetfetchTcpSessionManager.WriteResult {
        val session = sessions[key] ?: return NetfetchTcpSessionManager.WriteResult.NO_SESSION
        if (!session.writeQueue.offer(data)) {
            return if (sessions[key] === session) {
                NetfetchTcpSessionManager.WriteResult.QUEUE_FULL
            } else {
                NetfetchTcpSessionManager.WriteResult.NO_SESSION
            }
        }
        selector.wakeup()
        return NetfetchTcpSessionManager.WriteResult.ACCEPTED
    }

    override fun close(key: TcpFlowKey) {
        val (pending, session) = synchronized(lifecycleLock) {
            val pendingSession = connecting.remove(key)
            pendingSession?.cancelled?.set(true)
            val activeSession = sessions.remove(key)
            activeSession?.writeQueue?.close()
            pendingSession to activeSession
        }
        pending?.channel?.let { runCatching { it.close() } }
        session?.let(::closeChannel)
        selector.wakeup()
    }

    override fun closeSessions() {
        val (pendingSessions, activeSessions) = synchronized(lifecycleLock) {
            val pendingSnapshot = connecting.values.toList()
            val activeSnapshot = sessions.values.toList()
            connecting.clear()
            sessions.clear()
            pendingSnapshot.forEach { it.cancelled.set(true) }
            activeSnapshot.forEach { it.writeQueue.close() }
            commands.clear()
            pendingSnapshot to activeSnapshot
        }
        pendingSessions.forEach { it.channel?.let { channel -> runCatching { channel.close() } } }
        activeSessions.forEach(::closeChannel)
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

    override fun count(): Int = sessions.size

    override fun isConnecting(key: TcpFlowKey): Boolean = connecting.containsKey(key)

    override fun availableReceiveWindow(key: TcpFlowKey): Int {
        val session = sessions[key] ?: return 65_535
        return (session.writeQueue.remainingCapacity() * 1460).coerceIn(0, 65_535)
    }

    private fun runSelector() {
        try {
            while (running.get()) {
                drainCommands()
                sessions.values.forEach { session ->
                    try {
                        updateInterest(session)
                    } catch (_: CancelledKeyException) {
                        // The flow is being retired by a concurrent close.
                    }
                }
                if (selector.selectedKeys().isEmpty()) {
                    selector.select(SELECT_TIMEOUT_MS)
                }
                val iterator = selector.selectedKeys().iterator()
                var events = 0
                while (iterator.hasNext() && events < MAX_SELECTOR_EVENTS_PER_ITERATION) {
                    val key = iterator.next()
                    iterator.remove()
                    events++
                    if (!key.isValid) continue
                    try {
                        when {
                            key.isConnectable -> finishConnect(key)
                            key.attachment() is Pending -> {
                                val pending = key.attachment() as Pending
                                if (key.isReadable || key.isWritable) {
                                    advanceHandshake(pending, key)
                                }
                            }
                            key.attachment() is Session -> {
                                val session = key.attachment() as Session
                                if (key.isReadable) read(session)
                                if (key.isValid && key.isWritable) writeAvailable(session)
                            }
                        }
                    } catch (error: Exception) {
                        android.util.Log.e(
                            "NetfetchTcpSelector",
                            "TCP selector event failed; retiring the affected session",
                            error
                        )
                        when (val attachment = key.attachment()) {
                            is Session -> retire(attachment, notifyClosed = true)
                            is Pending -> fail(attachment)
                        }
                    }
                }
                expirePendingConnections()
            }
        } catch (error: IOException) {
            android.util.Log.e("NetfetchTcpSelector", "TCP selector stopped unexpectedly", error)
            if (running.get()) closeAll()
        } catch (error: Exception) {
            android.util.Log.e("NetfetchTcpSelector", "TCP selector failed unexpectedly", error)
            if (running.get()) closeAll()
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
                android.util.Log.e("NetfetchTcpSelector", "TCP selector command failed", error)
            }
        }
    }

    private fun finishConnect(selectionKey: SelectionKey) {
        val pending = selectionKey.attachment() as Pending
        val channel = pending.channel ?: return fail(pending)
        if (!channel.finishConnect()) return
        startHandshake(pending, channel)
    }

    private fun startHandshake(pending: Pending, channel: SocketChannel) {
        if (!isCurrent(pending)) {
            runCatching { channel.close() }
            return
        }
        val handshake = connector.createTcpHandshake(
            pending.destination,
            pending.destinationPort
        )
        if (handshake == null) {
            establish(pending, channel)
            return
        }
        pending.handshake = handshake
        pending.deadlineNanos =
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(handshake.timeoutMillis)
        advanceHandshake(pending, channel.keyFor(selector))
    }

    private fun advanceHandshake(pending: Pending, selectionKey: SelectionKey?) {
        val channel = pending.channel ?: return fail(pending)
        val handshake = pending.handshake ?: return fail(pending)
        try {
            if (handshake.advance(channel)) {
                establish(pending, channel)
            } else {
                val key = selectionKey ?: channel.register(
                    selector,
                    handshake.interestOps,
                    pending
                )
                key.attach(pending)
                key.interestOps(handshake.interestOps)
            }
        } catch (error: Exception) {
            android.util.Log.e(
                "NetfetchTcpSelector",
                "TCP channel negotiation failed",
                error
            )
            fail(pending)
        }
    }

    private fun establish(pending: Pending, channel: SocketChannel) {
        val session = Session(
            pending = pending,
            channel = channel,
            writeQueue = BoundedTcpWriteQueue(WRITE_QUEUE_CAPACITY)
        )
        val published = synchronized(lifecycleLock) {
            if (!isCurrentLocked(pending) || !channel.isOpen) {
                false
            } else {
                val selectionKey = channel.keyFor(selector)
                    ?: channel.register(selector, SelectionKey.OP_READ, session)
                selectionKey.attach(session)
                selectionKey.interestOps(SelectionKey.OP_READ)
                session.selectionKey = selectionKey
                connecting.remove(pending.key, pending)
                sessions[pending.key] = session
                true
            }
        }
        if (!published) {
            runCatching { channel.close() }
            return
        }
        try {
            pending.onConnected(pending.key)
        } catch (error: Exception) {
            android.util.Log.e("NetfetchTcpSelector", "TCP connected callback failed", error)
            retire(session, notifyClosed = true)
        }
    }

    private fun read(session: Session) {
        repeat(MAX_READS_PER_EVENT) {
            session.readBuffer.clear()
            val count = session.channel.read(session.readBuffer)
            if (count < 0) {
                return retire(session, notifyClosed = true)
            }
            if (count == 0) return
            val data = ByteArray(count)
            session.readBuffer.flip()
            session.readBuffer.get(data)
            session.pending.onData(session.pending.key, data)
        }
    }

    private fun writeAvailable(session: Session) {
        var writes = 0
        while (writes < 16) {
            var current = session.currentWrite
            if (current == null) {
                val dequeued = session.writeQueue.poll(timeoutMillis = 0L)
                    ?: break
                current = ByteBuffer.wrap(dequeued.data)
                session.currentWrite = current
                if (dequeued.wasFull) {
                    session.pending.onWriteQueueAvailable(session.pending.key)
                }
            }

            val count = session.channel.write(current)
            if (count == 0) break
            if (!current.hasRemaining()) {
                session.currentWrite = null
            } else {
                break
            }
            writes++
        }
        updateInterest(session)
    }

    private fun updateInterest(session: Session) {
        val selectionKey = session.selectionKey ?: return
        if (!selectionKey.isValid || sessions[session.pending.key] !== session) return
        val hasPendingWrites =
            session.currentWrite != null ||
                session.writeQueue.remainingCapacity() < WRITE_QUEUE_CAPACITY
        selectionKey.interestOps(
            SelectionKey.OP_READ or (if (hasPendingWrites) SelectionKey.OP_WRITE else 0)
        )
    }

    private fun expirePendingConnections() {
        val now = System.nanoTime()
        connecting.values
            .filter { now >= it.deadlineNanos }
            .forEach(::fail)
    }

    private fun fail(pending: Pending) {
        val removed = synchronized(lifecycleLock) {
            pending.cancelled.set(true)
            connecting.remove(pending.key, pending)
        }
        pending.channel?.let { runCatching { it.close() } }
        if (removed) {
            try {
                pending.onConnectFailed(pending.key)
            } catch (error: Exception) {
                android.util.Log.e("NetfetchTcpSelector", "TCP connect failure callback failed", error)
            }
        }
    }

    private fun retire(session: Session, notifyClosed: Boolean) {
        val removed = synchronized(lifecycleLock) {
            sessions.remove(session.pending.key, session)
        }
        closeChannel(session)
        if (removed && notifyClosed) {
            try {
                session.pending.onClosed(session.pending.key)
            } catch (error: Exception) {
                android.util.Log.e("NetfetchTcpSelector", "TCP close callback failed", error)
            }
        }
    }

    private fun closeChannel(session: Session) {
        session.writeQueue.close()
        session.selectionKey?.cancel()
        runCatching { session.channel.close() }
    }

    private fun isCurrent(pending: Pending): Boolean =
        synchronized(lifecycleLock) { isCurrentLocked(pending) }

    private fun isCurrentLocked(pending: Pending): Boolean =
        !pending.cancelled.get() && connecting[pending.key] === pending
}
