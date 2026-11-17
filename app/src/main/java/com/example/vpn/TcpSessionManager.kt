package com.example.vpn

import com.example.data.local.ProxyProfileEntity
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class TcpSessionManager(
    private val scope: CoroutineScope,
    private val profile: ProxyProfileEntity,
    private val protectSocket: (Socket) -> Boolean,
    private val writePacketToTun: (ByteArray) -> Unit,
) {
    data class SessionKey(
        val srcIpInt: Int,
        val srcPort: Int,
        val dstIpInt: Int,
        val dstPort: Int,
    )

    private enum class TcpState {
        CONNECTING,
        ESTABLISHED,
        CLOSING,
        CLOSED,
    }

    private inner class TcpSession(
        val key: SessionKey,
        val clientIp: ByteArray,
        val remoteIp: ByteArray,
        val clientPort: Int,
        val remotePort: Int,
        clientInitialSeq: Long,
    ) {
        val lock = Any()
        @Volatile var state: TcpState = TcpState.CONNECTING
        var serverSeq: Long = Random.nextLong(100_000L, 2_000_000_000L)
        var clientNextSeq: Long = (clientInitialSeq + 1L) and 0xFFFFFFFFL
        var upstreamSocket: Socket? = null
        var upstreamOut: OutputStream? = null
        @Volatile var lastActivityMs: Long = System.currentTimeMillis()
        val closedFlag = AtomicBoolean(false)

        fun startConnect() {
            scope.launch(Dispatchers.IO) {
                try {
                    val socket = ProxyTunnelClient.openTcpTunnel(
                        profile = profile,
                        dstIpBytes = remoteIp,
                        dstPort = remotePort,
                        protectSocket = protectSocket,
                    )
                    if (closedFlag.get() || state == TcpState.CLOSED) {
                        runCatching { socket.close() }
                        return@launch
                    }
                    synchronized(lock) {
                        upstreamSocket = socket
                        upstreamOut = socket.getOutputStream()
                        state = TcpState.ESTABLISHED
                        val synAck = PacketUtils.buildTcpPacket(
                            srcIp = remoteIp,
                            dstIp = clientIp,
                            srcPort = remotePort,
                            dstPort = clientPort,
                            seqNum = serverSeq,
                            ackNum = clientNextSeq,
                            flags = PacketUtils.TCP_SYN or PacketUtils.TCP_ACK,
                            includeMssOption = true,
                        )
                        serverSeq = (serverSeq + 1L) and 0xFFFFFFFFL
                        writePacketToTun(synAck)
                        lastActivityMs = System.currentTimeMillis()
                    }
                    readFromUpstreamLoop(socket)
                } catch (e: Throwable) {
                    val dstStr = "${PacketUtils.ipBytesToString(remoteIp)}:$remotePort"
                    VpnStateHolder.addLog(
                        LogLevel.WARN,
                        "TCP",
                        "خطا در اتصال به $dstStr: ${e.message ?: "Connection failed"}",
                    )
                    sendResetAndClose()
                }
            }
        }

        private fun readFromUpstreamLoop(socket: Socket) {
            val buffer = ByteArray(1360)
            try {
                val input = socket.getInputStream()
                while (!closedFlag.get()) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        sendFinAndClose()
                        break
                    }
                    if (read > 0) {
                        lastActivityMs = System.currentTimeMillis()
                        VpnStateHolder.totalRxBytes.addAndGet(read.toLong())
                        synchronized(lock) {
                            if (state == TcpState.CLOSED) break
                            val dataPkt = PacketUtils.buildTcpPacket(
                                srcIp = remoteIp,
                                dstIp = clientIp,
                                srcPort = remotePort,
                                dstPort = clientPort,
                                seqNum = serverSeq,
                                ackNum = clientNextSeq,
                                flags = PacketUtils.TCP_PSH or PacketUtils.TCP_ACK,
                                payload = buffer,
                                payloadOffset = 0,
                                payloadLength = read,
                            )
                            serverSeq = (serverSeq + read.toLong()) and 0xFFFFFFFFL
                            writePacketToTun(dataPkt)
                        }
                    }
                }
            } catch (_: IOException) {
                if (!closedFlag.get()) {
                    sendResetAndClose()
                }
            }
        }

        fun onClientPacket(packet: ByteArray, tcp: PacketUtils.TcpHeader) {
            lastActivityMs = System.currentTimeMillis()
            if ((tcp.flags and PacketUtils.TCP_RST) != 0) {
                closeSilently()
                return
            }

            if ((tcp.flags and PacketUtils.TCP_SYN) != 0) {
                // Retransmitted SYN while connecting or established
                synchronized(lock) {
                    if (state == TcpState.ESTABLISHED) {
                        val synAck = PacketUtils.buildTcpPacket(
                            srcIp = remoteIp,
                            dstIp = clientIp,
                            srcPort = remotePort,
                            dstPort = clientPort,
                            seqNum = (serverSeq - 1L) and 0xFFFFFFFFL,
                            ackNum = clientNextSeq,
                            flags = PacketUtils.TCP_SYN or PacketUtils.TCP_ACK,
                        )
                        writePacketToTun(synAck)
                    }
                }
                return
            }

            if (tcp.payloadLength > 0) {
                val payloadCopy = packet.copyOfRange(tcp.payloadOffset, tcp.payloadOffset + tcp.payloadLength)
                val expectedSeq: Long
                val outStream: OutputStream?
                synchronized(lock) {
                    expectedSeq = clientNextSeq
                    outStream = upstreamOut
                    if (tcp.seqNum == expectedSeq) {
                        clientNextSeq = (clientNextSeq + payloadCopy.size.toLong()) and 0xFFFFFFFFL
                        val ackPkt = PacketUtils.buildTcpPacket(
                            srcIp = remoteIp,
                            dstIp = clientIp,
                            srcPort = remotePort,
                            dstPort = clientPort,
                            seqNum = serverSeq,
                            ackNum = clientNextSeq,
                            flags = PacketUtils.TCP_ACK,
                        )
                        writePacketToTun(ackPkt)
                    } else {
                        // Out-of-order / retransmission: re-ACK what we expect
                        val dupAck = PacketUtils.buildTcpPacket(
                            srcIp = remoteIp,
                            dstIp = clientIp,
                            srcPort = remotePort,
                            dstPort = clientPort,
                            seqNum = serverSeq,
                            ackNum = clientNextSeq,
                            flags = PacketUtils.TCP_ACK,
                        )
                        writePacketToTun(dupAck)
                        return
                    }
                }

                if (outStream != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            outStream.write(payloadCopy)
                            outStream.flush()
                            VpnStateHolder.totalTxBytes.addAndGet(payloadCopy.size.toLong())
                        } catch (_: IOException) {
                            sendResetAndClose()
                        }
                    }
                }
            }

            if ((tcp.flags and PacketUtils.TCP_FIN) != 0) {
                synchronized(lock) {
                    if (state == TcpState.CLOSED) return
                    clientNextSeq = (clientNextSeq + 1L) and 0xFFFFFFFFL
                    val finAck = PacketUtils.buildTcpPacket(
                        srcIp = remoteIp,
                        dstIp = clientIp,
                        srcPort = remotePort,
                        dstPort = clientPort,
                        seqNum = serverSeq,
                        ackNum = clientNextSeq,
                        flags = PacketUtils.TCP_FIN or PacketUtils.TCP_ACK,
                    )
                    serverSeq = (serverSeq + 1L) and 0xFFFFFFFFL
                    writePacketToTun(finAck)
                    state = TcpState.CLOSING
                }
                closeSilently()
            }
        }

        private fun sendFinAndClose() {
            synchronized(lock) {
                if (state == TcpState.CLOSED || state == TcpState.CLOSING) {
                    closeSilently()
                    return
                }
                val finPkt = PacketUtils.buildTcpPacket(
                    srcIp = remoteIp,
                    dstIp = clientIp,
                    srcPort = remotePort,
                    dstPort = clientPort,
                    seqNum = serverSeq,
                    ackNum = clientNextSeq,
                    flags = PacketUtils.TCP_FIN or PacketUtils.TCP_ACK,
                )
                serverSeq = (serverSeq + 1L) and 0xFFFFFFFFL
                writePacketToTun(finPkt)
                state = TcpState.CLOSING
            }
            closeSilently()
        }

        fun sendResetAndClose() {
            synchronized(lock) {
                if (state != TcpState.CLOSED) {
                    val rstPkt = PacketUtils.buildTcpPacket(
                        srcIp = remoteIp,
                        dstIp = clientIp,
                        srcPort = remotePort,
                        dstPort = clientPort,
                        seqNum = serverSeq,
                        ackNum = clientNextSeq,
                        flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK,
                    )
                    writePacketToTun(rstPkt)
                }
            }
            closeSilently()
        }

        fun closeSilently() {
            if (!closedFlag.compareAndSet(false, true)) return
            state = TcpState.CLOSED
            sessions.remove(key)
            VpnStateHolder.activeSessionCount.set(sessions.size)
            runCatching { upstreamSocket?.close() }
            upstreamSocket = null
            upstreamOut = null
        }
    }

    private val sessions = ConcurrentHashMap<SessionKey, TcpSession>()

    fun handleTcpPacket(packet: ByteArray, ip: PacketUtils.IpHeader, tcp: PacketUtils.TcpHeader) {
        val key = SessionKey(
            srcIpInt = PacketUtils.ipBytesToInt(ip.srcIp),
            srcPort = tcp.srcPort,
            dstIpInt = PacketUtils.ipBytesToInt(ip.dstIp),
            dstPort = tcp.dstPort,
        )

        val existing = sessions[key]
        if (existing != null) {
            existing.onClientPacket(packet, tcp)
            return
        }

        if ((tcp.flags and PacketUtils.TCP_SYN) != 0 && (tcp.flags and PacketUtils.TCP_ACK) == 0) {
            val session = TcpSession(
                key = key,
                clientIp = ip.srcIp,
                remoteIp = ip.dstIp,
                clientPort = tcp.srcPort,
                remotePort = tcp.dstPort,
                clientInitialSeq = tcp.seqNum,
            )
            if (sessions.putIfAbsent(key, session) == null) {
                VpnStateHolder.activeSessionCount.set(sessions.size)
                session.startConnect()
            }
        } else if ((tcp.flags and PacketUtils.TCP_RST) == 0) {
            // Unknown non-SYN TCP packet -> respond with RST so client recovers immediately
            val rst = PacketUtils.buildTcpPacket(
                srcIp = ip.dstIp,
                dstIp = ip.srcIp,
                srcPort = tcp.dstPort,
                dstPort = tcp.srcPort,
                seqNum = tcp.ackNum,
                ackNum = (tcp.seqNum + tcp.payloadLength.toLong()) and 0xFFFFFFFFL,
                flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK,
            )
            writePacketToTun(rst)
        }
    }

    fun reapIdleSessions(maxIdleMs: Long = 120_000L) {
        val now = System.currentTimeMillis()
        sessions.values.forEach { session ->
            if (now - session.lastActivityMs > maxIdleMs) {
                session.closeSilently()
            }
        }
    }

    fun closeAll() {
        sessions.values.toList().forEach { it.closeSilently() }
        sessions.clear()
        VpnStateHolder.activeSessionCount.set(0)
    }
}
