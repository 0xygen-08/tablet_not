package com.tabletnotes.app.feature.chat.groups

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.AvatarPreset
import com.tabletnotes.app.domain.model.FriendshipRecord
import com.tabletnotes.app.domain.model.GroupChatSummary
import com.tabletnotes.app.domain.model.GroupRole
import com.tabletnotes.app.domain.model.GroupSendPermission
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.feature.chat.auth.AvatarPresetSelector
import com.tabletnotes.app.feature.chat.auth.UserAvatarBadge
import com.tabletnotes.app.feature.chat.data.FirestoreMessagingRepository

@Composable
fun CreateGroupDialog(
    friends: List<FriendshipRecord>,
    isBusy: Boolean,
    errorMessageRes: Int?,
    onCreateGroup: (name: String, description: String, avatarPreset: String, members: List<PublicUserProfile>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var groupName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedAvatarId by rememberSaveable { mutableStateOf(AvatarPreset.PURPLE_INITIALS.id) }
    val selectedMemberIds = remember { mutableStateListOf<String>() }

    val maxSelectableFriends = FirestoreMessagingRepository.MAX_GROUP_MEMBERS - 1
    val selectedProfiles = remember(friends, selectedMemberIds.toList()) {
        friends.map { it.otherProfile }.filter { it.uid in selectedMemberIds }
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
                    text = stringResource(R.string.group_create_title),
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessageRes != null) {
                    GroupErrorBanner(stringResource(errorMessageRes))
                }

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(60) },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text(stringResource(R.string.group_desc_label)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                AvatarPresetSelector(
                    displayName = groupName.ifBlank { "G" },
                    selectedAvatarId = selectedAvatarId,
                    onSelect = { selectedAvatarId = it }
                )

                HorizontalDivider(color = palette.borderSubtle)

                Text(
                    text = stringResource(R.string.group_pick_members_fmt, 1 + selectedMemberIds.size),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )

                if (friends.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_no_friends_to_add),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted
                    )
                } else {
                    friends.forEach { friend ->
                        val profile = friend.otherProfile
                        val isChecked = profile.uid in selectedMemberIds
                        val canCheckMore = selectedMemberIds.size < maxSelectableFriends
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isChecked) {
                                        selectedMemberIds.remove(profile.uid)
                                    } else if (canCheckMore) {
                                        selectedMemberIds.add(profile.uid)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked && canCheckMore && profile.uid !in selectedMemberIds) {
                                        selectedMemberIds.add(profile.uid)
                                    } else if (!checked) {
                                        selectedMemberIds.remove(profile.uid)
                                    }
                                }
                            )
                            UserAvatarBadge(
                                displayName = profile.displayName,
                                avatarPresetId = profile.avatarPreset,
                                size = 34.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "@${profile.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateGroup(groupName, description, selectedAvatarId, selectedProfiles)
                },
                enabled = !isBusy && groupName.trim().isNotEmpty(),
                shape = CircleShape
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = palette.onPrimaryAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.group_create_btn))
                }
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
fun GroupSettingsDialog(
    group: GroupChatSummary,
    myUid: String,
    allFriends: List<FriendshipRecord>,
    isBusy: Boolean,
    errorMessageRes: Int?,
    onSaveSettings: (name: String, description: String, avatarPreset: String, whoCanSend: GroupSendPermission) -> Unit,
    onAddMembers: (List<PublicUserProfile>) -> Unit,
    onRemoveMember: (String) -> Unit,
    onSetRole: (targetUid: String, newRole: GroupRole) -> Unit,
    onToggleMute: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val myRole = group.roles[myUid] ?: GroupRole.MEMBER
    val isOwner = myRole == GroupRole.OWNER
    val isAdminOrOwner = isOwner || myRole == GroupRole.ADMIN

    var name by rememberSaveable(group.groupId, group.name) { mutableStateOf(group.name) }
    var description by rememberSaveable(group.groupId, group.description) { mutableStateOf(group.description) }
    var avatarPreset by rememberSaveable(group.groupId, group.avatarPreset) { mutableStateOf(group.avatarPreset) }
    var whoCanSend by rememberSaveable(group.groupId, group.whoCanSend) { mutableStateOf(group.whoCanSend) }
    var showAddMembersPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val availableToAdd = remember(allFriends, group.memberIds) {
        allFriends.map { it.otherProfile }.filter { it.uid !in group.memberIds }
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
                    text = stringResource(R.string.group_settings_title),
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
                    .widthIn(min = 400.dp, max = 540.dp)
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessageRes != null) {
                    GroupErrorBanner(stringResource(errorMessageRes))
                }

                // Yerel Sessize Alma Anahtarı (Cihaz içi kalıcı)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.group_mute_local_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = group.isMutedLocally,
                            onCheckedChange = { onToggleMute() }
                        )
                    }
                }

                // Grup Bilgileri & Mesaj İzni (Owner & Admin düzenleyebilir)
                if (isAdminOrOwner) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text(stringResource(R.string.group_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(300) },
                        label = { Text(stringResource(R.string.group_desc_label)) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AvatarPresetSelector(
                        displayName = name.ifBlank { "G" },
                        selectedAvatarId = avatarPreset,
                        onSelect = { avatarPreset = it }
                    )

                    Text(
                        text = stringResource(R.string.group_who_can_send_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary
                    )
                    Surface(
                        shape = CircleShape,
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                GroupSendPermission.EVERYONE to R.string.group_send_everyone,
                                GroupSendPermission.ADMINS_ONLY to R.string.group_send_admins_only
                            ).forEach { (perm, labelRes) ->
                                val selected = whoCanSend == perm
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(if (selected) palette.primaryAccent else Color.Transparent)
                                        .clickable { whoCanSend = perm }
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

                    Button(
                        onClick = { onSaveSettings(name, description, avatarPreset, whoCanSend) },
                        enabled = !isBusy && name.trim().isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.group_save_settings_btn))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        if (group.description.isNotBlank()) {
                            Text(
                                text = group.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                // Üye Listesi ve Üye Ekleme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.group_members_header_fmt, group.memberIds.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary
                    )
                    if (isAdminOrOwner && group.memberIds.size < FirestoreMessagingRepository.MAX_GROUP_MEMBERS) {
                        TextButton(onClick = { showAddMembersPicker = !showAddMembersPicker }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.group_add_members_btn))
                        }
                    }
                }

                if (showAddMembersPicker && isAdminOrOwner) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (availableToAdd.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.group_no_friends_to_add),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            } else {
                                availableToAdd.forEach { friend ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${friend.displayName} (@${friend.username})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { onAddMembers(listOf(friend)) },
                                            enabled = !isBusy
                                        ) {
                                            Text(stringResource(R.string.chat_add_contact_confirm))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                group.memberIds.forEach { memberUid ->
                    val memberProfile = group.memberProfiles[memberUid] ?: PublicUserProfile(
                        uid = memberUid,
                        username = "uye",
                        displayName = memberUid.take(8)
                    )
                    val role = group.roles[memberUid] ?: GroupRole.MEMBER
                    val roleLabelRes = when (role) {
                        GroupRole.OWNER -> R.string.group_role_owner
                        GroupRole.ADMIN -> R.string.group_role_admin
                        GroupRole.MEMBER -> R.string.group_role_member
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatarBadge(
                            displayName = memberProfile.displayName,
                            avatarPresetId = memberProfile.avatarPreset,
                            size = 36.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = memberProfile.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${memberProfile.username} • ${stringResource(roleLabelRes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (role == GroupRole.OWNER) palette.primaryAccent else palette.textMuted
                            )
                        }

                        if (memberUid != myUid) {
                            if (isOwner && role == GroupRole.MEMBER) {
                                TextButton(
                                    onClick = { onSetRole(memberUid, GroupRole.ADMIN) },
                                    enabled = !isBusy
                                ) {
                                    Text(stringResource(R.string.group_make_admin), style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (isOwner && role == GroupRole.ADMIN) {
                                TextButton(
                                    onClick = { onSetRole(memberUid, GroupRole.MEMBER) },
                                    enabled = !isBusy
                                ) {
                                    Text(stringResource(R.string.group_remove_admin), style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            val canRemove = isOwner || (isAdminOrOwner && role == GroupRole.MEMBER)
                            if (canRemove) {
                                IconButton(
                                    onClick = { onRemoveMember(memberUid) },
                                    enabled = !isBusy,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = stringResource(R.string.group_remove_member),
                                        tint = palette.secondaryAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                if (isOwner) {
                    if (!confirmDelete) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            enabled = !isBusy,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.secondaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = palette.secondaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.group_delete_btn),
                                color = palette.secondaryAccent
                            )
                        }
                    } else {
                        Surface(
                            color = palette.secondaryAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.secondaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.group_delete_confirm_msg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onDeleteGroup,
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAccent),
                                        shape = CircleShape
                                    ) {
                                        Text(stringResource(R.string.editor_text_dialog_delete))
                                    }
                                    TextButton(onClick = { confirmDelete = false }) {
                                        Text(stringResource(R.string.editor_text_dialog_cancel))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onLeaveGroup,
                        enabled = !isBusy,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.secondaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = palette.secondaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.group_leave_btn),
                            color = palette.secondaryAccent
                        )
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
private fun GroupErrorBanner(message: String) {
    val palette = TabletNotesTheme.palette
    Surface(
        color = palette.secondaryAccent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.secondaryAccent),
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
