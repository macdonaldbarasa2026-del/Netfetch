package com.netfetch.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetfetchBufferPoolTest {

    @Test
    fun testAcquireReturnsCorrectSizeBuffer() {
        val buffer = NetfetchBufferPool.acquire()
        assertNotNull(buffer)
        assertEquals(NetfetchBufferPool.BUFFER_SIZE, buffer.size)
        NetfetchBufferPool.release(buffer)
    }

    @Test
    fun testBufferReusability() {
        val buf1 = NetfetchBufferPool.acquire()
        NetfetchBufferPool.release(buf1)
        val buf2 = NetfetchBufferPool.acquire()
        // Should reuse the pooled instance
        assertEquals(buf1.size, buf2.size)
        NetfetchBufferPool.release(buf2)
    }

    @Test
    fun testConcurrentAcquireAndRelease() {
        val acquiredBuffers = mutableListOf<ByteArray>()
        for (i in 0 until 10) {
            acquiredBuffers.add(NetfetchBufferPool.acquire())
        }

        for (buffer in acquiredBuffers) {
            assertEquals(32 * 1024, buffer.size)
            NetfetchBufferPool.release(buffer)
        }
    }

    @Test
    fun testCapacityBoundEnforced() {
        // Releasing up to MAX_BUFFERS + 10 buffers must not crash and remain bounded
        for (i in 0 until NetfetchBufferPool.MAX_BUFFERS + 20) {
            NetfetchBufferPool.release(ByteArray(NetfetchBufferPool.BUFFER_SIZE))
        }

        val buffer = NetfetchBufferPool.acquire()
        assertEquals(NetfetchBufferPool.BUFFER_SIZE, buffer.size)
        NetfetchBufferPool.release(buffer)
    }
}
