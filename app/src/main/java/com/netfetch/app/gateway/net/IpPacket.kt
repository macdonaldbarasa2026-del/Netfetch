package com.netfetch.app.gateway.net

internal const val PROTO_TCP = 6
internal const val PROTO_UDP = 17

internal data class Ipv4Header(
    val headerLength: Int,
    val totalLength: Int,
    val protocol: Int,
    val sourceIp: Int,
    val destinationIp: Int
) {
    companion object {
        fun parse(buffer: ByteArray, length: Int): Ipv4Header? {
            if (length < 20) return null

            val versionIhl = buffer[0].toInt() and 0xFF
            if ((versionIhl ushr 4) != 4) return null

            val headerLength = (versionIhl and 0x0F) * 4
            if (headerLength < 20 || headerLength > length) return null

            val totalLength =
                ((buffer[2].toInt() and 0xFF) shl 8) or
                    (buffer[3].toInt() and 0xFF)

            if (totalLength < headerLength || totalLength > length) return null

            val fragment =
                ((buffer[6].toInt() and 0xFF) shl 8) or
                    (buffer[7].toInt() and 0xFF)

            if ((fragment and 0x1FFF) != 0 || (fragment and 0x2000) != 0) {
                return null
            }

            return Ipv4Header(
                headerLength = headerLength,
                totalLength = totalLength,
                protocol = buffer[9].toInt() and 0xFF,
                sourceIp = readInt(buffer, 12),
                destinationIp = readInt(buffer, 16)
            )
        }
    }
}

internal data class TcpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val sequence: Long,
    val acknowledgement: Long,
    val dataOffset: Int,
    val flags: Int,
    val window: Int
) {
    val syn: Boolean get() = flags and SYN != 0
    val ack: Boolean get() = flags and ACK != 0
    val fin: Boolean get() = flags and FIN != 0
    val rst: Boolean get() = flags and RST != 0

    companion object {
        const val FIN = 0x01
        const val SYN = 0x02
        const val RST = 0x04
        const val ACK = 0x10
        const val PUSH_ACK = 0x18

        fun parse(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): TcpHeader? {
            if (length < 20 || offset < 0 || offset + length > buffer.size) {
                return null
            }

            val dataOffset =
                ((buffer[offset + 12].toInt() and 0xFF) ushr 4) * 4

            if (dataOffset < 20 || dataOffset > length) return null

            return TcpHeader(
                sourcePort = readUnsignedShort(buffer, offset),
                destinationPort = readUnsignedShort(buffer, offset + 2),
                sequence = readUnsignedInt(buffer, offset + 4),
                acknowledgement = readUnsignedInt(buffer, offset + 8),
                dataOffset = dataOffset,
                flags = buffer[offset + 13].toInt() and 0xFF,
                window = readUnsignedShort(buffer, offset + 14)
            )
        }
    }
}

internal data class UdpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val length: Int
) {
    val payloadLength: Int
        get() = length - 8

    companion object {
        fun parse(
            buffer: ByteArray,
            offset: Int,
            available: Int
        ): UdpHeader? {
            if (available < 8 || offset < 0 || offset + available > buffer.size) {
                return null
            }

            val length = readUnsignedShort(buffer, offset + 4)

            if (length < 8 || length > available) {
                return null
            }

            return UdpHeader(
                sourcePort = readUnsignedShort(buffer, offset),
                destinationPort = readUnsignedShort(buffer, offset + 2),
                length = length
            )
        }
    }
}

internal fun readUnsignedShort(
    buffer: ByteArray,
    offset: Int
): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 8) or
        (buffer[offset + 1].toInt() and 0xFF)
}

internal fun readUnsignedInt(
    buffer: ByteArray,
    offset: Int
): Long {
    return ((buffer[offset].toLong() and 0xFF) shl 24) or
        ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
        ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
        (buffer[offset + 3].toLong() and 0xFF)
}

internal fun readInt(
    buffer: ByteArray,
    offset: Int
): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 24) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
        (buffer[offset + 3].toInt() and 0xFF)
}

internal fun Int.toIpv4String(): String {
    return "${(this ushr 24) and 0xFF}." +
        "${(this ushr 16) and 0xFF}." +
        "${(this ushr 8) and 0xFF}." +
        "${this and 0xFF}"
}

internal data class TcpFlowKey(
    val sourceIp: Int,
    val sourcePort: Int,
    val destinationIp: Int,
    val destinationPort: Int
)

internal data class UdpFlowKey(
    val sourceIp: Int,
    val sourcePort: Int,
    val destinationIp: Int,
    val destinationPort: Int
)
