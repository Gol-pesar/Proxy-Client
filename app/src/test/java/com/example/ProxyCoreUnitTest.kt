package com.example

import com.example.data.local.ProxyProfileEntity
import com.example.data.model.ProxyProtocol
import com.example.data.model.ProxyUriParser
import com.example.vpn.PacketUtils
import com.example.vpn.ProxyTunnelClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

class ProxyCoreUnitTest {

    @Test
    fun `packet utils builds and parses IPv4 TCP packet with valid checksums`() {
        val srcIp = byteArrayOf(10, 10, 0, 2)
        val dstIp = byteArrayOf(93, 184.toByte(), 216.toByte(), 34)
        val payload = "GET / HTTP/1.1\r\n\r\n".toByteArray()

        val packet = PacketUtils.buildTcpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 51234,
            dstPort = 80,
            seqNum = 1000L,
            ackNum = 2000L,
            flags = PacketUtils.TCP_PSH or PacketUtils.TCP_ACK,
            payload = payload,
        )

        val ip = PacketUtils.parseIpHeader(packet, packet.size)
        assertNotNull(ip)
        assertEquals(PacketUtils.PROTO_TCP, ip!!.protocol)
        assertArrayEquals(srcIp, ip.srcIp)
        assertArrayEquals(dstIp, ip.dstIp)
        // IPv4 header checksum verification: sum of header words must be 0xFFFF -> computeChecksum == 0
        assertEquals(0, PacketUtils.computeChecksum(packet, 0, ip.ihl))

        val tcp = PacketUtils.parseTcpHeader(packet, ip)
        assertNotNull(tcp)
        assertEquals(51234, tcp!!.srcPort)
        assertEquals(80, tcp.dstPort)
        assertEquals(1000L, tcp.seqNum)
        assertEquals(2000L, tcp.ackNum)
        assertEquals(payload.size, tcp.payloadLength)
        assertEquals(
            0,
            PacketUtils.computeTransportChecksum(
                srcIp = srcIp,
                dstIp = dstIp,
                protocol = PacketUtils.PROTO_TCP,
                buffer = packet,
                transportOffset = ip.ihl,
                transportLength = packet.size - ip.ihl,
            ),
        )
    }

    @Test
    fun `proxy uri parser supports socks5 and http with credentials`() {
        val socks = ProxyUriParser.parse("socks5://alice:s3cr3t@10.0.0.5:1088#GermanyNode").getOrThrow()
        assertEquals(ProxyProtocol.SOCKS5, socks.protocol)
        assertEquals("10.0.0.5", socks.host)
        assertEquals(1088, socks.port)
        assertEquals("alice", socks.username)
        assertEquals("s3cr3t", socks.password)
        assertEquals("GermanyNode", socks.name)

        val http = ProxyUriParser.parse("http://proxy.local:3128").getOrThrow()
        assertEquals(ProxyProtocol.HTTP, http.protocol)
        assertEquals("proxy.local", http.host)
        assertEquals(3128, http.port)
        assertEquals("", http.username)
    }

    @Test
    fun `socks5 client performs RFC1928 and RFC1929 handshake against local server`() {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                server.accept().use { client ->
                    val input = DataInputStream(client.getInputStream())
                    val out = client.getOutputStream()

                    // Greeting
                    val ver = input.readUnsignedByte()
                    val nMethods = input.readUnsignedByte()
                    val methods = ByteArray(nMethods)
                    input.readFully(methods)
                    assertEquals(0x05, ver)
                    assertTrue(methods.contains(0x02.toByte()))
                    out.write(byteArrayOf(0x05, 0x02))
                    out.flush()

                    // RFC 1929 Auth
                    val authVer = input.readUnsignedByte()
                    val uLen = input.readUnsignedByte()
                    val user = ByteArray(uLen)
                    input.readFully(user)
                    val pLen = input.readUnsignedByte()
                    val pass = ByteArray(pLen)
                    input.readFully(pass)
                    assertEquals(0x01, authVer)
                    assertEquals("user1", String(user))
                    assertEquals("pass1", String(pass))
                    out.write(byteArrayOf(0x01, 0x00))
                    out.flush()

                    // CONNECT request
                    val req = ByteArray(10)
                    input.readFully(req)
                    assertEquals(0x05, req[0].toInt())
                    assertEquals(0x01, req[1].toInt())
                    out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 80))
                    out.flush()
                }
            }

            val profile = ProxyProfileEntity(
                id = 1L,
                name = "LocalSocks",
                protocol = ProxyProtocol.SOCKS5,
                host = "127.0.0.1",
                port = port,
                username = "user1",
                password = "pass1",
            )
            val result = ProxyTunnelClient.testProxyConnection(profile)
            serverThread.join(3000)
            assertTrue("Expected SOCKS5 handshake to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        }
    }

    @Test
    fun `http connect client performs CONNECT handshake against local server`() {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines.add(line)
                    }
                    assertTrue(lines.first().startsWith("CONNECT 1.1.1.1:80 HTTP/1.1"))
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    out.flush()
                }
            }

            val profile = ProxyProfileEntity(
                id = 2L,
                name = "LocalHttp",
                protocol = ProxyProtocol.HTTP,
                host = "127.0.0.1",
                port = port,
            )
            val result = ProxyTunnelClient.testProxyConnection(profile)
            serverThread.join(3000)
            assertTrue("Expected HTTP CONNECT handshake to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        }
    }
}
