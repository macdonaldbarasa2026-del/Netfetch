package com.netfetch.app.gateway.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class NetfetchTcpEngineTest {

    private val writtenPackets = CopyOnWriteArrayList<ByteArray>()

    private val mockConnector = object : NetfetchConnector {
        override fun connectTcp(destination: InetAddress, port: Int, timeoutMs: Int): Socket {
            throw java.io.IOException("Test mock connection rejected")
        }

        override fun openUdp(): DatagramSocket {
            throw UnsupportedOperationException("Not implemented")
        }
    }

    @Test
    fun testInitialStateAndShutdown() {
        val engine = NetfetchTcpEngine(mockConnector) { packet ->
            writtenPackets.add(packet)
        }

        assertEquals(0, engine.count())
        engine.shutdown()
    }

    @Test
    fun testHandleRstCleansUpFlow() {
        val engine = NetfetchTcpEngine(mockConnector) { packet ->
            writtenPackets.add(packet)
        }

        val ip = Ipv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 40,
            protocol = PROTO_TCP,
            sourceIp = 0x7f000001,
            destinationIp = 0x7f000002
        )

        val tcpRst = TcpHeader(
            sourcePort = 1234,
            destinationPort = 80,
            sequence = 100L,
            acknowledgement = 0L,
            dataOffset = 20,
            flags = TcpHeader.RST,
            window = 65535,
            urgentPointer = 0
        )

        val dummyPacket = ByteArray(40)
        engine.handle(dummyPacket, ip, tcpRst)
        assertEquals(0, engine.count())
        engine.shutdown()
    }

    @Test
    fun testHandleSynInitiatesFlow() {
        val engine = NetfetchTcpEngine(mockConnector) { packet ->
            writtenPackets.add(packet)
        }

        val ip = Ipv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 40,
            protocol = PROTO_TCP,
            sourceIp = 0x7f000001,
            destinationIp = 0x7f000002
        )

        val tcpSyn = TcpHeader(
            sourcePort = 1234,
            destinationPort = 80,
            sequence = 100L,
            acknowledgement = 0L,
            dataOffset = 20,
            flags = TcpHeader.SYN,
            window = 65535,
            urgentPointer = 0
        )

        val dummyPacket = ByteArray(40)
        engine.handle(dummyPacket, ip, tcpSyn)

        // Flow should be created in flows map while upstream connect is underway
        assertEquals(1, engine.count())

        engine.shutdown()
    }
}
