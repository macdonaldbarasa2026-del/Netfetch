package com.netfetch.app.network

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance, zero-garbage byte buffer pool.
 *
 * Avoids continuous GC churn and memory fragmentation on Android
 * during high-speed proxy and TUN stream forwarding.
 */
object NetfetchBufferPool {

    const val DEFAULT_BUFFER_SIZE = 32 * 1024 // 32 KB per chunk
    private const val MAX_POOL_CAPACITY = 128   // Max 4 MB pooled memory

    private val pool = ArrayBlockingQueue<ByteArray>(MAX_POOL_CAPACITY)
    private val allocatedCount = AtomicInteger(0)

    /**
     * Obtains a 32 KB buffer from the pool, or allocates a new one if pool is empty.
     */
    fun acquire(size: Int = DEFAULT_BUFFER_SIZE): ByteArray {
        if (size == DEFAULT_BUFFER_SIZE) {
            val pooled = pool.poll()
            if (pooled != null) {
                return pooled
            }
        }
        allocatedCount.incrementAndGet()
        return ByteArray(size)
    }

    /**
     * Returns a buffer to the pool for reuse if capacity allows.
     */
    fun release(buffer: ByteArray?) {
        if (buffer != null && buffer.size == DEFAULT_BUFFER_SIZE) {
            pool.offer(buffer)
        }
    }

    /**
     * Executes the given block with a pooled buffer, guaranteeing its release.
     */
    inline fun <T> useBuffer(size: Int = DEFAULT_BUFFER_SIZE, block: (ByteArray) -> T): T {
        val buffer = acquire(size)
        return try {
            block(buffer)
        } finally {
            release(buffer)
        }
    }

    val pooledCount: Int
        get() = pool.size

    val totalAllocated: Int
        get() = allocatedCount.get()

    fun clear() {
        pool.clear()
    }
}
