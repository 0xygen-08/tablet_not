package com.tabletnotes.app.feature.chat.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.CourseFolderPalette
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.ChatContact
import com.tabletnotes.app.domain.model.ChatMessage
import com.tabletnotes.app.domain.model.ChatMessageType
import com.tabletnotes.app.domain.model.RelayTransferState
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.editor.ui.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenNoteInEditor: (String) -> Unit,
    showGroupsOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val isOnline by viewModel.isOnline.collectAsState()
    val allContacts by viewModel.contacts.collectAsState()
    val selectedContactId by viewModel.selectedContactId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val availableNotes by viewModel.availableNotes.collectAsState()
    val bannerMessage by viewModel.bannerMessage.collectAsState()

    val filteredContacts = remember(allContacts, showGroupsOnly) {
        allContacts.filter { it.isGroup == showGroupsOnly }
    }

    var portraitShowConversation by rememberSaveable(showGroupsOnly) { mutableStateOf(false) }
    var composerText by rememberSaveable { mutableStateOf("") }
    var showNotePickerDialog by rememberSaveable { mutableStateOf(false) }
    var showAddContactDialog by rememberSaveable { mutableStateOf(false) }

    val selectedContact = filteredContacts.firstOrNull { it.id == selectedContactId }
        ?: filteredContacts.firstOrNull()

    BackHandler(enabled = !isLandscape && portraitShowConversation) {
        portraitShowConversation = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            // YATAY (LANDSCAPE) TABLET MODU: Sol Kişi/Grup Listesi + Sağ Aktif Sohbet Paneli
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                ) {
                    ContactsListPane(
                        contacts = filteredContacts,
                        selectedContactId = selectedContact?.id,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSelectContact = { contact ->
                            viewModel.selectContact(contact.id)
                        },
                        onAddClick = { showAddContactDialog = true }
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
                    if (selectedContact == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.chat_section_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                        }
                    } else {
                        ConversationDetailPane(
                            contact = selectedContact,
                            messages = messages,
                            isOnline = isOnline,
                            bannerMessage = bannerMessage,
                            composerText = composerText,
                            onComposerTextChange = { composerText = it },
                            onSendText = {
                                viewModel.sendTextMessage(composerText)
                                composerText = ""
                            },
                            onOpenNotePicker = { showNotePickerDialog = true },
                            onDismissBanner = { viewModel.dismissBanner() },
                            onToggleOffline = { viewModel.toggleSimulatedOffline() },
                            onSimulateIncomingNote = { viewModel.simulateIncomingEncryptedNote() },
                            onDownloadAndDecrypt = { msg ->
                                viewModel.downloadAndDecryptNoteMessage(msg)
                            },
                            onRetryUpload = { msgId -> viewModel.resumeOrRetryNoteUpload(msgId) },
                            onOpenNoteInEditor = onOpenNoteInEditor,
                            showBackButton = false,
                            onBackClick = {}
                        )
                    }
                }
            }
        } else {
            // DİKEY (PORTRAIT) TABLET MODU: Taslak 2. Ekran Görünümü -> Tıklanınca Sohbet Detayı
            if (!portraitShowConversation || selectedContact == null) {
                ContactsListPane(
                    contacts = filteredContacts,
                    selectedContactId = null,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSelectContact = { contact ->
                        viewModel.selectContact(contact.id)
                        portraitShowConversation = true
                    },
                    onAddClick = { showAddContactDialog = true }
                )
            } else {
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(palette.cardCornerRadius),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxSize()
                ) {
                    ConversationDetailPane(
                        contact = selectedContact,
                        messages = messages,
                        isOnline = isOnline,
                        bannerMessage = bannerMessage,
                        composerText = composerText,
                        onComposerTextChange = { composerText = it },
                        onSendText = {
                            viewModel.sendTextMessage(composerText)
                            composerText = ""
                        },
                        onOpenNotePicker = { showNotePickerDialog = true },
                        onDismissBanner = { viewModel.dismissBanner() },
                        onToggleOffline = { viewModel.toggleSimulatedOffline() },
                        onSimulateIncomingNote = { viewModel.simulateIncomingEncryptedNote() },
                        onDownloadAndDecrypt = { msg ->
                            viewModel.downloadAndDecryptNoteMessage(msg)
                        },
                        onRetryUpload = { msgId -> viewModel.resumeOrRetryNoteUpload(msgId) },
                        onOpenNoteInEditor = onOpenNoteInEditor,
                        showBackButton = true,
                        onBackClick = { portraitShowConversation = false }
                    )
                }
            }
        }
    }

    // Not Gönderme Seçici Diyaloğu
    if (showNotePickerDialog) {
        NoteAttachmentPickerDialog(
            notes = availableNotes,
            onDismiss = { showNotePickerDialog = false },
            onSelectNote = { noteSummary ->
                showNotePickerDialog = false
                if (selectedContact != null) {
                    viewModel.sendNoteAttachment(noteSummary, targetContactId = selectedContact.id)
                }
            },
            onSimulateOversize = {
                showNotePickerDialog = false
                val first = availableNotes.firstOrNull()
                if (first != null && selectedContact != null) {
                    viewModel.sendNoteAttachment(
                        noteSummary = first,
                        targetContactId = selectedContact.id,
                        simulateOversizedFile = true
                    )
                }
            }
        )
    }

    // Yeni Arkadaş / Grup Ekleme Diyaloğu
    if (showAddContactDialog) {
        AddContactOrGroupDialog(
            isGroup = showGroupsOnly,
            onDismiss = { showAddContactDialog = false },
            onConfirm = { name, handle ->
                viewModel.addOrFindContact(name, handle, isGroup = showGroupsOnly)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
private fun ContactsListPane(
    contacts: List<ChatContact>,
    selectedContactId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectContact: (ChatContact) -> Unit,
    onAddClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Taslak 2. Ekrandaki Kompakt Pill Arama Çubuğu ("Ara" sol tarafta, Büyüteç sağda — 42dp)
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

            // Kişi / Grup Listesi (Taslak 2. Ekran Düzeni: Ahmet, Mehmet, Ali)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(contacts, key = { _, item -> item.id }) { index, contact ->
                    val isSelected = contact.id == selectedContactId
                    MockupContactRowItem(
                        contact = contact,
                        index = index,
                        isSelected = isSelected,
                        onClick = { onSelectContact(contact) }
                    )
                }
            }
        }

        // Sağ Alt Köşe + Squircle FAB (Taslak 2. Ekran ile Birebir Uyumlu)
        Surface(
            onClick = onAddClick,
            shape = RoundedCornerShape(palette.fabCornerRadius),
            color = palette.primaryAccent,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 4.dp)
                .size(54.dp)
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
    }
}

