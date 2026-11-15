package com.tabletnotes.app.feature.nearby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.feature.editor.ui.formatFileSize
import com.tabletnotes.app.feature.nearby.permissions.NearbyPermissionsHelper
import com.tabletnotes.app.feature.nearby.service.NearbySessionState

@Composable
fun NearbyShareScreen(
    viewModel: NearbyShareViewModel,
    onOpenNoteInEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val palette = TabletNotesTheme.palette

    val notes by viewModel.notes.collectAsState()
    val selectedNoteId by viewModel.selectedNoteId.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(NearbyPermissionsHelper.hasAllPermissions(context))
    }
    var showTestMenu by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it } || NearbyPermissionsHelper.hasAllPermissions(context)
    }

    val effectiveSelectedId = selectedNoteId ?: notes.firstOrNull()?.id

    @Composable
    fun NoteSelectionPane(paneModifier: Modifier) {
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(palette.cardCornerRadius),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = paneModifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.nearby_select_note_label),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = stringResource(R.string.nearby_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted
                )

                // Çalışma Zamanı İzin Kartı (Android 8 - 15+)
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hasPermissions) palette.statusReadyEmerald else palette.primaryAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (hasPermissions) palette.statusReadyEmerald else palette.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (hasPermissions) R.string.nearby_permissions_granted
                                    else R.string.nearby_permissions_missing
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary
                            )
                        }

                        if (!hasPermissions) {
                            Button(
                                onClick = {
                                    permissionsLauncher.launch(
                                        NearbyPermissionsHelper.getRequiredPermissions().toTypedArray()
                                    )
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primaryAccent,
                                    contentColor = palette.onPrimaryAccent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.nearby_grant_permissions_btn))
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val isSelected = note.id == effectiveSelectedId
                        Surface(
                            color = if (isSelected) palette.primaryAccent.copy(alpha = 0.14f) else palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) palette.primaryAccent else palette.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.selectNote(note.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${note.courseName} › ${note.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "${note.pageCount} sayfa • ${formatFileSize(note.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PeersAndTransferPane(paneModifier: Modifier) {
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(palette.cardCornerRadius),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = paneModifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!hasPermissions) {
                                    permissionsLauncher.launch(
                                        NearbyPermissionsHelper.getRequiredPermissions().toTypedArray()
                                    )
                                }
                                viewModel.startAdvertising()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdvertising) palette.statusReadyEmerald else palette.primaryAccent,
                                contentColor = palette.onPrimaryAccent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.nearby_start_advertising_btn))
                        }

                        OutlinedButton(
                            onClick = {
                                if (!hasPermissions) {
                                    permissionsLauncher.launch(
                                        NearbyPermissionsHelper.getRequiredPermissions().toTypedArray()
                                    )
                                }
                                viewModel.startDiscovery()
                            },
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (isDiscovering) palette.statusReadyEmerald else palette.border
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                tint = if (isDiscovering) palette.statusReadyEmerald else palette.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nearby_start_discovery_btn),
                                color = palette.textPrimary
                            )
                        }

                        if (isAdvertising || isDiscovering) {
                            OutlinedButton(
                                onClick = { viewModel.stopAll() },
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = stringResource(R.string.nearby_stop_all_btn))
                            }
                        }
                    }

                    // Test / Simülasyon Menüsü (Geliştirici Araçları)
                    Box {
                        Surface(
                            onClick = { showTestMenu = true },
                            shape = CircleShape,
                            color = palette.surfaceInteractive,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Test Araçları",
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showTestMenu,
                            onDismissRequest = { showTestMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nearby_simulate_peer_btn)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                },
                                onClick = {
                                    showTestMenu = false
                                    viewModel.simulateDiscoverAndSendSelectedNote()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nearby_simulate_receive_btn)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                },
                                onClick = {
                                    showTestMenu = false
                                    viewModel.simulateReceiveNearbyNote()
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                // Aktif Not Aktarım Kartı
                val transfer = activeTransfer
                if (transfer != null) {
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.primaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stringResource(R.string.nearby_transfer_progress_title)}: ${transfer.noteTitle}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${transfer.peerName} • ${formatFileSize(transfer.bytesTransferred)} / ${formatFileSize(transfer.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textMuted
                                    )
                                }
                                Text(
                                    text = "${transfer.progressPercent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primaryAccent
                                )
                            }

                            LinearProgressIndicator(
                                progress = { (transfer.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )

                            Text(
                                text = transfer.statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (transfer.state in listOf(NearbySessionState.CONNECTING, NearbySessionState.IN_PROGRESS)) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelActiveTransfer() },
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.nearby_cancel_transfer_btn))
                                    }
                                }

                                if (transfer.state in listOf(NearbySessionState.CANCELLED, NearbySessionState.FAILED)) {
                                    Button(
                                        onClick = { viewModel.retryLastTransfer() },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.primaryAccent,
                                            contentColor = palette.onPrimaryAccent
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.nearby_retry_transfer_btn))
                                    }
                                }

                                if (transfer.state == NearbySessionState.COMPLETED) {
                                    Button(
                                        onClick = { onOpenNoteInEditor(transfer.noteId) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.statusReadyEmerald,
                                            contentColor = palette.onPrimaryAccent
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.chat_note_open_in_editor))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bulunan Yakın Cihazlar Listesi
                Text(
                    text = stringResource(R.string.nearby_discovered_peers_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )

                if (discoveredPeers.isEmpty()) {
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.nearby_no_peers_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(discoveredPeers, key = { it.endpointId }) { peer ->
                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(palette.primaryAccent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TabletAndroid,
                                                contentDescription = null,
                                                tint = palette.primaryAccent
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = peer.deviceName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = palette.textPrimary
                                            )
                                            Text(
                                                text = "Wi-Fi Direct / Bluetooth",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.textMuted
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.sendSelectedNoteToPeer(peer) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.primaryAccent,
                                            contentColor = palette.onPrimaryAccent
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.nearby_send_to_peer_btn))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NoteSelectionPane(
                paneModifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight()
            )
            PeersAndTransferPane(
                paneModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NoteSelectionPane(
                paneModifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            )
            PeersAndTransferPane(
                paneModifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
            )
        }
    }
}
