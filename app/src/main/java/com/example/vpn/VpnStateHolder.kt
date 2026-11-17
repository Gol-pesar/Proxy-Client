package com.example.vpn

import com.example.data.model.ConnectionState
import com.example.data.model.LogLevel
import com.example.data.model.ProxyLogEntry
import com.example.data.model.ProxyProtocol
import com.example.data.model.ProxyTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object VpnStateHolder {
    private const val MAX_LOG_ENTRIES = 250

    private val _telemetry = MutableStateFlow(ProxyTelemetry())
    val telemetry: StateFlow<ProxyTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<ProxyLogEntry>>(emptyList())
    val logs: StateFlow<List<ProxyLogEntry>> = _logs.asStateFlow()

    private val logIdSeq = AtomicLong(1L)
    val totalRxBytes = AtomicLong(0L)
    val totalTxBytes = AtomicLong(0L)
    val activeSessionCount = AtomicInteger(0)

    fun setConnecting(profileId: Long, profileName: String, protocol: ProxyProtocol, endpoint: String) {
        totalRxBytes.set(0L)
        totalTxBytes.set(0L)
        activeSessionCount.set(0)
        _telemetry.value = ProxyTelemetry(
            state = ConnectionState.CONNECTING,
            activeProfileId = profileId,
            activeProfileName = profileName,
            activeProtocol = protocol,
            activeEndpoint = endpoint,
            lastError = null,
        )
    }

    fun setConnected(profileId: Long, profileName: String, protocol: ProxyProtocol, endpoint: String) {
        _telemetry.update {
            it.copy(
                state = ConnectionState.CONNECTED,
                activeProfileId = profileId,
                activeProfileName = profileName,
                activeProtocol = protocol,
                activeEndpoint = endpoint,
                connectedSinceMs = System.currentTimeMillis(),
                lastError = null,
            )
        }
    }

    fun updateTraffic(rxSpeedBps: Long, txSpeedBps: Long) {
        _telemetry.update {
            it.copy(
                rxBytes = totalRxBytes.get(),
                txBytes = totalTxBytes.get(),
                rxSpeedBps = rxSpeedBps,
                txSpeedBps = txSpeedBps,
                activeTcpSessions = activeSessionCount.get().coerceAtLeast(0),
            )
        }
    }

    fun setDisconnected(errorMessage: String? = null) {
        activeSessionCount.set(0)
        _telemetry.value = ProxyTelemetry(
            state = if (errorMessage != null) ConnectionState.ERROR else ConnectionState.DISCONNECTED,
            lastError = errorMessage,
        )
    }

    fun setDisconnecting() {
        _telemetry.update {
            it.copy(state = ConnectionState.DISCONNECTING)
        }
    }

    fun clearError() {
        _telemetry.update {
            if (it.state == ConnectionState.ERROR) {
                it.copy(state = ConnectionState.DISCONNECTED, lastError = null)
            } else {
                it.copy(lastError = null)
            }
        }
    }

    fun addLog(level: LogLevel, tag: String, message: String) {
        val entry = ProxyLogEntry(
            id = logIdSeq.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        _logs.update { current ->
            val next = ArrayList<ProxyLogEntry>(minOf(current.size + 1, MAX_LOG_ENTRIES))
            next.add(entry)
            val keep = minOf(current.size, MAX_LOG_ENTRIES - 1)
            for (i in 0 until keep) {
                next.add(current[i])
            }
            next
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
