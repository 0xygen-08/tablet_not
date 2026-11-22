package com.tabletnotes.app.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.AccountAuthState
import com.tabletnotes.app.domain.model.DirectChatSummary
import com.tabletnotes.app.domain.model.FirestoreChatMessage
import com.tabletnotes.app.domain.model.GroupChatSummary
import com.tabletnotes.app.domain.model.GroupRole
import com.tabletnotes.app.domain.model.GroupSendPermission
import com.tabletnotes.app.domain.model.MessageDeliveryStatus
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.feature.chat.auth.UserAvatarBadge
import com.tabletnotes.app.feature.chat.friends.AddFriendAndQrDialog
import com.tabletnotes.app.feature.chat.friends.FriendRequestsAndBlocksDialog
import com.tabletnotes.app.feature.chat.friends.ReportDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenNearbyShare: () -> Unit,
    showGroupsOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val accountAuthState by viewModel.accountAuthState.collectAsState()
    val signedInProfile = (accountAuthState as? AccountAuthState.SignedIn)?.profile ?: return

    // Kota kuralı: yalnızca ChatScreen ekrandayken SnapshotListener bağlanır, çıkınca sökülür
    DisposableEffect(Unit) {
        viewModel.setChatSectionVisible(true)
        onDispose {
            viewModel.setChatSectionVisible(false)
        }
    }

    val isOnline by viewModel.isOnline.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val directItems by viewModel.directChatItems.collectAsState()
    val groupItems by viewModel.groupChatItems.collectAsState()
    val activeTarget by viewModel.activeTarget.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val hasMoreOlder by viewModel.hasMoreOlderMessages.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlderMessages.collectAsState()
    val chatNoticeRes by viewModel.chatNoticeRes.collectAsState()

    val friendships by viewModel.friendships.collectAsState()
    val incomingRequests by viewModel.incomingFriendRequests.collectAsState()
    val outgoingRequests by viewModel.outgoingFriendRequests.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val friendSearchResult by viewModel.friendSearchResult.collectAsState()
    val friendBusy by viewModel.friendBusy.collectAsState()
    val friendErrorRes by viewModel.friendErrorRes.collectAsState()
    val friendInfoRes by viewModel.friendInfoRes.collectAsState()

    var showFabMenu by remember { mutableStateOf(false) }
    var showAddFriendDialog by rememberSaveable { mutableStateOf(false) }
    var showRequestsDialog by rememberSaveable { mutableStateOf(false) }
    var showAttachComingSoonDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateGroupComingSoonNotice by rememberSaveable { mutableStateOf(false) }
    var pendingReport by remember { mutableStateOf<ReportRequest?>(null) }
    var composerText by rememberSaveable(activeTarget) { mutableStateOf("") }

    // Sekme (Arkadaşlar <-> Gruplar) değiştiğinde uyumsuz hedefi temizle
    LaunchedEffect(showGroupsOnly) {
        val current = activeTarget
        if (showGroupsOnly && current is ActiveConversationTarget.Direct) {
            viewModel.closeActiveConversation()
        } else if (!showGroupsOnly && current is ActiveConversationTarget.Group) {
            viewModel.closeActiveConversation()
        }
    }

    val isDetailOpenForCurrentTab = when {
        showGroupsOnly && activeTarget is ActiveConversationTarget.Group -> true
        !showGroupsOnly && activeTarget is ActiveConversationTarget.Direct -> true
        else -> false
    }

    BackHandler(enabled = !isLandscape && isDetailOpenForCurrentTab) {
        viewModel.closeActiveConversation()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Çevrimdışı Önbellek Uyarısı
        if (!isOnline) {
            Surface(
                color = palette.secondaryAccent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.secondaryAccent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = palette.secondaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_offline_banner),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        if (isLandscape) {
            // TABLET YATAY (LANDSCAPE): Sol Liste (340dp) + Sağ Sohbet Paneli
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                ) {
                    ConversationsListPane(
                        showGroupsOnly = showGroupsOnly,
                        directItems = directItems,
                        groupItems = groupItems,
                        incomingRequestsCount = incomingRequests.size,
                        selectedId = when (val t = activeTarget) {
                            is ActiveConversationTarget.Direct -> t.chatId
                            is ActiveConversationTarget.Group -> t.groupId
                            null -> null
                        },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSelectDirect = { summary -> viewModel.openDirectConversation(summary.peerProfile) },
                        onSelectGroup = { grp -> viewModel.openGroupConversation(grp.groupId) },
                        onOpenRequests = { showRequestsDialog = true },
                        showFabMenu = showFabMenu,
                        onToggleFabMenu = { showFabMenu = it },
                        onOpenAddFriend = {
                            viewModel.clearFriendFeedback()
                            showAddFriendDialog = true
                        },
                        onOpenCreateGroup = {
                            showCreateGroupComingSoonNotice = true
                        }
                    )
                }

                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(palette.cardCornerRadius),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ActiveConversationContainer(
                        myProfile = signedInProfile,
                        activeTarget = activeTarget,
                        groupItems = groupItems,
                        messages = activeMessages,
                        hasMoreOlder = hasMoreOlder,
                        isLoadingOlder = isLoadingOlder,
                        noticeRes = chatNoticeRes,
                        composerText = composerText,
                        onComposerTextChange = { composerText = it.take(2000) },
                        onSend = {
                            viewModel.sendMessage(composerText)
                            composerText = ""
                        },
                        onLoadOlder = { viewModel.loadOlderMessages() },
                        onRetryFailed = { viewModel.retryFailedMessage(it) },
                        onDeleteOwnMessage = { viewModel.deleteOwnMessage(it) },
                        onReportUserOrMessage = { pendingReport = it },
                        onUnfriend = { peerUid -> viewModel.rejectCancelOrUnfriend(peerUid) },
                        onBlockUser = { peer -> viewModel.blockUser(peer) },
                        onAttachNoteClick = { showAttachComingSoonDialog = true },
                        onOpenGroupSettings = {},
                        onToggleGroupMute = { gid -> viewModel.toggleGroupMute(gid) },
                        onDismissNotice = { viewModel.dismissChatNotice() },
                        showBackButton = false,
                        onBackClick = {}
                    )
                }
            }
        } else {
            // DİKEY (PORTRAIT) / TELEFON: Tek Panel (Liste <-> Sohbet)
            if (!isDetailOpenForCurrentTab) {
                Box(modifier = Modifier.weight(1f)) {
                    ConversationsListPane(
                        showGroupsOnly = showGroupsOnly,
                        directItems = directItems,
                        groupItems = groupItems,
                        incomingRequestsCount = incomingRequests.size,
                        selectedId = null,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSelectDirect = { summary -> viewModel.openDirectConversation(summary.peerProfile) },
                        onSelectGroup = { grp -> viewModel.openGroupConversation(grp.groupId) },
                        onOpenRequests = { showRequestsDialog = true },
                        showFabMenu = showFabMenu,
                        onToggleFabMenu = { showFabMenu = it },
                        onOpenAddFriend = {
                            viewModel.clearFriendFeedback()
                            showAddFriendDialog = true
                        },
                        onOpenCreateGroup = {
                            showCreateGroupComingSoonNotice = true
                        }
                    )
                }
            } else {
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(palette.cardCornerRadius),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    ActiveConversationContainer(
                        myProfile = signedInProfile,
                        activeTarget = activeTarget,
                        groupItems = groupItems,
                        messages = activeMessages,
                        hasMoreOlder = hasMoreOlder,
                        isLoadingOlder = isLoadingOlder,
                        noticeRes = chatNoticeRes,
                        composerText = composerText,
                        onComposerTextChange = { composerText = it.take(2000) },
                        onSend = {
                            viewModel.sendMessage(composerText)
                            composerText = ""
                        },
                        onLoadOlder = { viewModel.loadOlderMessages() },
                        onRetryFailed = { viewModel.retryFailedMessage(it) },
                        onDeleteOwnMessage = { viewModel.deleteOwnMessage(it) },
                        onReportUserOrMessage = { pendingReport = it },
                        onUnfriend = { peerUid -> viewModel.rejectCancelOrUnfriend(peerUid) },
                        onBlockUser = { peer -> viewModel.blockUser(peer) },
                        onAttachNoteClick = { showAttachComingSoonDialog = true },
                        onOpenGroupSettings = {},
                        onToggleGroupMute = { gid -> viewModel.toggleGroupMute(gid) },
                        onDismissNotice = { viewModel.dismissChatNotice() },
                        showBackButton = true,
                        onBackClick = { viewModel.closeActiveConversation() }
                    )
                }
            }
        }
    }

    // --- DİYALOGLAR (M3 + M4) ---

    if (showAddFriendDialog) {
        AddFriendAndQrDialog(
            myProfile = signedInProfile,
            searchResult = friendSearchResult,
            existingFriendships = friendships,
            blockedUsers = blockedUsers,
            isBusy = friendBusy,
            errorMessageRes = friendErrorRes,
            infoMessageRes = friendInfoRes,
            onSearchExactUsername = { viewModel.searchUserByExactUsername(it) },
            onSendFriendRequest = { viewModel.sendFriendRequest(it) },
            onAcceptRequest = { viewModel.acceptFriendRequest(it) },
            onBlockUser = { viewModel.blockUser(it) },
            onReportUser = { target ->
                showAddFriendDialog = false
                pendingReport = ReportRequest(
                    targetType = "user",
                    targetLabel = "${target.displayName} (@${target.username})",
                    targetUserId = target.uid,
                    targetChatId = null,
                    targetMessageId = null
                )
            },
            onQrScanError = { resId -> viewModel.setFriendErrorRes(resId) },
            onClearState = { viewModel.clearFriendFeedback() },
            onDismiss = { showAddFriendDialog = false }
        )
    }

    if (showRequestsDialog) {
        FriendRequestsAndBlocksDialog(
            incomingRequests = incomingRequests,
            outgoingRequests = outgoingRequests,
            blockedUsers = blockedUsers,
            isBusy = friendBusy,
            onAcceptRequest = { viewModel.acceptFriendRequest(it) },
            onRejectOrCancel = { viewModel.rejectCancelOrUnfriend(it) },
            onUnblockUser = { viewModel.unblockUser(it) },
            onDismiss = { showRequestsDialog = false }
        )
    }

    val currentReport = pendingReport
    if (currentReport != null) {
        ReportDialog(
            targetLabel = currentReport.targetLabel,
            isBusy = friendBusy,
            onSubmitReport = { reason ->
                viewModel.submitReport(currentReport, reason)
                pendingReport = null
            },
            onDismiss = { pendingReport = null }
        )
    }

    if (showAttachComingSoonDialog) {
        AlertDialog(
            onDismissRequest = { showAttachComingSoonDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.chat_attach_coming_soon_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.chat_attach_coming_soon_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAttachComingSoonDialog = false
                        onOpenNearbyShare()
                    },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.chat_attach_go_nearby))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttachComingSoonDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }

    if (showCreateGroupComingSoonNotice) {
        AlertDialog(
            onDismissRequest = { showCreateGroupComingSoonNotice = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = { Text(stringResource(R.string.group_create_title)) },
            text = { Text(stringResource(R.string.chat_empty_groups_list)) },
            confirmButton = {
                TextButton(onClick = { showCreateGroupComingSoonNotice = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }
}

@Composable
private fun ConversationsListPane(
    showGroupsOnly: Boolean,
    directItems: List<DirectChatSummary>,
    groupItems: List<GroupChatSummary>,
    incomingRequestsCount: Int,
    selectedId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectDirect: (DirectChatSummary) -> Unit,
    onSelectGroup: (GroupChatSummary) -> Unit,
    onOpenRequests: () -> Unit,
    showFabMenu: Boolean,
    onToggleFabMenu: (Boolean) -> Unit,
    onOpenAddFriend: () -> Unit,
    onOpenCreateGroup: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Kompakt Pill Arama Çubuğu
            Surface(
                shape = CircleShape,
                color = palette.surfaceElevated,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_search_user_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                            cursorBrush = SolidColor(palette.primaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.chat_search_user_hint),
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Gelen Arkadaşlık İsteği Şeridi
            if (!showGroupsOnly && incomingRequestsCount > 0) {
                Surface(
                    onClick = onOpenRequests,
                    shape = RoundedCornerShape(14.dp),
                    color = palette.primaryAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupervisedUserCircle,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.chat_menu_requests_blocks_fmt, incomingRequestsCount),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }

            if (!showGroupsOnly) {
                if (directItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_friends_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 76.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(directItems, key = { _, item -> item.chatId }) { _, summary ->
                            DirectChatRowItem(
                                summary = summary,
                                isSelected = summary.chatId == selectedId,
                                onClick = { onSelectDirect(summary) }
                            )
                        }
                    }
                }
            } else {
                if (groupItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_groups_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 76.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(groupItems, key = { _, grp -> grp.groupId }) { _, group ->
                            GroupChatRowItem(
                                group = group,
                                isSelected = group.groupId == selectedId,
                                onClick = { onSelectGroup(group) }
                            )
                        }
                    }
                }
            }
        }

        // Sağ Alt "+" FAB + Küçük Menü
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 4.dp)
        ) {
            Surface(
                onClick = { onToggleFabMenu(true) },
                shape = RoundedCornerShape(palette.fabCornerRadius),
                color = palette.primaryAccent,
                shadowElevation = 4.dp,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.chat_add_contact_btn),
                        tint = palette.onPrimaryAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showFabMenu,
                onDismissRequest = { onToggleFabMenu(false) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_add_friend_qr)) },
                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    onClick = {
                        onToggleFabMenu(false)
                        onOpenAddFriend()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (incomingRequestsCount > 0) {
                                stringResource(R.string.chat_menu_requests_blocks_fmt, incomingRequestsCount)
                            } else {
                                stringResource(R.string.chat_menu_requests_blocks)
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.SupervisedUserCircle, contentDescription = null) },
                    onClick = {
                        onToggleFabMenu(false)
                        onOpenRequests()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_new_group)) },
                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                    onClick = {
                        onToggleFabMenu(false)
                        onOpenCreateGroup()
                    }
                )
            }
        }
    }
}

