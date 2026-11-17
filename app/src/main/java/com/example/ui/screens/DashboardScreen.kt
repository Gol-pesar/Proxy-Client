package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ConnectionState
import com.example.data.model.DnsMode
import com.example.data.model.PerAppMode
import com.example.ui.MainUiState
import com.example.ui.components.LatencyChip
import com.example.ui.components.MetricTile
import com.example.ui.components.PowerTunnelOrb
import com.example.ui.components.ProtocolBadge
import com.example.ui.components.formatByteCount
import com.example.ui.components.formatDurationSeconds
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoCodeStyle
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onToggleVpn: () -> Unit,
    onTestSelectedProfile: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenPerApp: () -> Unit,
    onChangeDnsMode: (DnsMode) -> Unit,
    onRestartVpn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val telemetry = uiState.telemetry
    val selectedProfile = uiState.selectedProfile
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(telemetry.state, telemetry.connectedSinceMs) {
        if (telemetry.state == ConnectionState.CONNECTED && telemetry.connectedSinceMs > 0L) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - telemetry.connectedSinceMs) / 1000L
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val statusText = when (telemetry.state) {
        ConnectionState.CONNECTED -> stringResource(R.string.status_connected)
        ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
        ConnectionState.DISCONNECTING -> stringResource(R.string.status_disconnecting)
        ConnectionState.ERROR -> telemetry.lastError ?: stringResource(R.string.status_error)
        ConnectionState.DISCONNECTED -> stringResource(R.string.status_disconnected)
    }

    val statusColor = when (telemetry.state) {
        ConnectionState.CONNECTED -> EmeraldPrimary
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> CyanSecondary
        ConnectionState.ERROR -> CoralError
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Reconnect banner if Per-App rules changed during active session
            item {
                AnimatedVisibility(visible = uiState.perAppChangesPendingRestart) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = AmberTertiary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AmberTertiary.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = AmberTertiary,
                            )
                            Text(
                                text = stringResource(R.string.per_app_reconnect_banner),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = onRestartVpn,
                                modifier = Modifier.testTag("restart_vpn_banner_button"),
                            ) {
                                Text("اعمال")
                            }
                        }
                    }
                }
            }

            // Hero Tunnel Control Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (telemetry.state == ConnectionState.CONNECTED) {
                            EmeraldPrimary.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        statusColor.copy(alpha = 0.08f),
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                ),
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = statusColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelLarge.copy(color = statusColor),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        PowerTunnelOrb(
                            state = telemetry.state,
                            onClick = onToggleVpn,
                        )

                        Spacer(Modifier.height(6.dp))

                        Button(
                            onClick = onToggleVpn,
                            enabled = telemetry.state != ConnectionState.DISCONNECTING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.state == ConnectionState.CONNECTED) {
                                    CoralError
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (telemetry.state == ConnectionState.CONNECTED) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(48.dp)
                                .testTag("dashboard_connect_cta"),
                        ) {
                            Text(
                                text = when (telemetry.state) {
                                    ConnectionState.CONNECTED, ConnectionState.CONNECTING ->
                                        stringResource(R.string.action_disconnect)
                                    else -> stringResource(R.string.action_connect)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }

            // Active Server Profile Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.active_server_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "تغییر سرور",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenProfiles)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("change_server_link"),
                            )
                        }

                        if (selectedProfile != null) {
                            val isTesting = selectedProfile.id in uiState.testingProfileIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ProtocolBadge(protocol = selectedProfile.protocol)
                                        Text(
                                            text = selectedProfile.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = selectedProfile.endpoint,
                                        style = MonoCodeStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LatencyChip(
                                        pingMs = selectedProfile.lastPingMs,
                                        lastError = selectedProfile.lastTestError,
                                        isTesting = isTesting,
                                    )
                                    OutlinedButton(
                                        onClick = onTestSelectedProfile,
                                        enabled = !isTesting,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("dashboard_ping_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_test_proxy),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.no_profile_selected),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.no_profile_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilledTonalButton(
                                    onClick = onOpenProfiles,
                                    modifier = Modifier.testTag("empty_add_server_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.action_add_profile))
                                }
                            }
                        }
                    }
                }
            }

            // Live Telemetry 2x2 Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            title = stringResource(R.string.traffic_download),
                            value = formatByteCount(telemetry.rxBytes),
                            subtitle = "${formatByteCount(telemetry.rxSpeedBps)}/s",
                            icon = Icons.Rounded.ArrowDownward,
                            accent = EmeraldPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            title = stringResource(R.string.traffic_upload),
                            value = formatByteCount(telemetry.txBytes),
                            subtitle = "${formatByteCount(telemetry.txSpeedBps)}/s",
                            icon = Icons.Rounded.ArrowUpward,
                            accent = CyanSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricTile(
                            title = stringResource(R.string.active_connections),
                            value = telemetry.activeTcpSessions.toString(),
                            icon = Icons.Rounded.Hub,
                            accent = AmberTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            title = stringResource(R.string.session_duration),
                            value = formatDurationSeconds(elapsedSeconds),
                            icon = Icons.Rounded.Timer,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Per-App Proxy Summary & DNS Routing Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenPerApp)
                                .padding(vertical = 4.dp)
                                .testTag("dashboard_perapp_summary"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.per_app_summary_title),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    val summaryDesc = when (uiState.settings.perAppMode) {
                                        PerAppMode.ALL_APPS -> stringResource(R.string.per_app_mode_all)
                                        PerAppMode.ALLOW_SELECTED ->
                                            "${stringResource(R.string.per_app_mode_allow)} (${uiState.selectedAppCount})"
                                        PerAppMode.BYPASS_SELECTED ->
                                            "${stringResource(R.string.per_app_mode_bypass)} (${uiState.selectedAppCount})"
                                    }
                                    Text(
                                        text = summaryDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(R.string.nav_per_app),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.dns_routing_label),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            val isProxyDns = uiState.settings.dnsMode == DnsMode.PROXY_TCP
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onChangeDnsMode(
                                            if (isProxyDns) DnsMode.DIRECT_PROTECTED else DnsMode.PROXY_TCP,
                                        )
                                    }
                                    .testTag("dns_mode_toggle"),
                            ) {
                                Text(
                                    text = if (isProxyDns) {
                                        stringResource(R.string.dns_mode_proxy)
                                    } else {
                                        stringResource(R.string.dns_mode_direct)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
