package com.example.vpn

import java.net.InetAddress

object PacketUtils {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    data class IpHeader(
        val version: Int,
        val ihl: Int,
        val totalLength: Int,
        val protocol: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
    )

    data class TcpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val seqNum: Long,
        val ackNum: Long,
        val headerLength: Int,
        val flags: Int,
        val windowSize: Int,
        val payloadOffset: Int,
        val payloadLength: Int,
    )

    data class UdpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val length: Int,
        val payloadOffset: Int,
        val payloadLength: Int,
    )

    fun parseIpHeader(packet: ByteArray, length: Int): IpHeader? {
        if (length < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl > length) return null
        val totalLength = readUInt16(packet, 2).coerceAtMost(length)
        if (totalLength < ihl) return null
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        return IpHeader(version, ihl, totalLength, protocol, srcIp, dstIp)
    }

    fun parseTcpHeader(packet: ByteArray, ip: IpHeader): TcpHeader? {
        val offset = ip.ihl
        if (ip.totalLength < offset + 20) return null
        val srcPort = readUInt16(packet, offset)
        val dstPort = readUInt16(packet, offset + 2)
        val seqNum = readUInt32(packet, offset + 4)
        val ackNum = readUInt32(packet, offset + 8)
        val dataOffset = ((packet[offset + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < 20 || offset + dataOffset > ip.totalLength) return null
        val flags = packet[offset + 13].toInt() and 0x3F
        val windowSize = readUInt16(packet, offset + 14)
        val payloadOffset = offset + dataOffset
        val payloadLength = (ip.totalLength - payloadOffset).coerceAtLeast(0)
        return TcpHeader(
            srcPort = srcPort,
            dstPort = dstPort,
            seqNum = seqNum,
            ackNum = ackNum,
            headerLength = dataOffset,
            flags = flags,
            windowSize = windowSize,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength,
        )
    }

    fun parseUdpHeader(packet: ByteArray, ip: IpHeader): UdpHeader? {
        val offset = ip.ihl
        if (ip.totalLength < offset + 8) return null
        val srcPort = readUInt16(packet, offset)
        val dstPort = readUInt16(packet, offset + 2)
        val udpLen = readUInt16(packet, offset + 4)
        val payloadOffset = offset + 8
        val payloadLength = (ip.totalLength - payloadOffset).coerceAtLeast(0)
        return UdpHeader(
            srcPort = srcPort,
            dstPort = dstPort,
            length = udpLen,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength,
        )
    }

    fun buildTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0),
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size,
        includeMssOption: Boolean = false,
    ): ByteArray {
        val tcpHeaderLen = if (includeMssOption) 24 else 20
        val totalLen = 20 + tcpHeaderLen + payloadLength
        val out = ByteArray(totalLen)

        // IPv4 Header
        out[0] = 0x45
        out[1] = 0x00
        writeUInt16(out, 2, totalLen)
        writeUInt16(out, 4, ((seqNum ushr 16) and 0xFFFF).toInt())
        writeUInt16(out, 6, 0x4000) // Don't fragment
        out[8] = 64 // TTL
        out[9] = PROTO_TCP.toByte()
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        val ipChecksum = computeChecksum(out, 0, 20)
        writeUInt16(out, 10, ipChecksum)

        // TCP Header
        val tcpOff = 20
        writeUInt16(out, tcpOff, srcPort)
        writeUInt16(out, tcpOff + 2, dstPort)
        writeUInt32(out, tcpOff + 4, seqNum)
        writeUInt32(out, tcpOff + 8, ackNum)
        out[tcpOff + 12] = ((tcpHeaderLen / 4) shl 4).toByte()
        out[tcpOff + 13] = (flags and 0x3F).toByte()
        writeUInt16(out, tcpOff + 14, windowSize.coerceIn(0, 65535))
        if (includeMssOption) {
            out[tcpOff + 20] = 0x02 // Kind: MSS
            out[tcpOff + 21] = 0x04 // Length: 4
            writeUInt16(out, tcpOff + 22, 1360)
        }
        if (payloadLength > 0) {
            System.arraycopy(payload, payloadOffset, out, tcpOff + tcpHeaderLen, payloadLength)
        }

        val tcpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_TCP,
            buffer = out,
            transportOffset = tcpOff,
            transportLength = tcpHeaderLen + payloadLength,
        )
        writeUInt16(out, tcpOff + 16, tcpChecksum)
        return out
    }

    fun buildUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)

        // IPv4 Header
        out[0] = 0x45
        out[1] = 0x00
        writeUInt16(out, 2, totalLen)
        writeUInt16(out, 4, (System.nanoTime() and 0xFFFF).toInt())
        writeUInt16(out, 6, 0x4000)
        out[8] = 64
        out[9] = PROTO_UDP.toByte()
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        writeUInt16(out, 10, computeChecksum(out, 0, 20))

        // UDP Header
        val udpOff = 20
        writeUInt16(out, udpOff, srcPort)
        writeUInt16(out, udpOff + 2, dstPort)
        writeUInt16(out, udpOff + 4, udpLen)
        System.arraycopy(payload, 0, out, udpOff + 8, payload.size)

        var udpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_UDP,
            buffer = out,
            transportOffset = udpOff,
            transportLength = udpLen,
        )
        if (udpChecksum == 0) udpChecksum = 0xFFFF
        writeUInt16(out, udpOff + 6, udpChecksum)
        return out
    }

    fun computeChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    fun computeTransportChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        protocol: Int,
        buffer: ByteArray,
        transportOffset: Int,
        transportLength: Int,
    ): Int {
        var sum = 0L
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += protocol.toLong()
        sum += transportLength.toLong()

        var i = transportOffset
        val end = transportOffset + transportLength
        while (i + 1 < end) {
            val word = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    fun readUInt16(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
    }

    fun writeUInt16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    fun readUInt32(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)
    }

    fun writeUInt32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    fun ipBytesToInt(ip: ByteArray): Int {
        return ((ip[0].toInt() and 0xFF) shl 24) or
            ((ip[1].toInt() and 0xFF) shl 16) or
            ((ip[2].toInt() and 0xFF) shl 8) or
            (ip[3].toInt() and 0xFF)
    }

    fun ipBytesToString(ip: ByteArray): String {
        return InetAddress.getByAddress(ip).hostAddress ?: "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
    }
}
