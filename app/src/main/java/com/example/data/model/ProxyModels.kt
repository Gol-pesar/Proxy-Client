package com.example.data.model

import java.net.URI
import java.net.URLDecoder

enum class ProxyProtocol(val label: String, val defaultPort: Int) {
    SOCKS5("SOCKS5", 1080),
    HTTP("HTTP", 8080),
}

enum class PerAppMode {
    ALL_APPS,
    ALLOW_SELECTED,
    BYPASS_SELECTED,
}

enum class DnsMode {
    PROXY_TCP,
    DIRECT_PROTECTED,
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean,
)

data class ProxyTelemetry(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val activeProfileId: Long? = null,
    val activeProfileName: String = "",
    val activeProtocol: ProxyProtocol? = null,
    val activeEndpoint: String = "",
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val activeTcpSessions: Int = 0,
    val connectedSinceMs: Long = 0L,
    val lastError: String? = null,
)

data class ProxyLogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

data class ParsedProxyUri(
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

object ProxyUriParser {
    fun parse(rawInput: String): Result<ParsedProxyUri> = runCatching {
        val trimmed = rawInput.trim()
        require(trimmed.isNotEmpty()) { "لینک پروکسی خالی است" }

        val normalized = if (trimmed.contains("://")) trimmed else "socks5://$trimmed"
        val uri = URI(normalized)
        val scheme = (uri.scheme ?: "").lowercase()
        val protocol = when (scheme) {
            "socks5", "socks5h", "socks" -> ProxyProtocol.SOCKS5
            "http", "https" -> ProxyProtocol.HTTP
            else -> throw IllegalArgumentException("پروتکل پشتیبانی نمی‌شود: $scheme")
        }

        val host = uri.host?.trim().orEmpty()
        require(host.isNotEmpty()) { "آدرس هاست نامعتبر است" }

        val port = if (uri.port in 1..65535) uri.port else protocol.defaultPort
        val userInfo = uri.rawUserInfo
        var username = ""
        var password = ""
        if (!userInfo.isNullOrEmpty()) {
            val parts = userInfo.split(":", limit = 2)
            username = URLDecoder.decode(parts[0], "UTF-8")
            if (parts.size > 1) {
                password = URLDecoder.decode(parts[1], "UTF-8")
            }
        }

        val fragment = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.trim()
        val name = if (!fragment.isNullOrEmpty()) fragment else "$host:$port"

        ParsedProxyUri(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
        )
    }
}
