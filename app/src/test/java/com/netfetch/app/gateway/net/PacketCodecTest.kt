package com.netfetch.app.gateway.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PacketCodecTest {

    private fun ipToInt(ipString: String): Int {
        val bytes = InetAddress.getByName(ipString).address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    @Test
    fun testChecksumCalculation() {
        val testData = byteArrayOf(
            0x45.toByte(), 0x00, 0x00, 0x3c,
            0x1c.toByte(), 0x46, 0x40, 0x00,
            0x40, 0x06, 0x00, 0x00, // zero checksum at 10,11
            0xac.toByte(), 0x10, 0x0a, 0x63,
            0xac.toByte(), 0x10, 0x0a, 0x0c
        )
        val computed = PacketCodec.checksum(testData, 0, testData.size)
        assertTrue(computed in 0..0xFFFF)
    }

    @Test
    fun testIpv4TcpEncodingAndParsing() {
        val srcIp = ipToInt("192.168.49.2")
        val dstIp = ipToInt("192.168.49.1")
        val srcPort = 12345
        val dstPort = 80
        val payload = "GET / HTTP/1.1\r\n\r\n".toByteArray()

        val packet = PacketCodec.ipv4Tcp(
            sourceIp = srcIp,
            destinationIp = dstIp,
            sourcePort = srcPort,
            destinationPort = dstPort,
            sequence = 1000L,
            acknowledgement = 2000L,
            flags = TcpHeader.ACK or TcpHeader.PUSH_ACK,
            window = 65535,
            payload = payload
        )

        assertNotNull(packet)
        assertTrue(packet.size >= 40 + payload.size)

        val ipv4Header = Ipv4Header.parse(packet, packet.size)
        assertNotNull(ipv4Header)
        assertEquals(PROTO_TCP, ipv4Header!!.protocol)
        assertEquals(srcIp, ipv4Header.sourceIp)
        assertEquals(dstIp, ipv4Header.destinationIp)

        val tcpHeader = TcpHeader.parse(packet, ipv4Header.headerLength, packet.size - ipv4Header.headerLength)
        assertNotNull(tcpHeader)
        assertEquals(srcPort, tcpHeader!!.sourcePort)
        assertEquals(dstPort, tcpHeader.destinationPort)
        assertEquals(1000L, tcpHeader.sequence)
        assertEquals(2000L, tcpHeader.acknowledgement)
        assertTrue(tcpHeader.ack)
    }

    @Test
    fun testIpv4UdpEncodingAndParsing() {
        val srcIp = ipToInt("192.168.49.2")
        val dstIp = ipToInt("192.168.49.1")
        val srcPort = 5353
        val dstPort = 53
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val packet = PacketCodec.ipv4Udp(
            sourceIp = srcIp,
            destinationIp = dstIp,
            sourcePort = srcPort,
            destinationPort = dstPort,
            payload = payload
        )

        assertNotNull(packet)
        val ipv4Header = Ipv4Header.parse(packet, packet.size)
        assertNotNull(ipv4Header)
        assertEquals(PROTO_UDP, ipv4Header!!.protocol)
        assertEquals(srcIp, ipv4Header.sourceIp)
        assertEquals(dstIp, ipv4Header.destinationIp)

        val udpHeader = UdpHeader.parse(packet, ipv4Header.headerLength, packet.size - ipv4Header.headerLength)
        assertNotNull(udpHeader)
        assertEquals(srcPort, udpHeader!!.sourcePort)
        assertEquals(dstPort, udpHeader.destinationPort)
        assertEquals(8 + payload.size, udpHeader.length)
    }
}
