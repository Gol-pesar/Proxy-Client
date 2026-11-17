package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LogLevel
import com.example.data.model.ProxyLogEntry
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.MonoCodeStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    logs: List<ProxyLogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filteredLogs = remember(logs, selectedLevel) {
        if (selectedLevel == null) logs else logs.filter { it.level == selectedLevel }
    }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("همه") },
                        modifier = Modifier.testTag("log_filter_all"),
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.INFO,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                        label = { Text("INFO", style = MonoCodeStyle) },
                        modifier = Modifier.testTag("log_filter_info"),
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.WARN,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.WARN) null else LogLevel.WARN },
                        label = { Text("WARN", style = MonoCodeStyle) },
                        modifier = Modifier.testTag("log_filter_warn"),
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.ERROR,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                        label = { Text("ERR", style = MonoCodeStyle) },
                        modifier = Modifier.testTag("log_filter_error"),
                    )
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    enabled = logs.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("clear_logs_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.action_clear_logs))
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(18.dp)
                                    .size(36.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.empty_logs_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.empty_logs_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val levelColor = when (entry.level) {
                            LogLevel.INFO -> CyanSecondary
                            LogLevel.WARN -> AmberTertiary
                            LogLevel.ERROR -> CoralError
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = levelColor.copy(alpha = 0.16f),
                                        ) {
                                            Text(
                                                text = entry.tag,
                                                style = MonoCodeStyle.copy(color = levelColor),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        Text(
                                            text = entry.level.name,
                                            style = MonoCodeStyle.copy(
                                                color = levelColor.copy(alpha = 0.85f),
                                            ),
                                        )
                                    }
                                    Text(
                                        text = timeFormatter.format(Date(entry.timestampMs)),
                                        style = MonoCodeStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