@Composable
private fun MockupContactRowItem(
    contact: ChatContact,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val courseStyle = CourseFolderPalette.allStyles[index % CourseFolderPalette.allStyles.size]

    val avatarBg = when {
        palette.usePastelFolderCards -> courseStyle.pastelSurfaceLight
        palette.isDark -> palette.surfaceInteractive
        else -> palette.surfaceElevated
    }
    val avatarTextColor = when {
        palette.usePastelFolderCards -> courseStyle.dotLight
        palette.isDark -> palette.primaryAccent
        else -> palette.primaryAccent
    }

    val rowBg = if (isSelected) {
        palette.primaryAccent.copy(alpha = 0.12f)
    } else {
        palette.appBackground.copy(alpha = 0.01f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Yuvarlak Avatar (A, M, A)
        Surface(
            shape = CircleShape,
            color = avatarBg,
            border = if (!palette.usePastelFolderCards && !palette.isDark) {
                BorderStroke(1.dp, palette.borderSubtle)
            } else {
                null
            },
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.avatarInitials.take(1),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = avatarTextColor
                )
            }
        }

        // İsim ve Son Mesaj Önizlemesi
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.lastMessagePreview,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Sağ Tarih ("Dün", "29.01.2026") ve Okunmamış Mesaj Rozeti ("2")
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatContactDateLabel(contact.id, contact.lastMessageTimeMillis),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
            if (contact.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.onPrimaryAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationDetailPane(
    contact: ChatContact,
    messages: List<ChatMessage>,
    isOnline: Boolean,
    bannerMessage: String?,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onOpenNotePicker: () -> Unit,
    onDismissBanner: () -> Unit,
    onToggleOffline: () -> Unit,
    onSimulateIncomingNote: () -> Unit,
    onDownloadAndDecrypt: (ChatMessage) -> Unit,
    onRetryUpload: (String) -> Unit,
    onOpenNoteInEditor: (String) -> Unit,
    showBackButton: Boolean,
    onBackClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var showDevMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Kompakt Sohbet Üst Başlığı
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

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.avatarInitials.take(1),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.primaryAccent
                    )
                }

                Column {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = palette.statusReadyEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.chat_e2ee_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenNotePicker,
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
                        onClick = { showDevMenu = true },
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
                        expanded = showDevMenu,
                        onDismissRequest = { showDevMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_simulate_incoming_btn)) },
                            onClick = {
                                showDevMenu = false
                                onSimulateIncomingNote()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isOnline) {
                                        stringResource(R.string.chat_offline_toggle_online)
                                    } else {
                                        stringResource(R.string.chat_offline_toggle_offline)
                                    }
                                )
                            },
                            onClick = {
                                showDevMenu = false
                                onToggleOffline()
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = palette.borderSubtle)

        if (bannerMessage != null) {
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
                        text = bannerMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissBanner) {
                        Text(text = stringResource(R.string.editor_json_debug_close))
                    }
                }
            }
        }

        // Mesaj Akışı
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.appBackground.copy(alpha = 0.45f)),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onDownloadAndDecrypt = { onDownloadAndDecrypt(msg) },
                    onRetryUpload = { onRetryUpload(msg.id) },
                    onOpenInEditor = { noteId -> onOpenNoteInEditor(noteId) }
                )
            }
        }

        HorizontalDivider(color = palette.borderSubtle)

        // Alt Mesaj Yazma Çubuğu (Kompakt Pill Tasarımı)
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
                    .height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (composerText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_composer_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted
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
            }

            Surface(
                onClick = onSendText,
                shape = CircleShape,
                color = palette.primaryAccent,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send_btn),
                        tint = palette.onPrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onDownloadAndDecrypt: () -> Unit,
    onRetryUpload: () -> Unit,
    onOpenInEditor: (String) -> Unit
) {
    val palette = TabletNotesTheme.palette
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOutgoing) {
        palette.primaryAccent.copy(alpha = 0.16f)
    } else {
        palette.surfaceElevated
    }
    val bubbleBorder = if (message.isOutgoing) {
        palette.primaryAccent.copy(alpha = 0.40f)
    } else {
        palette.borderSubtle
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, bubbleBorder),
            modifier = Modifier.widthIn(min = 180.dp, max = 440.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.type == ChatMessageType.NOTE_ATTACHMENT && message.noteAttachment != null) {
                    NoteAttachmentCard(
                        message = message,
                        onDownloadAndDecrypt = onDownloadAndDecrypt,
                        onRetryUpload = onRetryUpload,
                        onOpenInEditor = onOpenInEditor
                    )
                } else {
                    Text(
                        text = message.textContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = timeFormat.format(Date(message.timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteAttachmentCard(
    message: ChatMessage,
    onDownloadAndDecrypt: () -> Unit,
    onRetryUpload: () -> Unit,
    onOpenInEditor: (String) -> Unit
) {
    val palette = TabletNotesTheme.palette
    val att = message.noteAttachment ?: return
    val state = message.relayState

    val thumbBitmap = remember(att.thumbnailPath) {
        att.thumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = palette.appBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.size(width = 56.dp, height = 72.dp)
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = att.noteTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = att.noteTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.notes_card_meta_format,
                            att.pageCount,
                            att.strokeCount,
                            formatFileSize(att.fileSizeBytes)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                    RelayStatusChip(state = state, progress = att.uploadProgressPercent)
                }
            }

            if (state == RelayTransferState.ENCRYPTING_AND_UPLOADING ||
                state == RelayTransferState.DOWNLOADING_AND_DECRYPTING
            ) {
                LinearProgressIndicator(
                    progress = { (att.uploadProgressPercent / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    !message.isOutgoing && state == RelayTransferState.WAITING_IN_RELAY -> {
                        Button(
                            onClick = onDownloadAndDecrypt,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primaryAccent,
                                contentColor = palette.onPrimaryAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_note_download_decrypt),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    state == RelayTransferState.UPLOAD_INTERRUPTED ||
                        state == RelayTransferState.QUEUED_OFFLINE ||
                        state == RelayTransferState.FAILED_ERROR -> {
                        OutlinedButton(
                            onClick = onRetryUpload,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.primaryAccent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_note_retry_upload),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.primaryAccent
                            )
                        }
                    }

                    message.isOutgoing || state == RelayTransferState.DOWNLOADED_AND_DELETED_FROM_RELAY -> {
                        OutlinedButton(
                            onClick = { onOpenInEditor(att.noteId) },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.border),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_note_open_in_editor),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayStatusChip(state: RelayTransferState, progress: Int) {
    val palette = TabletNotesTheme.palette
    val (icon, label, tint) = when (state) {
        RelayTransferState.QUEUED_OFFLINE -> Triple(
            Icons.Default.CloudOff,
            stringResource(R.string.chat_relay_state_queued),
            palette.secondaryAccent
        )
        RelayTransferState.ENCRYPTING_AND_UPLOADING -> Triple(
            Icons.Default.CloudUpload,
            stringResource(R.string.chat_relay_state_uploading, progress),
            palette.primaryAccent
        )
        RelayTransferState.UPLOAD_INTERRUPTED -> Triple(
            Icons.Default.WarningAmber,
            stringResource(R.string.chat_relay_state_interrupted, progress),
            palette.secondaryAccent
        )
        RelayTransferState.WAITING_IN_RELAY -> Triple(
            Icons.Default.Lock,
            stringResource(R.string.chat_relay_state_waiting),
            palette.primaryAccent
        )
        RelayTransferState.DOWNLOADING_AND_DECRYPTING -> Triple(
            Icons.Default.CloudDownload,
            stringResource(R.string.chat_relay_state_downloading),
            palette.primaryAccent
        )
        RelayTransferState.DOWNLOADED_AND_DELETED_FROM_RELAY -> Triple(
            Icons.Default.CloudDone,
            stringResource(R.string.chat_relay_state_downloaded),
            palette.statusReadyEmerald
        )
        RelayTransferState.EXPIRED_AUTO_DELETED -> Triple(
            Icons.Default.WarningAmber,
            stringResource(R.string.chat_relay_state_expired),
            palette.secondaryAccent
        )
        RelayTransferState.FAILED_TOO_LARGE -> Triple(
            Icons.Default.WarningAmber,
            stringResource(R.string.chat_relay_state_too_large),
            palette.secondaryAccent
        )
        RelayTransferState.FAILED_ERROR -> Triple(
            Icons.Default.WarningAmber,
            stringResource(R.string.chat_relay_state_error),
            palette.secondaryAccent
        )
        RelayTransferState.NONE -> Triple(
            Icons.Default.Lock,
            stringResource(R.string.chat_e2ee_badge),
            palette.textMuted
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun NoteAttachmentPickerDialog(
    notes: List<NoteSummary>,
    onDismiss: () -> Unit,
    onSelectNote: (NoteSummary) -> Unit,
    onSimulateOversize: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = { Text(stringResource(R.string.chat_picker_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp)
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectNote(note) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${note.courseName} › ${note.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.notes_card_meta_format,
                                            note.pageCount,
                                            note.strokeCount,
                                            formatFileSize(note.fileSizeBytes)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onSimulateOversize,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.chat_picker_simulate_oversize),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_text_dialog_cancel))
            }
        }
    )
}

@Composable
private fun AddContactOrGroupDialog(
    isGroup: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }

    val palette = TabletNotesTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Text(
                stringResource(
                    if (isGroup) R.string.chat_add_group_dialog_title else R.string.chat_add_contact_dialog_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.chat_add_contact_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text(stringResource(R.string.chat_add_contact_handle_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, handle) }) {
                Text(stringResource(R.string.chat_add_contact_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_text_dialog_cancel))
            }
        }
    )
}

private fun formatContactDateLabel(contactId: String, timestampMillis: Long): String {
    return when (contactId) {
        "contact_ahmet" -> "Dün"
        "contact_mehmet" -> "29.01.2026"
        "contact_ali" -> "22.01.2026"
        else -> {
            val diff = System.currentTimeMillis() - timestampMillis
            if (diff < 36 * 3_600_000L) {
                "Dün"
            } else {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestampMillis))
            }
        }
    }
}
