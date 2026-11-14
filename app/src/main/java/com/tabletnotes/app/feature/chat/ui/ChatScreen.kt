package com.tabletnotes.app.feature.chat.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.components.GraphGridBackground
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
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    val currentUser by viewModel.currentUser.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedContactId by viewModel.selectedContactId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val availableNotes by viewModel.availableNotes.collectAsState()
    val bannerMessage by viewModel.bannerMessage.collectAsState()

    var composerText by rememberSaveable { mutableStateOf("") }
    var showNotePickerDialog by rememberSaveable { mutableStateOf(false) }
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var showAddContactDialog by rememberSaveable { mutableStateOf(false) }

    val selectedContact = contacts.firstOrNull { it.id == selectedContactId }
        ?: contacts.firstOrNull()

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SOL PANEL: Sohbet Listesi, Kullanıcı Arama ve Kişi Ekleme (330.dp)
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(palette.cardCornerRadius),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .width(330.dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Aktif Kullanıcı & E2EE Anahtar Kimliği Başlığı
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentUser.emailOrPhone} • ${currentUser.keyFingerprint}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showAuthDialog = true },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.border),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.chat_auth_button),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                // Arama Kutusu
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.chat_search_user_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.chat_contacts_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textMuted
                    )
                    TextButton(
                        onClick = { showAddContactDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.chat_add_contact_btn),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primaryAccent
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        val isSelected = contact.id == selectedContact?.id
                        ContactRowItem(
                            contact = contact,
                            isSelected = isSelected,
                            onClick = { viewModel.selectContact(contact.id) }
                        )
                    }
                }
            }
        }

        // SAĞ PANEL: 1:1 WhatsApp Tarzı Mesajlaşma ve El Yazısı Not Kartları
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(palette.cardCornerRadius),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (selectedContact == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chat_section_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textMuted
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1:1 Sohbet Üst Başlığı + E2EE Rozeti + Test Kontrolleri
                    ChatConversationHeader(
                        contact = selectedContact,
                        isOnline = isOnline,
                        onToggleOffline = { viewModel.toggleSimulatedOffline() },
                        onSimulateIncomingNote = { viewModel.simulateIncomingEncryptedNote() }
                    )

                    HorizontalDivider(color = palette.borderSubtle)

                    // Bilgilendirme / Hata Bildirim Çubuğu (Banner)
                    if (bannerMessage != null) {
                        Surface(
                            color = palette.primaryAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, palette.primaryAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissBanner() }) {
                                    Text(text = stringResource(R.string.editor_json_debug_close))
                                }
                            }
                        }
                    }

                    // Mesaj Geçmişi Listesi
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onOpenInEditor = { noteId -> onOpenNoteInEditor(noteId) },
                                onDownloadAndDecrypt = {
                                    viewModel.downloadAndDecryptNoteMessage(msg)
                                },
                                onRetryUpload = {
                                    viewModel.resumeOrRetryNoteUpload(msg.id)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = palette.borderSubtle)

                    // Alt Mesaj Yazma ve Not Ekleme Çubuğu (Composer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNotePickerDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.primaryAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = stringResource(R.string.chat_attach_note_btn),
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_attach_note_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary
                            )
                        }

                        OutlinedTextField(
                            value = composerText,
                            onValueChange = { composerText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.chat_composer_hint),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                if (composerText.isNotBlank()) {
                                    viewModel.sendTextMessage(composerText)
                                    composerText = ""
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primaryAccent,
                                contentColor = palette.onPrimaryAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send_btn),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_send_btn),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // Not Seçme Diyaloğu (.tnote Ekleme + 10 MB Sınır Testi)
    if (showNotePickerDialog) {
        AlertDialog(
            onDismissRequest = { showNotePickerDialog = false },
            title = {
                Text(text = stringResource(R.string.chat_picker_dialog_title))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableNotes, key = { it.id }) { note ->
                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.sendNoteAttachment(note, simulateOversizedFile = false)
                                        showNotePickerDialog = false
                                    }
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
                                            text = note.title,
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
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = palette.statusReadyEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (availableNotes.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.sendNoteAttachment(
                                    availableNotes.first(),
                                    simulateOversizedFile = true
                                )
                                showNotePickerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_picker_simulate_oversize),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotePickerDialog = false }) {
                    Text(text = stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // Kullanıcı Girişi (E-posta / Telefon) & E2EE Anahtar Kimliği Diyaloğu
    if (showAuthDialog) {
        var nameInput by remember(currentUser) { mutableStateOf(currentUser.displayName) }
        var handleInput by remember(currentUser) { mutableStateOf(currentUser.emailOrPhone) }

        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text(text = stringResource(R.string.chat_auth_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(text = stringResource(R.string.chat_auth_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = handleInput,
                        onValueChange = { handleInput = it },
                        label = { Text(text = stringResource(R.string.chat_auth_email_phone_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "E2EE Parmak İzi (ECDH P-256 + AES-256-GCM):",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted
                            )
                            Text(
                                text = currentUser.keyFingerprint,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = palette.statusReadyEmerald
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.signInOrUpdateProfile(nameInput, handleInput)
                        showAuthDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.chat_auth_save_login))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text(text = stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // Yeni Kişi Arama / Ekleme Diyaloğu
    if (showAddContactDialog) {
        var newContactName by remember { mutableStateOf("") }
        var newContactHandle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text(text = stringResource(R.string.chat_add_contact_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text(text = stringResource(R.string.chat_add_contact_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newContactHandle,
                        onValueChange = { newContactHandle = it },
                        label = { Text(text = stringResource(R.string.chat_add_contact_handle_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newContactName.isNotBlank() && newContactHandle.isNotBlank()) {
                            viewModel.addOrFindContact(newContactName, newContactHandle)
                            showAddContactDialog = false
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.chat_add_contact_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text(text = stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun ContactRowItem(
    contact: ChatContact,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val bgColor = if (isSelected) palette.primaryAccent.copy(alpha = 0.18f) else palette.surface
    val borderColor = if (isSelected) palette.primaryAccent else palette.borderSubtle

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.primaryAccent.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.avatarInitials,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (contact.isOnline) palette.statusReadyEmerald else palette.textMuted)
                    )
                }
                Text(
                    text = contact.handleOrEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatConversationHeader(
    contact: ChatContact,
    isOnline: Boolean,
    onToggleOffline: () -> Unit,
    onSimulateIncomingNote: () -> Unit
) {
    val palette = TabletNotesTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Surface(
                    color = palette.statusReadyEmerald.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.statusReadyEmerald.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                            color = palette.textSecondary
                        )
                    }
                }
            }
            Text(
                text = contact.handleOrEmail,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Çevrimdışı Kuyruk / Kesinti Test Butonu (M7)
            OutlinedButton(
                onClick = onToggleOffline,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (isOnline) palette.statusReadyEmerald else palette.primaryAccent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isOnline) palette.statusReadyEmerald else palette.primaryAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        if (isOnline) R.string.chat_offline_toggle_online else R.string.chat_offline_toggle_offline
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }

            // Karşıdan Şifreli Not Gelmesini Simüle Et Butonu
            OutlinedButton(
                onClick = onSimulateIncomingNote,
                shape = CircleShape,
                border = BorderStroke(1.dp, palette.border),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.chat_simulate_incoming_btn),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onOpenInEditor: (String) -> Unit,
    onDownloadAndDecrypt: () -> Unit,
    onRetryUpload: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleBg = if (message.isOutgoing) {
        palette.primaryAccent.copy(alpha = 0.20f)
    } else {
        palette.surface
    }
    val bubbleBorder = if (message.isOutgoing) {
        palette.primaryAccent.copy(alpha = 0.55f)
    } else {
        palette.border
    }

    val timeStr = remember(message.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMillis))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleBg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, bubbleBorder),
            modifier = Modifier.widthIn(min = 220.dp, max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.type == ChatMessageType.TEXT || message.noteAttachment == null) {
                    Text(
                        text = message.textContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )
                } else {
                    NoteAttachmentCardContent(
                        message = message,
                        onOpenInEditor = onOpenInEditor,
                        onDownloadAndDecrypt = onDownloadAndDecrypt,
                        onRetryUpload = onRetryUpload
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
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteAttachmentCardContent(
    message: ChatMessage,
    onOpenInEditor: (String) -> Unit,
    onDownloadAndDecrypt: () -> Unit,
    onRetryUpload: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val att = message.noteAttachment ?: return

    val thumbnailBitmap = remember(att.thumbnailPath, message.relayState) {
        val path = att.thumbnailPath ?: return@remember null
        val file = File(path)
        if (file.exists()) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Küçük Önizleme Alanı (Thumbnail veya Şifreli Vektör Önizlemesi)
        Surface(
            color = palette.canvasBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = att.noteTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GraphGridBackground(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = att.noteTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF1F1F1F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Not Başlığı ve Vektör Boyut Bilgisi
        Text(
            text = att.noteTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
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

        // Relay Durum Göstergesi
        val (statusText, statusIcon, statusColor) = when (message.relayState) {
            RelayTransferState.QUEUED_OFFLINE -> Triple(
                stringResource(R.string.chat_relay_state_queued),
                Icons.Default.CloudOff,
                palette.primaryAccent
            )
            RelayTransferState.ENCRYPTING_AND_UPLOADING -> Triple(
                stringResource(R.string.chat_relay_state_uploading, att.uploadProgressPercent),
                Icons.Default.CloudUpload,
                palette.primaryAccent
            )
            RelayTransferState.UPLOAD_INTERRUPTED -> Triple(
                stringResource(R.string.chat_relay_state_interrupted, att.uploadProgressPercent),
                Icons.Default.WarningAmber,
                palette.primaryAccent
            )
            RelayTransferState.WAITING_IN_RELAY -> Triple(
                stringResource(R.string.chat_relay_state_waiting),
                Icons.Default.Lock,
                palette.statusReadyEmerald
            )
            RelayTransferState.DOWNLOADING_AND_DECRYPTING -> Triple(
                stringResource(R.string.chat_relay_state_downloading),
                Icons.Default.CloudDownload,
                palette.primaryAccent
            )
            RelayTransferState.DOWNLOADED_AND_DELETED_FROM_RELAY -> Triple(
                stringResource(R.string.chat_relay_state_downloaded),
                Icons.Default.CloudDone,
                palette.statusReadyEmerald
            )
            RelayTransferState.EXPIRED_AUTO_DELETED -> Triple(
                stringResource(R.string.chat_relay_state_expired),
                Icons.Default.WarningAmber,
                palette.textMuted
            )
            RelayTransferState.FAILED_TOO_LARGE -> Triple(
                stringResource(R.string.chat_relay_state_too_large),
                Icons.Default.WarningAmber,
                Color(0xFFEF4444)
            )
            RelayTransferState.FAILED_ERROR, RelayTransferState.NONE -> Triple(
                stringResource(R.string.chat_relay_state_error),
                Icons.Default.WarningAmber,
                palette.primaryAccent
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }

        if (message.relayState == RelayTransferState.ENCRYPTING_AND_UPLOADING) {
            LinearProgressIndicator(
                progress = { (att.uploadProgressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Eylem Butonları:
        // 1. Karşıdan gelen ve henüz Relay'de bekleyen şifreli not -> "İndir & Şifreyi Çöz"
        // 2. Kesilen / Çevrimdışı bekleyen yükleme -> "Yüklemeyi Devam Ettir / Yeniden Dene"
        // 3. Cihazda hazır olan not -> "Çizim Editöründe Aç"
        when {
            !message.isOutgoing && message.relayState == RelayTransferState.WAITING_IN_RELAY -> {
                Button(
                    onClick = onDownloadAndDecrypt,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryAccent,
                        contentColor = palette.onPrimaryAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.chat_note_download_decrypt))
                }
            }

            message.relayState in listOf(
                RelayTransferState.QUEUED_OFFLINE,
                RelayTransferState.UPLOAD_INTERRUPTED,
                RelayTransferState.FAILED_ERROR
            ) -> {
                OutlinedButton(
                    onClick = onRetryUpload,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.chat_note_retry_upload))
                }
            }

            message.relayState != RelayTransferState.FAILED_TOO_LARGE -> {
                OutlinedButton(
                    onClick = { onOpenInEditor(att.noteId) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_note_open_in_editor),
                        color = palette.textPrimary
                    )
                }
            }
        }
    }
}
