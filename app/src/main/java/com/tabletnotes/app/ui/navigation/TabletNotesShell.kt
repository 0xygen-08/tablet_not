package com.tabletnotes.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.components.GraphGridBackground
import com.tabletnotes.app.core.designsystem.theme.CourseFolderPalette
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.domain.model.AccountAuthState
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.chat.auth.AccountSheetDialog
import com.tabletnotes.app.feature.chat.auth.SignedOutChatGateCard
import com.tabletnotes.app.feature.chat.auth.UserAvatarBadge
import com.tabletnotes.app.feature.chat.ui.ChatScreen
import com.tabletnotes.app.feature.chat.ui.ChatViewModel
import com.tabletnotes.app.feature.editor.ui.NoteEditorScreen
import com.tabletnotes.app.feature.editor.ui.NotesWorkspaceViewModel
import com.tabletnotes.app.feature.nearby.ui.NearbyShareScreen
import com.tabletnotes.app.feature.nearby.ui.NearbyShareViewModel

enum class TopTabDestination(
    @StringRes val labelRes: Int
) {
    NOTES(R.string.nav_notes),
    FRIENDS_CHAT(R.string.nav_friends_chat),
    GROUPS_CHAT(R.string.nav_groups_chat)
}

@Composable
fun TabletNotesShell(
    currentTheme: ThemePreset,
    onCycleTheme: () -> Unit,
    onSelectTheme: (ThemePreset) -> Unit = {},
    workspaceViewModel: NotesWorkspaceViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    nearbyViewModel: NearbyShareViewModel = hiltViewModel()
) {
    val palette = TabletNotesTheme.palette
    var selectedTab by rememberSaveable { mutableStateOf(TopTabDestination.NOTES) }
    var showNearbyModal by rememberSaveable { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var folderPendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showShareFromEditorDialog by rememberSaveable { mutableStateOf(false) }
    var showNotesSearchBar by rememberSaveable { mutableStateOf(false) }
    var notesSearchQuery by rememberSaveable { mutableStateOf("") }

    val isEditorOpen by workspaceViewModel.isEditorOpen.collectAsState()
    val noteSummaries by workspaceViewModel.noteSummaries.collectAsState()
    val customCourses by workspaceViewModel.customCourses.collectAsState()
    val selectedCourse by workspaceViewModel.selectedCourseFilter.collectAsState()
    val autosaveStatus by workspaceViewModel.autosaveStatus.collectAsState()
    val accountAuthState by chatViewModel.accountAuthState.collectAsState()
    val authBusy by chatViewModel.authBusy.collectAsState()
    val authErrorRes by chatViewModel.authErrorRes.collectAsState()
    val authInfoRes by chatViewModel.authInfoRes.collectAsState()
    val allContacts by chatViewModel.contacts.collectAsState()
    val defaultNoteTitlePrefix = stringResource(R.string.notes_default_title_format)

    BackHandler(enabled = showNearbyModal) {
        showNearbyModal = false
    }

    BackHandler(enabled = !isEditorOpen && !showNearbyModal && selectedTab != TopTabDestination.NOTES) {
        selectedTab = TopTabDestination.NOTES
    }

    GraphGridBackground(modifier = Modifier.fillMaxSize()) {
        when {
            isEditorOpen -> {
                val activeCourse = workspaceViewModel.editorController.courseName
                val currentCourseNotes = remember(noteSummaries, activeCourse) {
                    noteSummaries
                        .filter { it.courseName.equals(activeCourse, ignoreCase = true) }
                        .sortedByDescending { it.updatedAtMillis }
                }

                NoteEditorScreen(
                    controller = workspaceViewModel.editorController,
                    autosaveStatus = autosaveStatus,
                    courseNotes = currentCourseNotes,
                    onSwitchSubNote = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                    onCreateSubNote = {
                        workspaceViewModel.createAndOpenNewNote(
                            defaultTitlePrefix = defaultNoteTitlePrefix,
                            targetCourse = activeCourse
                        )
                    },
                    onDeleteSubNote = { noteId -> workspaceViewModel.deleteNote(noteId) },
                    onRequestJsonDebug = { workspaceViewModel.buildJsonDebugSnapshot() },
                    onCycleTheme = onCycleTheme,
                    onNavigateBack = { workspaceViewModel.closeEditorAndSaveImmediately() },
                    onShareNoteRequest = {
                        workspaceViewModel.saveNowAndGetSummary {
                            showShareFromEditorDialog = true
                        }
                    }
                )
            }

            showNearbyModal -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            onClick = { showNearbyModal = false },
                            shape = CircleShape,
                            color = palette.surfaceElevated,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.nearby_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                    }

                    NearbyShareScreen(
                        viewModel = nearbyViewModel,
                        onOpenNoteInEditor = { noteId ->
                            showNearbyModal = false
                            workspaceViewModel.openExistingNote(noteId)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabletTopNavigationBar(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        currentTheme = currentTheme,
                        onSelectTheme = onSelectTheme,
                        accountAuthState = accountAuthState,
                        onProfileClick = {
                            chatViewModel.clearAuthFeedback()
                            showProfileDialog = true
                        },
                        onToggleSearch = {
                            showNotesSearchBar = !showNotesSearchBar
                            if (!showNotesSearchBar) notesSearchQuery = ""
                        },
                        onOpenNearbyShare = { showNearbyModal = true },
                        onNewFolderClick = { showNewFolderDialog = true }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            TopTabDestination.NOTES -> NotesFoldersOnlyWorkspace(
                                notes = noteSummaries,
                                courses = customCourses,
                                selectedCourse = selectedCourse,
                                showSearchBar = showNotesSearchBar,
                                searchQuery = notesSearchQuery,
                                onSearchQueryChange = { notesSearchQuery = it },
                                onOpenFolder = { courseName ->
                                    workspaceViewModel.openCourseFolder(
                                        courseName = courseName,
                                        defaultTitlePrefix = defaultNoteTitlePrefix
                                    )
                                },
                                onDeleteFolderRequest = { courseName ->
                                    folderPendingDelete = courseName
                                },
                                onCreateNewNote = {
                                    workspaceViewModel.createAndOpenNewNote(
                                        defaultTitlePrefix = defaultNoteTitlePrefix,
                                        targetCourse = selectedCourse ?: customCourses.firstOrNull() ?: "Fizik"
                                    )
                                }
                            )

                            TopTabDestination.FRIENDS_CHAT -> {
                                if (accountAuthState is AccountAuthState.SignedIn) {
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        onOpenNoteInEditor = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                                        showGroupsOnly = false
                                    )
                                } else {
                                    SignedOutChatGateCard(
                                        onOpenAuthDialog = {
                                            chatViewModel.clearAuthFeedback()
                                            showProfileDialog = true
                                        }
                                    )
                                }
                            }

                            TopTabDestination.GROUPS_CHAT -> {
                                if (accountAuthState is AccountAuthState.SignedIn) {
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        onOpenNoteInEditor = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                                        showGroupsOnly = true
                                    )
                                } else {
                                    SignedOutChatGateCard(
                                        onOpenAuthDialog = {
                                            chatViewModel.clearAuthFeedback()
                                            showProfileDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DİYALOGLAR (Tamamı 24dp yuvarlatılmış dikdörtgen formunda!) ---

    // 1. Firebase Hesap, Giriş, Profil & Benzersiz Kullanıcı Adı Diyaloğu
    if (showProfileDialog || accountAuthState is AccountAuthState.NeedsProfileSetup) {
        AccountSheetDialog(
            authState = accountAuthState,
            isBusy = authBusy,
            errorMessageRes = authErrorRes,
            infoMessageRes = authInfoRes,
            onDismiss = {
                showProfileDialog = false
                chatViewModel.clearAuthFeedback()
            },
            onSignInEmail = { email, password -> chatViewModel.signInWithEmail(email, password) },
            onSignUpEmail = { email, password, displayName, username, avatarPreset ->
                chatViewModel.signUpWithEmail(email, password, displayName, username, avatarPreset)
            },
            onGoogleIdToken = { token -> chatViewModel.signInWithGoogleIdToken(token) },
            onGoogleSignInError = { resId -> chatViewModel.setAuthErrorRes(resId) },
            onCompleteProfileSetup = { displayName, username, avatarPreset ->
                chatViewModel.completeProfileSetup(displayName, username, avatarPreset)
            },
            onUpdateProfile = { displayName, username, avatarPreset ->
                chatViewModel.updateAccountProfile(displayName, username, avatarPreset)
            },
            onSendPasswordReset = { email -> chatViewModel.sendPasswordReset(email) },
            onSignOut = {
                chatViewModel.signOutAccount()
                showProfileDialog = false
            },
            onClearFeedback = { chatViewModel.clearAuthFeedback() }
        )
    }

    // 2. Yeni Ders Klasörü Ekleme Diyaloğu
    if (showNewFolderDialog) {
        var folderName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = { Text(stringResource(R.string.notes_new_folder_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.notes_new_folder_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceViewModel.addCourseFolder(folderName)
                        showNewFolderDialog = false
                    },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.editor_text_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // 3. Ders Klasörü Silme Onay Diyaloğu
    val targetDeleteFolder = folderPendingDelete
    if (targetDeleteFolder != null) {
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = { Text(stringResource(R.string.folder_delete_title)) },
            text = {
                Text(
                    text = stringResource(R.string.folder_delete_confirm_msg, targetDeleteFolder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceViewModel.deleteCourseFolder(targetDeleteFolder)
                        folderPendingDelete = null
                    },
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.editor_text_dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text(stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // 4. Not Paylaşma Diyaloğu (24dp Dikdörtgen — Yazıların Tümü Eksiksiz Görünür)
    if (showShareFromEditorDialog) {
        val activeNoteId = workspaceViewModel.editorController.noteId
        val activeSummary = noteSummaries.firstOrNull { it.id == activeNoteId }

        AlertDialog(
            onDismissRequest = { showShareFromEditorDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.action_share_note),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .widthIn(min = 420.dp)
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            showShareFromEditorDialog = false
                            workspaceViewModel.closeEditorAndSaveImmediately()
                            showNearbyModal = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.primaryAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, palette.primaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = palette.primaryAccent
                            )
                            Text(
                                text = stringResource(R.string.nearby_section_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allContacts, key = { it.id }) { contact ->
                            Surface(
                                onClick = {
                                    if (activeSummary != null) {
                                        chatViewModel.sendNoteAttachment(
                                            noteSummary = activeSummary,
                                            targetContactId = contact.id
                                        )
                                    }
                                    showShareFromEditorDialog = false
                                    workspaceViewModel.closeEditorAndSaveImmediately()
                                    selectedTab = if (contact.isGroup) {
                                        TopTabDestination.GROUPS_CHAT
                                    } else {
                                        TopTabDestination.FRIENDS_CHAT
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareFromEditorDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }
}

@Composable
private fun TabletTopNavigationBar(
    selectedTab: TopTabDestination,
    onSelectTab: (TopTabDestination) -> Unit,
    currentTheme: ThemePreset,
    onSelectTheme: (ThemePreset) -> Unit,
    accountAuthState: AccountAuthState,
    onProfileClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenNearbyShare: () -> Unit,
    onNewFolderClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onProfileClick,
            shape = CircleShape,
            color = palette.surfaceElevated,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (accountAuthState is AccountAuthState.SignedIn) {
                    UserAvatarBadge(
                        displayName = accountAuthState.profile.displayName,
                        avatarPresetId = accountAuthState.profile.avatarPreset,
                        size = 34.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PersonOutline,
                        contentDescription = stringResource(R.string.chat_auth_button),
                        tint = palette.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Surface(
            color = palette.surfaceElevated,
            shape = CircleShape,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopTabDestination.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val pillBg = if (isSelected) palette.primaryAccent else Color.Transparent
                    val pillText = if (isSelected) palette.onPrimaryAccent else palette.textSecondary

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = pillText
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedTab == TopTabDestination.NOTES) {
                Surface(
                    onClick = onToggleSearch,
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search_notes),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box {
                Surface(
                    onClick = { showMenu = true },
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.action_menu),
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(260.dp)
                ) {
                    listOf(
                        ThemePreset.NIGHT_DARK,
                        ThemePreset.PAPER_LIGHT,
                        ThemePreset.PASTEL_SOFT,
                        ThemePreset.SYSTEM_AUTO
                    ).forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(stringResource(preset.titleRes)) },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            trailingIcon = {
                                if (currentTheme == preset) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                showMenu = false
                                onSelectTheme(preset)
                            }
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onNewFolderClick()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nearby_section_title)) },
                        leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenNearbyShare()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_auth_button)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onProfileClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * İlk Ekrandaki "Notlar" Sekmesi:
 * Yalnızca ders klasörü kartlarını (Fizik, Kimya, Mat., Tarih, İng. vb.) gösterir;
 * alttaki alt notlar listesi kaldırılmıştır. Bir klasöre tıklandığında kullanıcı doğrudan o klasörün
 * en son düzenlediği alt notuna açılır.
 */
@Composable
private fun NotesFoldersOnlyWorkspace(
    notes: List<NoteSummary>,
    courses: List<String>,
    selectedCourse: String?,
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onDeleteFolderRequest: (String) -> Unit,
    onCreateNewNote: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val allCourseNames = remember(courses, notes) {
        val merged = LinkedHashSet<String>()
        merged.addAll(courses)
        notes.forEach { if (it.courseName.isNotBlank()) merged.add(it.courseName) }
        if (searchQuery.isBlank()) {
            merged.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            merged.filter { course ->
                course.lowercase().contains(q) ||
                    notes.any { it.courseName.equals(course, ignoreCase = true) && it.title.lowercase().contains(q) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Surface(
                    shape = CircleShape,
                    color = palette.surfaceElevated,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
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
                                    text = stringResource(R.string.action_search_notes),
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
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = if (isLandscape) GridCells.Fixed(4) else GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(allCourseNames, key = { _, name -> name }) { index, courseName ->
                    val count = notes.count { it.courseName.equals(courseName, ignoreCase = true) }
                    val isSelected = selectedCourse.equals(courseName, ignoreCase = true)
                    CourseFolderCard(
                        courseName = courseName,
                        noteCount = count,
                        index = index,
                        isSelected = isSelected,
                        onClick = { onOpenFolder(courseName) },
                        onDeleteClick = { onDeleteFolderRequest(courseName) }
                    )
                }
            }
        }

        Surface(
            onClick = onCreateNewNote,
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
                    contentDescription = stringResource(R.string.action_new_note),
                    tint = palette.onPrimaryAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseFolderCard(
    courseName: String,
    noteCount: Int,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val courseStyle = CourseFolderPalette.styleForCourse(courseName, index)

    val cardBg = when {
        palette.usePastelFolderCards -> courseStyle.pastelSurfaceLight
        else -> palette.surfaceElevated
    }
    val dotColor = if (palette.isDark) courseStyle.dotDark else courseStyle.dotLight
    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, palette.primaryAccent)
        palette.usePastelFolderCards -> null
        else -> BorderStroke(1.dp, palette.borderSubtle)
    }

    Surface(
        onClick = onClick,
        color = cardBg,
        shape = RoundedCornerShape(palette.cardCornerRadius), // 24dp
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.folder_delete_title),
                        tint = palette.textMuted.copy(alpha = 0.78f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.notes_folder_count_format, noteCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    }
}
