package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.ProxyProfileEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.DnsMode
import com.example.data.model.LogLevel
import com.example.data.model.PerAppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ProxyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
        const val EXTRA_MANUAL_LAUNCH = "extra_manual_launch"

        private const val NOTIFICATION_CHANNEL_ID = "proxygate_vpn_channel"
        private const val NOTIFICATION_ID = 4201
        private const val TUN_ADDRESS = "10.10.0.2"
        private const val TUN_MTU = 1500

        fun startVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MANUAL_LAUNCH, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnJob: Job? = null
    private var telemetryJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private var tunOut: FileOutputStream? = null
    private var tcpSessionManager: TcpSessionManager? = null
    private val tunWriteLock = Any()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                VpnStateHolder.addLog(LogLevel.INFO, "VPN", "درخواست قطع اتصال از کاربر دریافت شد")
                stopTunnel(errorMessage = null)
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startTunnel()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel() {
        vpnJob?.cancel()
        telemetryJob?.cancel()
        cleanupTunnelResources()

        val initialNotif = buildNotification(
            title = getString(R.string.app_name),
            content = getString(R.string.status_connecting),
        )
        startForegroundCompat(initialNotif)

        vpnJob = serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val settingsStore = SettingsDataStore(applicationContext)
                val settings = settingsStore.getSettingsSnapshot()
                val profileId = settings.selectedProfileId
                val profile = profileId?.let { db.proxyDao().getProfileById(it) }

                if (profile == null) {
                    val msg = getString(R.string.no_profile_selected)
                    VpnStateHolder.addLog(LogLevel.ERROR, "VPN", msg)
                    stopTunnel(errorMessage = msg)
                    return@launch
                }

                VpnStateHolder.setConnecting(
                    profileId = profile.id,
                    profileName = profile.name,
                    protocol = profile.protocol,
                    endpoint = profile.endpoint,
                )
                VpnStateHolder.addLog(
                    LogLevel.INFO,
                    profile.protocol.label,
                    "بررسی اتصال به سرور ${profile.endpoint}…",
                )

                val selectedPackages = db.proxyDao().getSelectedPackages()
                if (settings.perAppMode == PerAppMode.ALLOW_SELECTED && selectedPackages.isEmpty()) {
                    val msg = getString(R.string.empty_allow_list_warning)
                    VpnStateHolder.addLog(LogLevel.WARN, "Per-App", msg)
                    stopTunnel(errorMessage = msg)
                    return@launch
                }

                val builder = Builder()
                    .setSession("ProxyGate - ${profile.name}")
                    .setMtu(TUN_MTU)
                    .addAddress(TUN_ADDRESS, 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setBlocking(true)

                applyPerAppRules(builder, settings.perAppMode, selectedPackages)

                val pfd = builder.establish()
                if (pfd == null) {
                    val msg = getString(R.string.vpn_permission_denied)
                    VpnStateHolder.addLog(LogLevel.ERROR, "VPN", msg)
                    stopTunnel(errorMessage = msg)
                    return@launch
                }

                tunInterface = pfd
                val outputStream = FileOutputStream(pfd.fileDescriptor)
                tunOut = outputStream

                val sessionManager = TcpSessionManager(
                    scope = serviceScope,
                    profile = profile,
                    protectSocket = { sock -> protect(sock) },
                    writePacketToTun = { bytes -> writePacketSafe(bytes) },
                )
                tcpSessionManager = sessionManager

                VpnStateHolder.setConnected(
                    profileId = profile.id,
                    profileName = profile.name,
                    protocol = profile.protocol,
                    endpoint = profile.endpoint,
                )
                VpnStateHolder.addLog(
                    LogLevel.INFO,
                    "TUN",
                    "تونل برقرار شد (${profile.protocol.label} • ${profile.endpoint} • حالت: ${modeLabel(settings.perAppMode)})",
                )

                startTelemetryLoop(profile)
                runPacketLoop(pfd, profile, settings.dnsMode, sessionManager)
            } catch (e: Throwable) {
                val msg = e.message ?: getString(R.string.status_error)
                VpnStateHolder.addLog(LogLevel.ERROR, "VPN", "خطای غیرمنتظره تونل: $msg")
                stopTunnel(errorMessage = msg)
            }
        }
    }

    private fun applyPerAppRules(
        builder: Builder,
        mode: PerAppMode,
        selectedPackages: List<String>,
    ) {
        val selfPkg = packageName
        when (mode) {
            PerAppMode.ALL_APPS -> {
                runCatching { builder.addDisallowedApplication(selfPkg) }
                VpnStateHolder.addLog(LogLevel.INFO, "Per-App", "حالت همه برنامه‌ها فعال است (به‌جز ProxyGate)")
            }
            PerAppMode.ALLOW_SELECTED -> {
                var added = 0
                for (pkg in selectedPackages) {
                    if (pkg == selfPkg) continue
                    if (isPackageInstalled(pkg)) {
                        runCatching {
                            builder.addAllowedApplication(pkg)
                            added++
                        }
                    }
                }
                VpnStateHolder.addLog(LogLevel.INFO, "Per-App", "$added برنامه در لیست مجاز تونل قرار گرفت")
            }
            PerAppMode.BYPASS_SELECTED -> {
                runCatching { builder.addDisallowedApplication(selfPkg) }
                var bypassed = 0
                for (pkg in selectedPackages) {
                    if (pkg == selfPkg) continue
                    if (isPackageInstalled(pkg)) {
                        runCatching {
                            builder.addDisallowedApplication(pkg)
                            bypassed++
                        }
                    }
                }
                VpnStateHolder.addLog(LogLevel.INFO, "Per-App", "$bypassed برنامه از تونل مستثنی (Bypass) شد")
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun runPacketLoop(
        pfd: ParcelFileDescriptor,
        profile: ProxyProfileEntity,
        dnsMode: DnsMode,
        sessionManager: TcpSessionManager,
    ) {
        val input = FileInputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)
        while (serviceScope.isActive && tunInterface != null) {
            val length = try {
                input.read(packet)
            } catch (_: IOException) {
                break
            }
            if (length <= 0) {
                if (length < 0) break
                continue
            }

            val ip = PacketUtils.parseIpHeader(packet, length) ?: continue
            when (ip.protocol) {
                PacketUtils.PROTO_TCP -> {
                    val tcp = PacketUtils.parseTcpHeader(packet, ip) ?: continue
                    sessionManager.handleTcpPacket(packet, ip, tcp)
                }
                PacketUtils.PROTO_UDP -> {
                    val udp = PacketUtils.parseUdpHeader(packet, ip) ?: continue
                    if (udp.dstPort == 53 && udp.payloadLength > 0) {
                        val dnsQuery = packet.copyOfRange(
                            udp.payloadOffset,
                            udp.payloadOffset + udp.payloadLength,
                        )
                        VpnStateHolder.totalTxBytes.addAndGet(dnsQuery.size.toLong())
                        serviceScope.launch(Dispatchers.IO) {
                            handleDnsQuery(profile, dnsMode, ip, udp, dnsQuery)
                        }
                    }
                }
            }
        }
    }

    private fun handleDnsQuery(
        profile: ProxyProfileEntity,
        dnsMode: DnsMode,
        ip: PacketUtils.IpHeader,
        udp: PacketUtils.UdpHeader,
        dnsQuery: ByteArray,
    ) {
        try {
            val dnsReply = when (dnsMode) {
                DnsMode.PROXY_TCP -> {
                    try {
                        ProxyTunnelClient.forwardDnsOverTcp(
                            profile = profile,
                            dnsQuery = dnsQuery,
                            dnsServerIpBytes = ip.dstIp,
                            protectSocket = { sock -> protect(sock) },
                        )
                    } catch (e: Throwable) {
                        VpnStateHolder.addLog(
                            LogLevel.WARN,
                            "DNS",
                            "DNS روی پروکسی ناموفق بود، تلاش با سوکت مستقیم: ${e.message}",
                        )
                        ProxyTunnelClient.forwardDnsDirectProtected(
                            dnsQuery = dnsQuery,
                            dstIpBytes = ip.dstIp,
                            dstPort = udp.dstPort,
                            protectDatagram = { ds -> protect(ds) },
                        )
                    }
                }
                DnsMode.DIRECT_PROTECTED -> {
                    ProxyTunnelClient.forwardDnsDirectProtected(
                        dnsQuery = dnsQuery,
                        dstIpBytes = ip.dstIp,
                        dstPort = udp.dstPort,
                        protectDatagram = { ds -> protect(ds) },
                    )
                }
            }

            VpnStateHolder.totalRxBytes.addAndGet(dnsReply.size.toLong())
            val replyPacket = PacketUtils.buildUdpPacket(
                srcIp = ip.dstIp,
                dstIp = ip.srcIp,
                srcPort = udp.dstPort,
                dstPort = udp.srcPort,
                payload = dnsReply,
            )
            writePacketSafe(replyPacket)
        } catch (e: Throwable) {
            VpnStateHolder.addLog(LogLevel.WARN, "DNS", "خطای DNS: ${e.message}")
        }
    }

    private fun writePacketSafe(packet: ByteArray) {
        synchronized(tunWriteLock) {
            runCatching {
                tunOut?.write(packet)
            }
        }
    }

    private fun startTelemetryLoop(profile: ProxyProfileEntity) {
        telemetryJob = serviceScope.launch {
            var lastRx = 0L
            var lastTx = 0L
            var tick = 0
            while (isActive) {
                delay(1000L)
                val curRx = VpnStateHolder.totalRxBytes.get()
                val curTx = VpnStateHolder.totalTxBytes.get()
                val rxSpeed = (curRx - lastRx).coerceAtLeast(0L)
                val txSpeed = (curTx - lastTx).coerceAtLeast(0L)
                lastRx = curRx
                lastTx = curTx

                VpnStateHolder.updateTraffic(rxSpeedBps = rxSpeed, txSpeedBps = txSpeed)
                val notifText = "${profile.protocol.label} • ${profile.endpoint}  ↓${formatBytes(rxSpeed)}/s  ↑${formatBytes(txSpeed)}/s"
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = getString(R.string.vpn_notification_title),
                        content = notifText,
                    ),
                )

                tick++
                if (tick % 30 == 0) {
                    tcpSessionManager?.reapIdleSessions()
                }
            }
        }
    }

    private fun stopTunnel(errorMessage: String?) {
        VpnStateHolder.setDisconnecting()
        vpnJob?.cancel()
        telemetryJob?.cancel()
        cleanupTunnelResources()
        VpnStateHolder.setDisconnected(errorMessage)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupTunnelResources() {
        tcpSessionManager?.closeAll()
        tcpSessionManager = null
        synchronized(tunWriteLock) {
            runCatching { tunOut?.close() }
            tunOut = null
        }
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    override fun onRevoke() {
        VpnStateHolder.addLog(LogLevel.WARN, "VPN", "مجوز VPN توسط سیستم لغو شد")
        stopTunnel(errorMessage = null)
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanupTunnelResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProxyVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.vpn_notification_stop),
                stopIntent,
            )
            .build()
    }

    private fun modeLabel(mode: PerAppMode): String = when (mode) {
        PerAppMode.ALL_APPS -> "همه برنامه‌ها"
        PerAppMode.ALLOW_SELECTED -> "فقط منتخب‌ها"
        PerAppMode.BYPASS_SELECTED -> "به‌جز منتخب‌ها"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "${bytes}B"
            bytes < 1024L * 1024L -> String.format("%.1fKB", bytes / 1024.0)
            else -> String.format("%.2fMB", bytes / (1024.0 * 1024.0))
        }
    }
}
