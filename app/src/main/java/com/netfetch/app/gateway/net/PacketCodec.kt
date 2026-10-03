package com.netfetch.app.gateway.net

import java.net.InetAddress

internal object PacketCodec {

    fun checksum(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset
    ): Int {
        var sum = 0L
        var i = offset
        val end = offset + length

        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or
                (data[i + 1].toInt() and 0xFF)
            i += 2
        }

        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return sum.inv().toInt() and 0xFFFF
    }

    fun transportChecksum(
        sourceIp: Int,
        destinationIp: Int,
        protocol: Int,
        payload: ByteArray,
        offset: Int = 0,
        length: Int = payload.size - offset
    ): Int {
        var sum = 0L

        sum += (sourceIp ushr 16) and 0xFFFF
        sum += sourceIp and 0xFFFF
        sum += (destinationIp ushr 16) and 0xFFFF
        sum += destinationIp and 0xFFFF
        sum += protocol and 0xFF
        sum += length and 0xFFFF

        var i = offset
        val end = offset + length

        while (i + 1 < end) {
            sum += ((payload[i].toInt() and 0xFF) shl 8) or
                (payload[i + 1].toInt() and 0xFF)
            i += 2
        }

        if (i < end) {
            sum += (payload[i].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return sum.inv().toInt() and 0xFFFF
    }

    fun ipv4Tcp(
        sourceIp: Int,
        destinationIp: Int,
        sourcePort: Int,
        destinationPort: Int,
        sequence: Long,
        acknowledgement: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {
        val ipHeaderLength = 20
        val tcpHeaderLength = 20
        val totalLength =
            ipHeaderLength + tcpHeaderLength + payload.size

        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, 0)
        writeShort(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = PROTO_TCP

        writeInt(packet, 12, sourceIp)
        writeInt(packet, 16, destinationIp)

        writeShort(packet, 20, sourcePort)
        writeShort(packet, 22, destinationPort)
        writeInt(packet, 24, sequence.toInt())
        writeInt(packet, 28, acknowledgement.toInt())

        packet[32] = 0x50
        packet[33] = flags.toByte()
        writeShort(packet, 34, window)
        writeShort(packet, 36, 0)
        writeShort(packet, 38, 0)

        if (payload.isNotEmpty()) {
            payload.copyInto(
                packet,
                destinationOffset = 40
            )
        }

        val tcpChecksum =
            transportChecksum(
                sourceIp,
                destinationIp,
                PROTO_TCP,
                packet,
                20,
                tcpHeaderLength + payload.size
            )

        writeShort(packet, 36, tcpChecksum)

        writeShort(
            packet,
            10,
            checksum(packet, 0, ipHeaderLength)
        )

        return packet
    }

    fun ipv4Udp(
        sourceIp: Int,
        destinationIp: Int,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLength = 20
        val udpHeaderLength = 8
        val totalLength =
            ipHeaderLength + udpHeaderLength + payload.size

        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, 0)
        writeShort(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = PROTO_UDP

        writeInt(packet, 12, sourceIp)
        writeInt(packet, 16, destinationIp)

        writeShort(packet, 20, sourcePort)
        writeShort(packet, 22, destinationPort)
        writeShort(packet, 24, udpHeaderLength + payload.size)
        writeShort(packet, 26, 0)

        payload.copyInto(
            packet,
            destinationOffset = 28
        )

        val udpChecksum =
            transportChecksum(
                sourceIp,
                destinationIp,
                PROTO_UDP,
                packet,
                20,
                udpHeaderLength + payload.size
            )

        writeShort(packet, 26, udpChecksum)

        writeShort(
            packet,
            10,
            checksum(packet, 0, ipHeaderLength)
        )

        return packet
    }

    fun ipv4Address(ip: Int): InetAddress {
        return InetAddress.getByAddress(
            byteArrayOf(
                (ip ushr 24).toByte(),
                (ip ushr 16).toByte(),
                (ip ushr 8).toByte(),
                ip.toByte()
            )
        )
    }

    private fun writeShort(
        buffer: ByteArray,
        offset: Int,
        value: Int
    ) {
        buffer[offset] = (value ushr 8).toByte()
        buffer[offset + 1] = value.toByte()
    }

    private fun writeInt(
        buffer: ByteArray,
        offset: Int,
        value: Int
    ) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }
}
