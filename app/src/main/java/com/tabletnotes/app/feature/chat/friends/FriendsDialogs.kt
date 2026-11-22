package com.tabletnotes.app.feature.chat.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.BlockedUserRecord
import com.tabletnotes.app.domain.model.FriendshipRecord
import com.tabletnotes.app.domain.model.FriendshipStatus
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.domain.model.UsernameValidator
import com.tabletnotes.app.feature.chat.auth.UserAvatarBadge

@Composable
fun AddFriendAndQrDialog(
    myProfile: PublicUserProfile,
    searchResult: PublicUserProfile?,
    existingFriendships: List<FriendshipRecord>,
    blockedUsers: List<BlockedUserRecord>,
    isBusy: Boolean,
    errorMessageRes: Int?,
    infoMessageRes: Int?,
    onSearchExactUsername: (String) -> Unit,
    onSendFriendRequest: (PublicUserProfile) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onBlockUser: (PublicUserProfile) -> Unit,
    onReportUser: (PublicUserProfile) -> Unit,
    onQrScanError: (Int) -> Unit,
    onClearState: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val context = LocalContext.current
    var showQrTab by rememberSaveable { mutableStateOf(false) }
    var usernameQuery by rememberSaveable { mutableStateOf("") }

    val qrBitmap = remember(myProfile.username) {
        QrCodeHelper.generateUsernameQrBitmap(myProfile.username, sizePx = 420)
    }

    fun startQrScan() {
        onClearState()
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val parsed = QrCodeHelper.parseScannedUsername(barcode.rawValue)
                    if (parsed != null) {
                        showQrTab = false
                        usernameQuery = parsed
                        onSearchExactUsername(parsed)
                    } else {
                        onQrScanError(R.string.friends_qr_invalid)
                    }
                }
                .addOnFailureListener {
                    onQrScanError(R.string.friends_qr_scanner_unavailable)
                }
        }.onFailure {
            onQrScanError(R.string.friends_qr_scanner_unavailable)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.friends_add_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = palette.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 500.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sekme seçici: Kullanıcı Adı Ara | QR Kodum & Tara
                Surface(
                    shape = CircleShape,
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            false to R.string.friends_tab_username_search,
                            true to R.string.friends_tab_qr_code
                        ).forEach { (qrTab, labelRes) ->
                            val selected = showQrTab == qrTab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selected) palette.primaryAccent else Color.Transparent)
                                    .clickable {
                                        showQrTab = qrTab
                                        onClearState()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) palette.onPrimaryAccent else palette.textSecondary
                                )
                            }
                        }
                    }
                }

                if (errorMessageRes != null) {
                    FriendBanner(message = stringResource(errorMessageRes), isError = true)
                }
                if (infoMessageRes != null) {
                    FriendBanner(message = stringResource(infoMessageRes), isError = false)
                }

                if (!showQrTab) {
                    Text(
                        text = stringResource(R.string.friends_exact_search_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = usernameQuery,
                            onValueChange = { usernameQuery = UsernameValidator.normalize(it).take(20) },
                            label = { Text(stringResource(R.string.auth_username_label)) },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSearchExactUsername(usernameQuery) },
                            enabled = !isBusy && UsernameValidator.isValid(UsernameValidator.normalize(usernameQuery)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = palette.onPrimaryAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = ::startQrScan,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.friends_qr_scan_cta))
                    }

                    if (searchResult != null) {
                        val existing = existingFriendships.firstOrNull { it.otherProfile.uid == searchResult.uid }
                        val isBlocked = blockedUsers.any { it.blockedProfile.uid == searchResult.uid }
                        val isSelf = searchResult.uid == myProfile.uid

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = palette.surface,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    UserAvatarBadge(
                                        displayName = searchResult.displayName,
                                        avatarPresetId = searchResult.avatarPreset,
                                        size = 46.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = searchResult.displayName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = "@${searchResult.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.primaryAccent
                                        )
                                    }
                                }

                                if (!isSelf) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        when {
                                            isBlocked -> {
                                                Text(
                                                    text = stringResource(R.string.friends_status_blocked_badge),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = palette.secondaryAccent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            existing?.status == FriendshipStatus.ACCEPTED -> {
                                                Text(
                                                    text = stringResource(R.string.friends_status_already_friends),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = palette.statusReadyEmerald,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            existing?.isIncoming == true -> {
                                                Button(
                                                    onClick = { onAcceptRequest(searchResult.uid) },
                                                    enabled = !isBusy,
                                                    shape = CircleShape,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(stringResource(R.string.friends_btn_accept))
                                                }
                                            }
                                            existing?.status == FriendshipStatus.PENDING -> {
                                                OutlinedButton(
                                                    onClick = {},
                                                    enabled = false,
                                                    shape = CircleShape,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(stringResource(R.string.friends_status_request_sent))
                                                }
                                            }
                                            else -> {
                                                Button(
                                                    onClick = { onSendFriendRequest(searchResult) },
                                                    enabled = !isBusy,
                                                    shape = CircleShape,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(stringResource(R.string.friends_btn_send_request))
                                                }
                                            }
                                        }

                                        if (!isBlocked) {
                                            IconButton(onClick = { onBlockUser(searchResult) }, enabled = !isBusy) {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = stringResource(R.string.friends_btn_block),
                                                    tint = palette.secondaryAccent
                                                )
                                            }
                                            IconButton(onClick = { onReportUser(searchResult) }, enabled = !isBusy) {
                                                Icon(
                                                    imageVector = Icons.Default.Flag,
                                                    contentDescription = stringResource(R.string.friends_btn_report),
                                                    tint = palette.textMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // QR Kodum ve QR Tarama Ekranı
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.friends_my_qr_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        if (qrBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, palette.borderSubtle),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "@${myProfile.username}",
                                    modifier = Modifier
                                        .size(210.dp)
                                        .padding(12.dp)
                                )
                            }
                        }
                        Text(
                            text = "@${myProfile.username}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.primaryAccent
                        )
                        Text(
                            text = stringResource(R.string.friends_my_qr_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = ::startQrScan,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.friends_qr_scan_cta))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_json_debug_close))
            }
        }
    )
}

