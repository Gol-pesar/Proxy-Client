package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProxyProfileEntity
import com.example.data.model.ProxyProtocol
import com.example.ui.MainUiState
import com.example.ui.components.ImportUriDialog
import com.example.ui.components.LatencyChip
import com.example.ui.components.ProfileEditorDialog
import com.example.ui.components.ProtocolBadge
import com.example.ui.theme.MonoCodeStyle

@Composable
fun ProfilesScreen(
    uiState: MainUiState,
    onSelectProfile: (Long) -> Unit,
    onSaveProfile: (
        editingId: Long?,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        portText: String,
        username: String,
        password: String,
    ) -> Unit,
    onImportUri: (String) -> Unit,
    onDeleteProfile: (ProxyProfileEntity) -> Unit,
    onTestProfile: (ProxyProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProxyProfileEntity?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
        ) {
            // Top Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${uiState.profiles.size} سرور ذخیره شده",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.testTag("open_import_uri_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_import_uri))
                }
            }

            if (uiState.profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(20.dp)
                                    .size(44.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.no_profile_selected),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.no_profile_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    editingProfile = null
                                    showEditorDialog = true
                                },
                                modifier = Modifier.testTag("empty_create_profile_button"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_add_profile))
                            }
                            FilledTonalButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.testTag("empty_import_uri_button"),
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_import_uri))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.profiles, key = { it.id }) { profile ->
                        val isSelected = uiState.selectedProfile?.id == profile.id
                        val isTesting = profile.id in uiState.testingProfileIds

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable { onSelectProfile(profile.id) }
                                .testTag("profile_card_${profile.id}"),
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) {
                                                Icons.Rounded.CheckCircle
                                            } else {
                                                Icons.Rounded.RadioButtonUnchecked
                                            },
                                            contentDescription = if (isSelected) "انتخاب‌شده" else "انتخاب سرور",
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                ProtocolBadge(protocol = profile.protocol)
                                                Text(
                                                    text = profile.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (profile.hasAuth) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Lock,
                                                        contentDescription = "احراز هویت فعال",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                            Text(
                                                text = profile.endpoint,
                                                style = MonoCodeStyle.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }

                                    LatencyChip(
                                        pingMs = profile.lastPingMs,
                                        lastError = profile.lastTestError,
                                        isTesting = isTesting,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    OutlinedButton(
                                        onClick = { onTestProfile(profile) },
                                        enabled = !isTesting,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("test_profile_${profile.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_test_proxy),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            editingProfile = profile
                                            showEditorDialog = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_profile_${profile.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = stringResource(R.string.action_edit),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteProfile(profile) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_profile_${profile.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingProfile = null
                showEditorDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_profile_fab"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_add_profile),
            )
        }
    }

    if (showEditorDialog) {
        ProfileEditorDialog(
            initialProfile = editingProfile,
            onDismiss = { showEditorDialog = false },
            onSave = { id, name, protocol, host, port, user, pass ->
                onSaveProfile(id, name, protocol, host, port, user, pass)
                showEditorDialog = false
            },
        )
    }

    if (showImportDialog) {
        ImportUriDialog(
            onDismiss = { showImportDialog = false },
            onImport = { uri ->
                onImportUri(uri)
                showImportDialog = false
            },
        )
    }
}
