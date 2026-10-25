package com.netfetch.app.netfetchlink

import com.netfetch.app.gateway.net.NetfetchTcpChannelHandshake
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

internal class NetfetchSocks5ChannelHandshake(
    username: String,
    password: String,
    destination: InetAddress,
    destinationPort: Int
) : NetfetchTcpChannelHandshake {
    override val timeoutMillis: Long = 30_000L

    private enum class State {
        WRITE_GREETING,
        READ_METHOD,
        WRITE_AUTH,
        READ_AUTH,
        WRITE_CONNECT,
        READ_REPLY_HEADER,
        READ_DOMAIN_LENGTH,
        READ_REPLY_TAIL,
        COMPLETE
    }

    private val usernameBytes = username.toByteArray(Charsets.UTF_8)
    private val passwordBytes = password.toByteArray(Charsets.UTF_8)
    private val destinationPort = destinationPort.also {
        if (it !in 1..65535) throw IOException("Invalid SOCKS5 destination port")
    }
    private val destinationBytes = destination.address
    private var state = State.WRITE_GREETING
    private var output: ByteBuffer? = ByteBuffer.wrap(
        byteArrayOf(5, 2, 0, 2)
    )
    private var nextStateAfterWrite = State.READ_METHOD
    private var input = ByteBuffer.allocate(0)
    private var inputSize = -1
    private var replyTailSize = 0

    override val interestOps: Int
        get() = if (output != null) SelectionKey.OP_WRITE else SelectionKey.OP_READ

    override fun advance(channel: SocketChannel): Boolean {
        for (step in 0 until 32) {
            val pendingOutput = output
            if (pendingOutput != null) {
                channel.write(pendingOutput)
                if (pendingOutput.hasRemaining()) return false
                output = null
                state = nextStateAfterWrite
                continue
            }

            when (state) {
                State.READ_METHOD -> {
                    val response = readExact(channel, 2) ?: return false
                    if ((response[0].toInt() and 0xff) != 5) {
                        throw IOException("Invalid SOCKS5 method response")
                    }
                    when (response[1].toInt() and 0xff) {
                        0 -> prepareConnectRequest()
                        2 -> prepareAuthentication()
                        else -> throw IOException("Provider rejected SOCKS5 authentication")
                    }
                }
                State.READ_AUTH -> {
                    val response = readExact(channel, 2) ?: return false
                    if ((response[0].toInt() and 0xff) != 1 ||
                        (response[1].toInt() and 0xff) != 0
                    ) {
                        throw IOException("NetFetch provider authentication failed")
                    }
                    prepareConnectRequest()
                }
                State.READ_REPLY_HEADER -> {
                    val response = readExact(channel, 4) ?: return false
                    if ((response[0].toInt() and 0xff) != 5 ||
                        (response[1].toInt() and 0xff) != 0 ||
                        (response[2].toInt() and 0xff) != 0
                    ) {
                        throw IOException("SOCKS5 CONNECT request failed")
                    }
                    replyTailSize = when (response[3].toInt() and 0xff) {
                        1 -> 4 + 2
                        3 -> {
                            state = State.READ_DOMAIN_LENGTH
                            continue
                        }
                        4 -> 16 + 2
                        else -> throw IOException("Unknown SOCKS5 address type")
                    }
                    state = State.READ_REPLY_TAIL
                }
                State.READ_DOMAIN_LENGTH -> {
                    val length = readExact(channel, 1) ?: return false
                    val size = length[0].toInt() and 0xff
                    if (size == 0) throw IOException("Invalid SOCKS5 domain response")
                    replyTailSize = size + 2
                    state = State.READ_REPLY_TAIL
                }
                State.READ_REPLY_TAIL -> {
                    readExact(channel, replyTailSize) ?: return false
                    state = State.COMPLETE
                    return true
                }
                State.COMPLETE -> return true
                State.WRITE_GREETING,
                State.WRITE_AUTH,
                State.WRITE_CONNECT -> error("Missing SOCKS5 request buffer")
            }
        }
        return false
    }

    private fun prepareAuthentication() {
        if (usernameBytes.size > 255 || passwordBytes.size > 255) {
            throw IOException("SOCKS5 credentials exceed protocol limits")
        }
        val bytes = ByteArray(3 + usernameBytes.size + passwordBytes.size)
        var offset = 0
        bytes[offset++] = 1
        bytes[offset++] = usernameBytes.size.toByte()
        usernameBytes.copyInto(bytes, offset)
        offset += usernameBytes.size
        bytes[offset++] = passwordBytes.size.toByte()
        passwordBytes.copyInto(bytes, offset)
        output = ByteBuffer.wrap(bytes)
        nextStateAfterWrite = State.READ_AUTH
        state = State.WRITE_AUTH
    }

    private fun prepareConnectRequest() {
        val addressType = when (destinationBytes.size) {
            4 -> 1
            16 -> 4
            else -> throw IOException("Unsupported SOCKS5 destination address")
        }
        val request = ByteArray(4 + destinationBytes.size + 2)
        request[0] = 5
        request[1] = 1
        request[2] = 0
        request[3] = addressType.toByte()
        destinationBytes.copyInto(request, 4)
        request[request.size - 2] = (destinationPort ushr 8).toByte()
        request[request.size - 1] = destinationPort.toByte()
        output = ByteBuffer.wrap(request)
        nextStateAfterWrite = State.READ_REPLY_HEADER
        state = State.WRITE_CONNECT
    }

    private fun readExact(channel: SocketChannel, size: Int): ByteArray? {
        if (inputSize != size) {
            if (input.position() != 0) {
                throw IOException("SOCKS5 response parser lost framing")
            }
            input = ByteBuffer.allocate(size)
            inputSize = size
        }
        val count = channel.read(input)
        if (count < 0) throw IOException("Unexpected end of SOCKS5 response")
        if (input.hasRemaining()) return null
        input.flip()
        val bytes = ByteArray(size)
        input.get(bytes)
        input = ByteBuffer.allocate(0)
        inputSize = -1
        return bytes
    }
}