@Composable
private fun DirectChatRowItem(
    summary: DirectChatSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val rowBg = if (isSelected) palette.primaryAccent.copy(alpha = 0.12f) else palette.appBackground.copy(alpha = 0.01f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserAvatarBadge(
            displayName = summary.peerProfile.displayName,
            avatarPresetId = summary.peerProfile.avatarPreset,
            size = 46.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary.peerProfile.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary.lastMessageText.ifBlank {
                    stringResource(R.string.chat_direct_tap_to_start)
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (summary.lastMessageAtMillis > 0L) {
                Text(
                    text = formatCompactDateLabel(summary.lastMessageAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }
            if (summary.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = summary.unreadCount.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.onPrimaryAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChatRowItem(
    group: GroupChatSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val rowBg = if (isSelected) palette.primaryAccent.copy(alpha = 0.12f) else palette.appBackground.copy(alpha = 0.01f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserAvatarBadge(
            displayName = group.name,
            avatarPresetId = group.avatarPreset,
            size = 46.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (group.isMutedLocally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = stringResource(R.string.group_muted_badge),
                        tint = palette.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val preview = if (group.lastMessageText.isNotBlank()) {
                val prefix = group.lastMessageSenderName?.takeIf { it.isNotBlank() }?.let { "$it: " } ?: ""
                prefix + group.lastMessageText
            } else {
                stringResource(R.string.group_members_count_fmt, group.memberIds.size)
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (group.lastMessageAtMillis > 0L) {
                Text(
                    text = formatCompactDateLabel(group.lastMessageAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }
            if (group.hasUnread && !group.isMutedLocally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent)
                )
            }
        }
    }
}

@Composable
private fun ActiveConversationContainer(
    myProfile: PublicUserProfile,
    activeTarget: ActiveConversationTarget?,
    groupItems: List<GroupChatSummary>,
    messages: List<FirestoreChatMessage>,
    hasMoreOlder: Boolean,
    isLoadingOlder: Boolean,
    noticeRes: Int?,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onRetryFailed: (FirestoreChatMessage) -> Unit,
    onDeleteOwnMessage: (String) -> Unit,
    onReportUserOrMessage: (ReportRequest) -> Unit,
    onUnfriend: (String) -> Unit,
    onBlockUser: (PublicUserProfile) -> Unit,
    onAttachNoteClick: () -> Unit,
    onOpenGroupSettings: (GroupChatSummary) -> Unit,
    onToggleGroupMute: (String) -> Unit,
    onDismissNotice: () -> Unit,
    showBackButton: Boolean,
    onBackClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    when (activeTarget) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_section_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        is ActiveConversationTarget.Direct -> {
            ConversationDetailPane(
                myUid = myProfile.uid,
                title = activeTarget.peer.displayName,
                subtitle = "@${activeTarget.peer.username} • ${stringResource(R.string.chat_peer_badge_verified)}",
                avatarPreset = activeTarget.peer.avatarPreset,
                isGroup = false,
                canSendMessages = true,
                messages = messages,
                hasMoreOlder = hasMoreOlder,
                isLoadingOlder = isLoadingOlder,
                noticeRes = noticeRes,
                composerText = composerText,
                onComposerTextChange = onComposerTextChange,
                onSend = onSend,
                onLoadOlder = onLoadOlder,
                onRetryFailed = onRetryFailed,
                onDeleteOwnMessage = onDeleteOwnMessage,
                onReportMessage = { msg ->
                    onReportUserOrMessage(
                        ReportRequest(
                            targetType = "direct_message",
                            targetLabel = "${activeTarget.peer.displayName}: ${msg.text.take(40)}",
                            targetUserId = msg.senderId,
                            targetChatId = activeTarget.chatId,
                            targetMessageId = msg.id
                        )
                    )
                },
                onAttachNoteClick = onAttachNoteClick,
                onDismissNotice = onDismissNotice,
                showBackButton = showBackButton,
                onBackClick = onBackClick,
                menuContent = { closeMenu ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.friends_btn_unfriend)) },
                        leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                        onClick = {
                            closeMenu()
                            onUnfriend(activeTarget.peer.uid)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.friends_btn_block)) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = {
                            closeMenu()
                            onBlockUser(activeTarget.peer)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.friends_btn_report)) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                        onClick = {
                            closeMenu()
                            onReportUserOrMessage(
                                ReportRequest(
                                    targetType = "user",
                                    targetLabel = "${activeTarget.peer.displayName} (@${activeTarget.peer.username})",
                                    targetUserId = activeTarget.peer.uid,
                                    targetChatId = activeTarget.chatId,
                                    targetMessageId = null
                                )
                            )
                        }
                    )
                }
            )
        }

        is ActiveConversationTarget.Group -> {
            val group = groupItems.firstOrNull { it.groupId == activeTarget.groupId }
            if (group == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chat_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted
                    )
                }
            } else {
                val myRole = group.roles[myProfile.uid] ?: GroupRole.MEMBER
                val canSend = group.whoCanSend == GroupSendPermission.EVERYONE ||
                    myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN

                ConversationDetailPane(
                    myUid = myProfile.uid,
                    title = group.name,
                    subtitle = stringResource(R.string.group_members_count_fmt, group.memberIds.size),
                    avatarPreset = group.avatarPreset,
                    isGroup = true,
                    canSendMessages = canSend,
                    messages = messages,
                    hasMoreOlder = hasMoreOlder,
                    isLoadingOlder = isLoadingOlder,
                    noticeRes = noticeRes,
                    composerText = composerText,
                    onComposerTextChange = onComposerTextChange,
                    onSend = onSend,
                    onLoadOlder = onLoadOlder,
                    onRetryFailed = onRetryFailed,
                    onDeleteOwnMessage = onDeleteOwnMessage,
                    onReportMessage = { msg ->
                        onReportUserOrMessage(
                            ReportRequest(
                                targetType = "group_message",
                                targetLabel = "${msg.senderName}: ${msg.text.take(40)}",
                                targetUserId = msg.senderId,
                                targetChatId = group.groupId,
                                targetMessageId = msg.id
                            )
                        )
                    },
                    onAttachNoteClick = onAttachNoteClick,
                    onDismissNotice = onDismissNotice,
                    showBackButton = showBackButton,
                    onBackClick = onBackClick,
                    menuContent = { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_settings_btn)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                closeMenu()
                                onOpenGroupSettings(group)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_mute_local_label)) },
                            leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                            onClick = {
                                closeMenu()
                                onToggleGroupMute(group.groupId)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationDetailPane(
    myUid: String,
    title: String,
    subtitle: String,
    avatarPreset: String,
    isGroup: Boolean,
    canSendMessages: Boolean,
    messages: List<FirestoreChatMessage>,
    hasMoreOlder: Boolean,
    isLoadingOlder: Boolean,
    noticeRes: Int?,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onRetryFailed: (FirestoreChatMessage) -> Unit,
    onDeleteOwnMessage: (String) -> Unit,
    onReportMessage: (FirestoreChatMessage) -> Unit,
    onAttachNoteClick: () -> Unit,
    onDismissNotice: () -> Unit,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    menuContent: @Composable (closeMenu: () -> Unit) -> Unit
) {
    val palette = TabletNotesTheme.palette
    var showMoreMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Üst Başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (showBackButton) {
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        color = palette.surfaceInteractive,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.action_back),
                                tint = palette.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                UserAvatarBadge(
                    displayName = title,
                    avatarPresetId = avatarPreset,
                    size = 38.dp
                )

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Not Ekle (Faz 2'de Yakında + Nearby bilgisi)
                OutlinedButton(
                    onClick = onAttachNoteClick,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.border),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.chat_attach_note_btn),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_menu),
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        menuContent { showMoreMenu = false }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.borderSubtle)

        if (noticeRes != null) {
            Surface(
                color = palette.primaryAccent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, palette.primaryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(noticeRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissNotice) {
                        Text(text = stringResource(R.string.editor_json_debug_close))
                    }
                }
            }
        }

        // Mesaj Listesi (Tarih ayırıcılar + sayfalama)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.appBackground.copy(alpha = 0.45f)),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasMoreOlder) {
                item(key = "load_older_header") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = onLoadOlder,
                            enabled = !isLoadingOlder
                        ) {
                            Text(
                                text = if (isLoadingOlder) {
                                    stringResource(R.string.chat_loading_older)
                                } else {
                                    stringResource(R.string.chat_load_older_btn)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item(key = "empty_msgs") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_messages),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    val showDateHeader = index == 0 ||
                        !isSameDay(messages[index - 1].createdAtMillis, msg.createdAtMillis)
                    if (showDateHeader) {
                        DateSeparatorChip(timestampMillis = msg.createdAtMillis)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    TextChatMessageBubble(
                        message = msg,
                        isMine = msg.senderId == myUid,
                        showSenderName = isGroup && msg.senderId != myUid,
                        onRetry = { onRetryFailed(msg) },
                        onDeleteOwn = { onDeleteOwnMessage(msg.id) },
                        onReport = { onReportMessage(msg) }
                    )
                }
            }
        }

        HorizontalDivider(color = palette.borderSubtle)

        if (!canSendMessages) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.group_admins_only_composer_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceInteractive,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (composerText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_composer_text_only_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = composerText,
                                onValueChange = onComposerTextChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                                cursorBrush = SolidColor(palette.primaryAccent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (composerText.length >= 1700) {
                            Text(
                                text = stringResource(R.string.chat_char_counter_fmt, composerText.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (composerText.length >= 2000) palette.secondaryAccent else palette.textMuted,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onSend,
                    shape = CircleShape,
                    color = if (composerText.isNotBlank()) palette.primaryAccent else palette.surfaceInteractive,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send_btn),
                            tint = if (composerText.isNotBlank()) palette.onPrimaryAccent else palette.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparatorChip(timestampMillis: Long) {
    val palette = TabletNotesTheme.palette
    val today = remember { Calendar.getInstance() }
    val msgCal = remember(timestampMillis) { Calendar.getInstance().apply { timeInMillis = timestampMillis } }

    val label = when {
        isSameCalendarDay(today, msgCal) -> stringResource(R.string.chat_date_today)
        isYesterdayCalendarDay(today, msgCal) -> stringResource(R.string.chat_date_yesterday)
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestampMillis))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = palette.surfaceElevated,
            shape = CircleShape,
            border = BorderStroke(1.dp, palette.borderSubtle)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextChatMessageBubble(
    message: FirestoreChatMessage,
    isMine: Boolean,
    showSenderName: Boolean,
    onRetry: () -> Unit,
    onDeleteOwn: () -> Unit,
    onReport: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMine) palette.primaryAccent.copy(alpha = 0.16f) else palette.surfaceElevated
    val bubbleBorder = when (message.deliveryStatus) {
        MessageDeliveryStatus.FAILED -> palette.secondaryAccent
        else -> if (isMine) palette.primaryAccent.copy(alpha = 0.40f) else palette.borderSubtle
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showMsgMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, bubbleBorder),
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        onClick = {
                            if (isMine && message.deliveryStatus == MessageDeliveryStatus.FAILED) {
                                onRetry()
                            }
                        },
                        onLongClick = { showMsgMenu = true }
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showSenderName && message.senderName.isNotBlank()) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.primaryAccent
                        )
                    }

                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (message.deliveryStatus) {
                            MessageDeliveryStatus.SENDING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = palette.textMuted
                                )
                                Text(
                                    text = stringResource(R.string.chat_msg_status_sending),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            }
                            MessageDeliveryStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = palette.secondaryAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.chat_msg_status_failed),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.secondaryAccent
                                )
                            }
                            MessageDeliveryStatus.SENT -> {
                                Text(
                                    text = timeFormat.format(Date(message.createdAtMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMsgMenu,
                onDismissRequest = { showMsgMenu = false }
            ) {
                if (isMine) {
                    if (message.deliveryStatus == MessageDeliveryStatus.FAILED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_msg_status_failed)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMsgMenu = false
                                onRetry()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_msg_delete)) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            showMsgMenu = false
                            onDeleteOwn()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_msg_report)) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                        onClick = {
                            showMsgMenu = false
                            onReport()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun formatCompactDateLabel(timestampMillis: Long): String {
    val calNow = Calendar.getInstance()
    val calMsg = Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return when {
        isSameCalendarDay(calNow, calMsg) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
        isYesterdayCalendarDay(calNow, calMsg) -> stringResource(R.string.chat_date_yesterday)
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return isSameCalendarDay(c1, c2)
}

private fun isSameCalendarDay(c1: Calendar, c2: Calendar): Boolean =
    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

private fun isYesterdayCalendarDay(today: Calendar, candidate: Calendar): Boolean {
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameCalendarDay(yesterday, candidate)
}
