package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerAppMode
import com.example.ui.MainUiState
import com.example.ui.components.InstalledAppIcon
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.MonoCodeStyle

@Composable
fun PerAppScreen(
    uiState: MainUiState,
    appIconProvider: (String) -> Drawable?,
    onChangeMode: (PerAppMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onToggleApp: (InstalledAppInfo) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onRestartVpn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMode = uiState.settings.perAppMode

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.size(2.dp))

            // Mode Selection Card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val modes = listOf(
                            PerAppMode.ALL_APPS to R.string.per_app_mode_all,
                            PerAppMode.ALLOW_SELECTED to R.string.per_app_mode_allow,
                            PerAppMode.BYPASS_SELECTED to R.string.per_app_mode_bypass,
                        )
                        modes.forEach { (mode, titleRes) ->
                            val selected = currentMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onChangeMode(mode) }
                                    .testTag("per_app_mode_${mode.name.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(titleRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = when (currentMode) {
                            PerAppMode.ALL_APPS -> stringResource(R.string.per_app_mode_all_desc)
                            PerAppMode.ALLOW_SELECTED -> stringResource(R.string.per_app_mode_allow_desc)
                            PerAppMode.BYPASS_SELECTED -> stringResource(R.string.per_app_mode_bypass_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = uiState.perAppChangesPendingRestart) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = AmberTertiary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AmberTertiary.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            modifier = Modifier.testTag("per_app_apply_restart_button"),
                        ) {
                            Text("اعمال")
                        }
                    }
                }
            }

            if (currentMode != PerAppMode.ALL_APPS) {
                // Search & Filters
                OutlinedTextField(
                    value = uiState.appSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "پاک کردن جستجو")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("per_app_search_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = uiState.settings.showSystemApps,
                            onClick = { onToggleShowSystemApps(!uiState.settings.showSystemApps) },
                            label = { Text(stringResource(R.string.show_system_apps)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag("show_system_apps_chip"),
                        )
                        Text(
                            text = stringResource(R.string.selected_count_format, uiState.selectedAppCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSelectAllVisible,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("select_all_apps_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = stringResource(R.string.select_all),
                            )
                        }
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("deselect_all_apps_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveDone,
                                contentDescription = stringResource(R.string.deselect_all),
                            )
                        }
                    }
                }

                if (uiState.isLoadingApps) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onToggleApp(app) }
                                    .testTag("app_rule_row_${app.packageName}"),
                                shape = MaterialTheme.shapes.medium,
                                color = if (app.isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (app.isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    InstalledAppIcon(
                                        packageName = app.packageName,
                                        drawableProvider = appIconProvider,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MonoCodeStyle.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Checkbox(
                                        checked = app.isSelected,
                                        onCheckedChange = { onToggleApp(app) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.per_app_mode_all),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "برای انتخاب یا استثنا کردن برنامه‌های خاص، یکی از حالت‌های «فقط برنامه‌های منتخب» یا «به‌جز برنامه‌های منتخب» را از بالا انتخاب کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        TextButton(
                            onClick = { onChangeMode(PerAppMode.ALLOW_SELECTED) },
                            modifier = Modifier.testTag("switch_to_allow_mode_button"),
                        ) {
                            Text("تنظیم لیست برنامه‌های مجاز")
                        }
                    }
                }
            }
        }
    }
}