@Composable
fun FriendRequestsAndBlocksDialog(
    incomingRequests: List<FriendshipRecord>,
    outgoingRequests: List<FriendshipRecord>,
    blockedUsers: List<BlockedUserRecord>,
    isBusy: Boolean,
    onAcceptRequest: (String) -> Unit,
    onRejectOrCancel: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Gelen, 1: Giden, 2: Engellenen

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.friends_requests_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = palette.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 520.dp)
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            0 to stringResource(R.string.friends_tab_incoming_fmt, incomingRequests.size),
                            1 to stringResource(R.string.friends_tab_outgoing_fmt, outgoingRequests.size),
                            2 to stringResource(R.string.friends_tab_blocked_fmt, blockedUsers.size)
                        ).forEach { (idx, label) ->
                            val selected = selectedTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selected) palette.primaryAccent else Color.Transparent)
                                    .clickable { selectedTab = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) palette.onPrimaryAccent else palette.textSecondary
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (incomingRequests.isEmpty()) {
                                item {
                                    EmptyStateMessage(stringResource(R.string.friends_empty_incoming))
                                }
                            } else {
                                items(incomingRequests, key = { it.pairId }) { req ->
                                    UserActionRowCard(
                                        profile = req.otherProfile,
                                        primaryActionLabel = stringResource(R.string.friends_btn_accept),
                                        secondaryActionLabel = stringResource(R.string.friends_btn_reject),
                                        isBusy = isBusy,
                                        onPrimaryClick = { onAcceptRequest(req.otherProfile.uid) },
                                        onSecondaryClick = { onRejectOrCancel(req.otherProfile.uid) }
                                    )
                                }
                            }
                        }
                        1 -> {
                            if (outgoingRequests.isEmpty()) {
                                item {
                                    EmptyStateMessage(stringResource(R.string.friends_empty_outgoing))
                                }
                            } else {
                                items(outgoingRequests, key = { it.pairId }) { req ->
                                    UserActionRowCard(
                                        profile = req.otherProfile,
                                        primaryActionLabel = null,
                                        secondaryActionLabel = stringResource(R.string.friends_btn_cancel_request),
                                        isBusy = isBusy,
                                        onPrimaryClick = {},
                                        onSecondaryClick = { onRejectOrCancel(req.otherProfile.uid) }
                                    )
                                }
                            }
                        }
                        else -> {
                            if (blockedUsers.isEmpty()) {
                                item {
                                    EmptyStateMessage(stringResource(R.string.friends_empty_blocked))
                                }
                            } else {
                                items(blockedUsers, key = { it.blockId }) { block ->
                                    UserActionRowCard(
                                        profile = block.blockedProfile,
                                        primaryActionLabel = stringResource(R.string.friends_btn_unblock),
                                        secondaryActionLabel = null,
                                        isBusy = isBusy,
                                        onPrimaryClick = { onUnblockUser(block.blockedProfile.uid) },
                                        onSecondaryClick = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_json_debug_close))
            }
        }
    )
}

@Composable
fun ReportDialog(
    targetLabel: String,
    isBusy: Boolean,
    onSubmitReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var reason by rememberSaveable { mutableStateOf("") }
    val presets = listOf(
        stringResource(R.string.report_reason_spam),
        stringResource(R.string.report_reason_harassment),
        stringResource(R.string.report_reason_inappropriate)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Text(
                text = stringResource(R.string.report_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.report_target_fmt, targetLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            onClick = { reason = preset },
                            shape = CircleShape,
                            color = if (reason == preset) palette.primaryAccent.copy(alpha = 0.16f) else palette.surface,
                            border = BorderStroke(
                                1.dp,
                                if (reason == preset) palette.primaryAccent else palette.borderSubtle
                            )
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text(stringResource(R.string.report_reason_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReport(reason) },
                enabled = !isBusy && reason.trim().isNotEmpty(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAccent)
            ) {
                Text(stringResource(R.string.report_btn_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_text_dialog_cancel))
            }
        }
    )
}

@Composable
private fun UserActionRowCard(
    profile: PublicUserProfile,
    primaryActionLabel: String?,
    secondaryActionLabel: String?,
    isBusy: Boolean,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatarBadge(
                displayName = profile.displayName,
                avatarPresetId = profile.avatarPreset,
                size = 40.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }
            if (primaryActionLabel != null) {
                Button(
                    onClick = onPrimaryClick,
                    enabled = !isBusy,
                    shape = CircleShape
                ) {
                    Text(primaryActionLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (secondaryActionLabel != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    enabled = !isBusy,
                    shape = CircleShape
                ) {
                    Text(secondaryActionLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FriendBanner(message: String, isError: Boolean) {
    val palette = TabletNotesTheme.palette
    val accent = if (isError) palette.secondaryAccent else palette.statusReadyEmerald
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EmptyStateMessage(text: String) {
    val palette = TabletNotesTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
            textAlign = TextAlign.Center
        )
    }
}
