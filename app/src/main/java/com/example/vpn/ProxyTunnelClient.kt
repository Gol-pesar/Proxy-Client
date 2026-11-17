package com.example.vpn

import android.util.Base64
import com.example.data.local.ProxyProfileEntity
import com.example.data.model.ProxyProtocol
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object ProxyTunnelClient {
    private const val HANDSHAKE_TIMEOUT_MS = 8000

    fun openTcpTunnel(
        profile: ProxyProfileEntity,
        dstIpBytes: ByteArray,
        dstPort: Int,
        protectSocket: ((Socket) -> Boolean)? = null,
    ): Socket {
        val socket = Socket()
        try {
            if (protectSocket != null && !protectSocket(socket)) {
                throw IOException("Failed to protect upstream proxy socket")
            }
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.connect(InetSocketAddress(profile.host, profile.port), HANDSHAKE_TIMEOUT_MS)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            when (profile.protocol) {
                ProxyProtocol.SOCKS5 -> performSocks5Connect(
                    input = input,
                    output = output,
                    dstIpBytes = dstIpBytes,
                    dstPort = dstPort,
                    username = profile.username,
                    password = profile.password,
                )
                ProxyProtocol.HTTP -> performHttpConnect(
                    input = input,
                    output = output,
                    dstHost = PacketUtils.ipBytesToString(dstIpBytes),
                    dstPort = dstPort,
                    username = profile.username,
                    password = profile.password,
                )
            }
            socket.soTimeout = 0
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    fun performSocks5Connect(
        input: InputStream,
        output: OutputStream,
        dstIpBytes: ByteArray,
        dstPort: Int,
        username: String,
        password: String,
    ) {
        val hasAuth = username.isNotEmpty() || password.isNotEmpty()
        val greeting = if (hasAuth) {
            byteArrayOf(0x05, 0x02, 0x00, 0x02)
        } else {
            byteArrayOf(0x05, 0x01, 0x00)
        }
        output.write(greeting)
        output.flush()

        val dataIn = DataInputStream(input)
        val ver = dataIn.readUnsignedByte()
        if (ver != 0x05) throw IOException("پاسخ SOCKS نامعتبر: version=$ver")
        val chosenMethod = dataIn.readUnsignedByte()

        when (chosenMethod) {
            0x00 -> Unit
            0x02 -> {
                if (!hasAuth) throw IOException("سرور SOCKS5 نیاز به نام کاربری و رمز عبور دارد")
                val userBytes = username.toByteArray(Charsets.UTF_8)
                val passBytes = password.toByteArray(Charsets.UTF_8)
                if (userBytes.size > 255 || passBytes.size > 255) {
                    throw IOException("طول نام کاربری یا رمز عبور بیش از حد مجاز است")
                }
                val authReq = ByteArray(3 + userBytes.size + passBytes.size)
                authReq[0] = 0x01
                authReq[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authReq, 2, userBytes.size)
                authReq[2 + userBytes.size] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authReq, 3 + userBytes.size, passBytes.size)
                output.write(authReq)
                output.flush()

                val authVer = dataIn.readUnsignedByte()
                val authStatus = dataIn.readUnsignedByte()
                if (authVer != 0x01 || authStatus != 0x00) {
                    throw IOException("احراز هویت SOCKS5 ناموفق بود (کد $authStatus)")
                }
            }
            0xFF -> throw IOException("روش احراز هویت SOCKS5 توسط سرور رد شد")
            else -> throw IOException("روش احراز هویت ناشناخته SOCKS5: $chosenMethod")
        }

        // CONNECT request (IPv4)
        val connectReq = ByteArray(10)
        connectReq[0] = 0x05
        connectReq[1] = 0x01 // CONNECT
        connectReq[2] = 0x00 // RSV
        connectReq[3] = 0x01 // ATYP IPv4
        System.arraycopy(dstIpBytes, 0, connectReq, 4, 4)
        connectReq[8] = ((dstPort ushr 8) and 0xFF).toByte()
        connectReq[9] = (dstPort and 0xFF).toByte()
        output.write(connectReq)
        output.flush()

        val repVer = dataIn.readUnsignedByte()
        val repCode = dataIn.readUnsignedByte()
        dataIn.readUnsignedByte() // RSV
        val atyp = dataIn.readUnsignedByte()
        if (repVer != 0x05 || repCode != 0x00) {
            throw IOException("اتصال SOCKS5 رد شد: ${socksReplyMessage(repCode)}")
        }
        when (atyp) {
            0x01 -> dataIn.readFully(ByteArray(6))
            0x03 -> {
                val len = dataIn.readUnsignedByte()
                dataIn.readFully(ByteArray(len + 2))
            }
            0x04 -> dataIn.readFully(ByteArray(18))
            else -> throw IOException("نوع آدرس نامعتبر در پاسخ SOCKS5: $atyp")
        }
    }

    fun performHttpConnect(
        input: InputStream,
        output: OutputStream,
        dstHost: String,
        dstPort: Int,
        username: String,
        password: String,
    ) {
        val sb = StringBuilder()
        sb.append("CONNECT ").append(dstHost).append(':').append(dstPort).append(" HTTP/1.1\r\n")
        sb.append("Host: ").append(dstHost).append(':').append(dstPort).append("\r\n")
        if (username.isNotEmpty() || password.isNotEmpty()) {
            val rawCreds = "$username:$password".toByteArray(Charsets.UTF_8)
            val encoded = Base64.encodeToString(rawCreds, Base64.NO_WRAP)
            sb.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n")
        }
        sb.append("Proxy-Connection: Keep-Alive\r\n\r\n")

        output.write(sb.toString().toByteArray(Charsets.US_ASCII))
        output.flush()

        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (headerBytes.size() < 16384) {
            val b = input.read()
            if (b == -1) throw IOException("سرور HTTP پروکسی اتصال را قبل از پاسخ بست")
            headerBytes.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
        }
        if (state != 4) throw IOException("پاسخ HTTP CONNECT بیش از حد طولانی است")

        val responseText = headerBytes.toString(Charsets.US_ASCII.name())
        val statusLine = responseText.lineSequence().firstOrNull().orEmpty()
        val parts = statusLine.split(" ", limit = 3)
        val statusCode = parts.getOrNull(1)?.toIntOrNull()
            ?: throw IOException("ساختار پاسخ HTTP پروکسی نامعتبر: $statusLine")
        if (statusCode !in 200..299) {
            throw IOException("خطای پروکسی HTTP: $statusLine")
        }
    }

    fun forwardDnsOverTcp(
        profile: ProxyProfileEntity,
        dnsQuery: ByteArray,
        dnsServerIpBytes: ByteArray,
        protectSocket: (Socket) -> Boolean,
    ): ByteArray {
        openTcpTunnel(profile, dnsServerIpBytes, 53, protectSocket).use { socket ->
            socket.soTimeout = 5000
            val out = socket.getOutputStream()
            val lenPrefix = byteArrayOf(
                ((dnsQuery.size ushr 8) and 0xFF).toByte(),
                (dnsQuery.size and 0xFF).toByte(),
            )
            out.write(lenPrefix)
            out.write(dnsQuery)
            out.flush()

            val dataIn = DataInputStream(socket.getInputStream())
            val respLen = dataIn.readUnsignedShort()
            if (respLen <= 0 || respLen > 4096) {
                throw IOException("طول پاسخ DNS نامعتبر: $respLen")
            }
            val resp = ByteArray(respLen)
            dataIn.readFully(resp)
            return resp
        }
    }

    fun forwardDnsDirectProtected(
        dnsQuery: ByteArray,
        dstIpBytes: ByteArray,
        dstPort: Int,
        protectDatagram: (DatagramSocket) -> Boolean,
    ): ByteArray {
        DatagramSocket().use { ds ->
            if (!protectDatagram(ds)) {
                throw IOException("Failed to protect DNS DatagramSocket")
            }
            ds.soTimeout = 4500
            val addr = InetAddress.getByAddress(dstIpBytes)
            val sendPacket = DatagramPacket(dnsQuery, dnsQuery.size, addr, dstPort)
            ds.send(sendPacket)

            val buf = ByteArray(2048)
            val recvPacket = DatagramPacket(buf, buf.size)
            ds.receive(recvPacket)
            return buf.copyOfRange(0, recvPacket.length)
        }
    }

    fun testProxyConnection(
        profile: ProxyProfileEntity,
        protectSocket: ((Socket) -> Boolean)? = null,
    ): Result<Int> = runCatching {
        val startNs = System.nanoTime()
        val testIp = byteArrayOf(1, 1, 1, 1) // Cloudflare anycast test endpoint
        openTcpTunnel(profile, testIp, 80, protectSocket).use { socket ->
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            socket.close()
            elapsedMs
        }
    }

    private fun socksReplyMessage(code: Int): String = when (code) {
        0x01 -> "خطای عمومی سرور SOCKS"
        0x02 -> "اتصال توسط قوانین سرور مجاز نیست"
        0x03 -> "شبکه در دسترس نیست"
        0x04 -> "میزبان مقصد در دسترس نیست"
        0x05 -> "اتصال توسط مقصد رد شد"
        0x06 -> "اتمام TTL"
        0x07 -> "دستور پشتیبانی نمی‌شود"
        0x08 -> "نوع آدرس پشتیبانی نمی‌شود"
        else -> "کد خطای $code"
    }
}
