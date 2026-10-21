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
            window = 65535
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
            window = 65535
        )

        val dummyPacket = ByteArray(40)
        engine.handle(dummyPacket, ip, tcpSyn)

        // Flow should be created in flows map while upstream connect is underway
        assertEquals(1, engine.count())

        engine.shutdown()
    }

    @Test
    fun testComputedHeaderLengthParsing() {
        val ipPacket = PacketCodec.ipv4Tcp(
            sourceIp = 0x7f000001,
            destinationIp = 0x7f000002,
            sourcePort = 80,
            destinationPort = 1234,
            sequence = 1000L,
            acknowledgement = 2000L,
            flags = TcpHeader.ACK,
            window = 65535,
            payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )

        val ip = Ipv4Header.parse(ipPacket, ipPacket.size)
        assertNotNull(ip)
        assertEquals(20, ip!!.headerLength)

        val tcp = TcpHeader.parse(ipPacket, ip.headerLength, ipPacket.size - ip.headerLength)
        assertNotNull(tcp)
        assertEquals(20, tcp!!.dataOffset)

        val computedHeaderLen = ip.headerLength + tcp.dataOffset
        assertEquals(40, computedHeaderLen)
        assertEquals(8, ipPacket.size - computedHeaderLen)
    }
}
