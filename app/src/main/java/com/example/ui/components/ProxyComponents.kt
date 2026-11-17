package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.local.ProxyProfileEntity
import com.example.data.model.ConnectionState
import com.example.data.model.ProxyProtocol
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricStyle
import java.util.Locale

@Composable
fun PowerTunnelOrb(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> EmeraldPrimary
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> CyanSecondary
            ConnectionState.ERROR -> CoralError
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "orb_accent",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_progress",
    )

    val isPulsing = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING

    Box(
        modifier = modifier.size(184.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val baseRadius = size.minDimension * 0.36f
            if (isPulsing) {
                val ringRadius = baseRadius + (size.minDimension * 0.14f) * pulseProgress
                val ringAlpha = (1f - pulseProgress) * 0.45f
                drawCircle(
                    color = accentColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isPulsing) 0.24f else 0.10f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
                center = center,
            )
        }

        Surface(
            modifier = Modifier
                .size(124.dp)
                .clip(CircleShape)
                .testTag("vpn_power_button")
                .minimumInteractiveComponentSize()
                .clickable(
                    enabled = state != ConnectionState.DISCONNECTING,
                    onClick = onClick,
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.5.dp, accentColor.copy(alpha = 0.85f)),
            tonalElevation = 6.dp,
            shadowElevation = if (isPulsing) 14.dp else 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == ConnectionState.CONNECTING || state == ConnectionState.DISCONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(52.dp),
                        color = accentColor,
                        strokeWidth = 3.5.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (state == ConnectionState.CONNECTED) {
                            stringResource(R.string.action_disconnect)
                        } else {
                            stringResource(R.string.action_connect)
                        },
                        tint = accentColor,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ProtocolBadge(
    protocol: ProxyProtocol,
    modifier: Modifier = Modifier,
) {
    val badgeColor = when (protocol) {
        ProxyProtocol.SOCKS5 -> CyanSecondary
        ProxyProtocol.HTTP -> AmberTertiary
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
    ) {
        Text(
            text = protocol.label,
            style = MonoCodeStyle.copy(
                fontWeight = FontWeight.Bold,
                color = badgeColor,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun LatencyChip(
    pingMs: Int?,
    lastError: String?,
    isTesting: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when {
        isTesting -> "در حال تست…" to CyanSecondary
        pingMs != null -> {
            val c = when {
                pingMs < 180 -> EmeraldPrimary
                pingMs < 450 -> AmberTertiary
                else -> CoralError
            }
            "${pingMs}ms" to c
        }
        lastError != null -> "خطا" to CoralError
        else -> "تست نشده" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = if (pingMs != null) MonoCodeStyle.copy(color = color)
                else MaterialTheme.typography.labelSmall.copy(color = color),
            )
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MonoMetricStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MonoCodeStyle.copy(
                        color = accent,
                        fontWeight = FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun InstalledAppIcon(
    packageName: String,
    drawableProvider: (String) -> Drawable?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(packageName) {
        runCatching { drawableProvider(packageName)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = packageName,
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProfileEditorDialog(
    initialProfile: ProxyProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (
        editingId: Long?,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        portText: String,
        username: String,
        password: String,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialProfile?.name ?: "") }
    var protocol by rememberSaveable { mutableStateOf(initialProfile?.protocol ?: ProxyProtocol.SOCKS5) }
    var host by rememberSaveable { mutableStateOf(initialProfile?.host ?: "") }
    var portText by rememberSaveable {
        mutableStateOf(initialProfile?.port?.toString() ?: ProxyProtocol.SOCKS5.defaultPort.toString())
    }
    var portManuallyEdited by rememberSaveable { mutableStateOf(initialProfile != null) }
    var useAuth by rememberSaveable { mutableStateOf(initialProfile?.hasAuth ?: false) }
    var username by rememberSaveable { mutableStateOf(initialProfile?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(initialProfile?.password ?: "") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val hostError = host.isNotBlank() && host.trim().isEmpty()
    val parsedPort = portText.trim().toIntOrNull()
    val portValid = parsedPort != null && parsedPort in 1..65535
    val canSave = host.trim().isNotEmpty() && portValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (initialProfile == null) {
                        stringResource(R.string.profile_dialog_add)
                    } else {
                        stringResource(R.string.profile_dialog_edit)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.field_protocol),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProxyProtocol.entries.forEach { item ->
                        val selected = protocol == item
                        val color = if (item == ProxyProtocol.SOCKS5) CyanSecondary else AmberTertiary
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("protocol_option_${item.name.lowercase()}")
                                .clickable {
                                    protocol = item
                                    if (!portManuallyEdited) {
                                        portText = item.defaultPort.toString()
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = item.label,
                                    style = MonoCodeStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.field_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.field_host)) },
                        placeholder = { Text("192.168.1.1", style = MonoCodeStyle) },
                        isError = hostError,
                        singleLine = true,
                        textStyle = MonoCodeStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .weight(2.1f)
                            .testTag("profile_host_input"),
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.filter { ch -> ch.isDigit() }.take(5)
                            portManuallyEdited = true
                        },
                        label = { Text(stringResource(R.string.field_port)) },
                        isError = portText.isNotEmpty() && !portValid,
                        singleLine = true,
                        textStyle = MonoCodeStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_port_input"),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { useAuth = !useAuth }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.field_auth_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = useAuth,
                        onCheckedChange = { useAuth = it },
                        modifier = Modifier.testTag("profile_auth_switch"),
                    )
                }

                AnimatedVisibility(visible = useAuth) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.field_username)) },
                            singleLine = true,
                            textStyle = MonoCodeStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_username_input"),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            singleLine = true,
                            textStyle = MonoCodeStyle,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = stringResource(R.string.field_password),
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_password_input"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile?.id,
                        name,
                        protocol,
                        host,
                        portText,
                        if (useAuth) username else "",
                        if (useAuth) password else "",
                    )
                },
                enabled = canSave,
                modifier = Modifier.testTag("save_profile_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun ImportUriDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var uriText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(stringResource(R.string.import_uri_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.import_uri_hint),
                    style = MonoCodeStyle.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                OutlinedTextField(
                    value = uriText,
                    onValueChange = { uriText = it },
                    placeholder = {
                        Text(
                            text = "socks5://user:pass@1.2.3.4:1080#Germany",
                            style = MonoCodeStyle,
                        )
                    },
                    textStyle = MonoCodeStyle,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_uri_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(uriText) },
                enabled = uriText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier.testTag("confirm_import_uri_button"),
            ) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

fun formatByteCount(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDurationSeconds(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hrs = s / 3600
    val mins = (s % 3600) / 60
    val secs = s % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
