package com.tabletnotes.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
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
    var showTrashDialog by rememberSaveable { mutableStateOf(false) }
    var showNotesSearchBar by rememberSaveable { mutableStateOf(false) }
    var notesSearchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilterMode by rememberSaveable { mutableStateOf("ALL") } // ALL, FAVORITES, PINNED, veya TAG:<etiket>

    val isEditorOpen by workspaceViewModel.isEditorOpen.collectAsState()
    val noteSummaries by workspaceViewModel.noteSummaries.collectAsState()
    val trashedNotes by workspaceViewModel.trashedNotes.collectAsState()
    val customCourses by workspaceViewModel.customCourses.collectAsState()
    val pinnedCourses by workspaceViewModel.pinnedCourses.collectAsState()
    val selectedCourse by workspaceViewModel.selectedCourseFilter.collectAsState()
    val autosaveStatus by workspaceViewModel.autosaveStatus.collectAsState()
    val workspaceNoticeRes by workspaceViewModel.workspaceNoticeRes.collectAsState()
    val isPortabilityBusy by workspaceViewModel.isPortabilityBusy.collectAsState()
    val accountAuthState by chatViewModel.accountAuthState.collectAsState()
    val authBusy by chatViewModel.authBusy.collectAsState()
    val authErrorRes by chatViewModel.authErrorRes.collectAsState()
    val authInfoRes by chatViewModel.authInfoRes.collectAsState()
    val defaultNoteTitlePrefix = stringResource(R.string.notes_default_title_format)

    val importPdfAsNewNoteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            workspaceViewModel.importPdfAsNewNote(
                uri = uri,
                targetCourse = selectedCourse ?: customCourses.firstOrNull() ?: "Fizik"
            )
        }
    }

    val exportPdfFromShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) workspaceViewModel.exportActiveNoteToPdf(uri)
    }

    val exportPngFromShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) workspaceViewModel.exportCurrentPageToPng(uri)
    }

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
                        .sortedWith(compareByDescending<NoteSummary> { it.isPinned }.thenByDescending { it.updatedAtMillis })
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
                    },
                    isPortabilityBusy = isPortabilityBusy,
                    noticeMessageRes = workspaceNoticeRes,
                    onDismissNotice = { workspaceViewModel.dismissWorkspaceNotice() },
                    onStylusSideButtonActionChange = { workspaceViewModel.setStylusSideButtonAction(it) },
                    onImportGalleryImage = { uri -> workspaceViewModel.importGalleryImageIntoActiveNote(uri) },
                    onImportPdfPages = { uri -> workspaceViewModel.appendPdfPagesToActiveNote(uri) },
                    onExportPdf = { uri -> workspaceViewModel.exportActiveNoteToPdf(uri) },
                    onExportPng = { uri -> workspaceViewModel.exportCurrentPageToPng(uri) }
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
                        trashedCount = trashedNotes.size,
                        onProfileClick = {
                            chatViewModel.clearAuthFeedback()
                            showProfileDialog = true
                        },
                        onToggleSearch = {
                            showNotesSearchBar = !showNotesSearchBar
                            if (!showNotesSearchBar) notesSearchQuery = ""
                        },
                        onOpenNearbyShare = { showNearbyModal = true },
                        onNewFolderClick = { showNewFolderDialog = true },
                        onOpenTrashBin = { showTrashDialog = true }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            TopTabDestination.NOTES -> NotesFoldersOnlyWorkspace(
                                notes = noteSummaries,
                                courses = customCourses,
                                pinnedCourses = pinnedCourses,
                                selectedCourse = selectedCourse,
                                showSearchBar = showNotesSearchBar,
                                searchQuery = notesSearchQuery,
                                activeFilterMode = activeFilterMode,
                                noticeRes = workspaceNoticeRes,
                                onDismissNotice = { workspaceViewModel.dismissWorkspaceNotice() },
                                onFilterModeChange = { activeFilterMode = it },
                                onSearchQueryChange = { notesSearchQuery = it },
                                onOpenFolder = { courseName ->
                                    workspaceViewModel.openCourseFolder(
                                        courseName = courseName,
                                        defaultTitlePrefix = defaultNoteTitlePrefix
                                    )
                                },
                                onTogglePinFolder = { courseName ->
                                    workspaceViewModel.toggleCoursePinned(courseName)
                                },
                                onDeleteFolderRequest = { courseName ->
                                    folderPendingDelete = courseName
                                },
                                onOpenNoteDirect = { noteId ->
                                    workspaceViewModel.openExistingNote(noteId)
                                },
                                onToggleNoteFavorite = { summary ->
                                    workspaceViewModel.toggleNoteFavorite(summary)
                                },
                                onToggleNotePin = { summary ->
                                    workspaceViewModel.toggleNotePinned(summary)
                                },
                                onCreateNewNote = {
                                    workspaceViewModel.createAndOpenNewNote(
                                        defaultTitlePrefix = defaultNoteTitlePrefix,
                                        targetCourse = selectedCourse ?: customCourses.firstOrNull() ?: "Fizik"
                                    )
                                },
                                onImportPdfAsNewNote = {
                                    importPdfAsNewNoteLauncher.launch(arrayOf("application/pdf"))
                                },
                                onNewFolderRequest = { showNewFolderDialog = true }
                            )

                            TopTabDestination.FRIENDS_CHAT -> {
                                if (accountAuthState is AccountAuthState.SignedIn) {
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        onOpenNearbyShare = { showNearbyModal = true },
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
                                        onOpenNearbyShare = { showNearbyModal = true },
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
            onDeleteAccount = { password ->
                chatViewModel.deleteAccountWithPassword(password) {
                    showProfileDialog = false
                }
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

    // 4. Not Paylaşma ve PDF/PNG Dışa Aktarma Diyaloğu (24dp Dikdörtgen)
    if (showShareFromEditorDialog) {
        val safeName = workspaceViewModel.editorController.noteTitle
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
            .ifBlank { "not" }
        val activePageNum = workspaceViewModel.editorController.getCurrentVisiblePageIndex() + 1

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
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            showShareFromEditorDialog = false
                            exportPdfFromShareLauncher.launch("$safeName.pdf")
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = palette.primaryAccent
                            )
                            Text(
                                text = stringResource(R.string.editor_export_pdf),
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            showShareFromEditorDialog = false
                            exportPngFromShareLauncher.launch("${safeName}_s$activePageNum.png")
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = palette.primaryAccent
                            )
                            Text(
                                text = stringResource(R.string.editor_export_png),
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                        }
                    }

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

                    Text(
                        text = stringResource(R.string.chat_editor_share_text_only_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareFromEditorDialog = false }) {
                    Text(stringResource(R.string.editor_json_debug_close))
                }
            }
        )
    }

    // 5. Çöp Kutusu (Geri Dönüşüm Kutusu) Diyaloğu
    if (showTrashDialog) {
        TrashBinDialog(
            trashedNotes = trashedNotes,
            onRestore = { summary -> workspaceViewModel.restoreNoteFromTrash(summary) },
            onDeleteForever = { noteId -> workspaceViewModel.permanentlyDeleteNote(noteId) },
            onEmptyTrash = { workspaceViewModel.emptyTrash() },
            onDismiss = { showTrashDialog = false }
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
    trashedCount: Int,
    onProfileClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenNearbyShare: () -> Unit,
    onNewFolderClick: () -> Unit,
    onOpenTrashBin: () -> Unit
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
                        text = { Text(stringResource(R.string.trash_menu_item, trashedCount)) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenTrashBin()
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
 * - Varsayılan olarak ("Tümü" modunda ve arama kapalıyken) yalnızca ders klasörü kartlarını gösterir
 *   (sabitlenen klasörler en başta görünür).
 * - "★ Favoriler", "📌 Sabitlenenler", dinamik "#etiket" filtreleri veya arama aktif olduğunda
 *   eşleşen not kartlarını gösterir.
 * - Sağ alttaki "+" menüsünden Yeni Not, PDF İçe Aktar (Yeni Not) ve Yeni Ders Klasörü oluşturulabilir.
 */
@Composable
private fun NotesFoldersOnlyWorkspace(
    notes: List<NoteSummary>,
    courses: List<String>,
    pinnedCourses: Set<String>,
    selectedCourse: String?,
    showSearchBar: Boolean,
    searchQuery: String,
    activeFilterMode: String,
    noticeRes: Int?,
    onDismissNotice: () -> Unit,
    onFilterModeChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onTogglePinFolder: (String) -> Unit,
    onDeleteFolderRequest: (String) -> Unit,
    onOpenNoteDirect: (String) -> Unit,
    onToggleNoteFavorite: (NoteSummary) -> Unit,
    onToggleNotePin: (NoteSummary) -> Unit,
    onCreateNewNote: () -> Unit,
    onImportPdfAsNewNote: () -> Unit,
    onNewFolderRequest: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var showFabMenu by remember { mutableStateOf(false) }

    val availableTags = remember(notes) {
        notes.flatMap { it.tags }.distinct().sorted()
    }

    val allCourseNames = remember(courses, notes, pinnedCourses) {
        val merged = LinkedHashSet<String>()
        merged.addAll(courses)
        notes.forEach { if (it.courseName.isNotBlank()) merged.add(it.courseName) }
        merged.sortedWith(
            compareByDescending<String> { c -> pinnedCourses.any { it.equals(c, ignoreCase = true) } }
        )
    }

    val showNoteListInsteadOfFolders = activeFilterMode != "ALL" || searchQuery.isNotBlank()

    val filteredNotes = remember(notes, activeFilterMode, searchQuery) {
        val q = searchQuery.trim().lowercase()
        notes.filter { note ->
            val modeOk = when {
                activeFilterMode == "FAVORITES" -> note.isFavorite
                activeFilterMode == "PINNED" -> note.isPinned
                activeFilterMode.startsWith("TAG:") -> {
                    val targetTag = activeFilterMode.removePrefix("TAG:")
                    targetTag in note.tags
                }
                else -> true
            }
            val searchOk = if (q.isBlank()) {
                true
            } else {
                note.title.lowercase().contains(q) ||
                    note.courseName.lowercase().contains(q) ||
                    note.tags.any { it.lowercase().contains(q) }
            }
            modeOk && searchOk
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (noticeRes != null) {
                Surface(
                    color = palette.primaryAccent.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
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
                            Text(stringResource(R.string.editor_json_debug_close), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

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

            // Favori, Sabitlenenler & Dinamik #Etiket Filtre Çubuğu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotesFilterChip(
                    label = stringResource(R.string.notes_filter_all),
                    selected = activeFilterMode == "ALL",
                    onClick = { onFilterModeChange("ALL") }
                )
                NotesFilterChip(
                    label = stringResource(R.string.notes_filter_favorites),
                    selected = activeFilterMode == "FAVORITES",
                    onClick = {
                        onFilterModeChange(if (activeFilterMode == "FAVORITES") "ALL" else "FAVORITES")
                    }
                )
                NotesFilterChip(
                    label = stringResource(R.string.notes_filter_pinned),
                    selected = activeFilterMode == "PINNED",
                    onClick = {
                        onFilterModeChange(if (activeFilterMode == "PINNED") "ALL" else "PINNED")
                    }
                )
                availableTags.forEach { tag ->
                    val mode = "TAG:$tag"
                    NotesFilterChip(
                        label = "#$tag",
                        selected = activeFilterMode == mode,
                        onClick = {
                            onFilterModeChange(if (activeFilterMode == mode) "ALL" else mode)
                        }
                    )
                }
            }

            if (!showNoteListInsteadOfFolders) {
                LazyVerticalGrid(
                    columns = if (isLandscape) GridCells.Fixed(4) else GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(allCourseNames, key = { _, name -> name }) { index, courseName ->
                        val count = notes.count { it.courseName.equals(courseName, ignoreCase = true) }
                        val isSelected = selectedCourse.equals(courseName, ignoreCase = true)
                        val isPinned = pinnedCourses.any { it.equals(courseName, ignoreCase = true) }
                        CourseFolderCard(
                            courseName = courseName,
                            noteCount = count,
                            index = index,
                            isSelected = isSelected,
                            isPinned = isPinned,
                            onClick = { onOpenFolder(courseName) },
                            onTogglePinClick = { onTogglePinFolder(courseName) },
                            onDeleteClick = { onDeleteFolderRequest(courseName) }
                        )
                    }
                }
            } else if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.notes_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isLandscape) GridCells.Fixed(3) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { summary ->
                        FilteredNoteCard(
                            summary = summary,
                            onClick = { onOpenNoteDirect(summary.id) },
                            onToggleFavorite = { onToggleNoteFavorite(summary) },
                            onTogglePin = { onToggleNotePin(summary) }
                        )
                    }
                }
            }
        }

        // Sağ Alt "+" FAB + Menü (Yeni Not / PDF İçe Aktar / Yeni Ders Klasörü)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 4.dp)
        ) {
            Surface(
                onClick = { showFabMenu = true },
                shape = RoundedCornerShape(palette.fabCornerRadius),
                color = palette.primaryAccent,
                shadowElevation = 4.dp,
                modifier = Modifier.size(54.dp)
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

            DropdownMenu(
                expanded = showFabMenu,
                onDismissRequest = { showFabMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_new_note)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                    onClick = {
                        showFabMenu = false
                        onCreateNewNote()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notes_fab_import_pdf)) },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                    onClick = {
                        showFabMenu = false
                        onImportPdfAsNewNote()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_new_folder)) },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        showFabMenu = false
                        onNewFolderRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun NotesFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) palette.primaryAccent else palette.surfaceElevated,
        border = BorderStroke(1.dp, if (selected) palette.primaryAccent else palette.borderSubtle)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) palette.onPrimaryAccent else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilteredNoteCard(
    summary: NoteSummary,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val courseStyle = CourseFolderPalette.styleForCourse(summary.courseName, 0)
    val dotColor = if (palette.isDark) courseStyle.dotDark else courseStyle.dotLight

    Surface(
        onClick = onClick,
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (summary.isPinned) palette.primaryAccent else palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = summary.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = if (summary.isPinned) palette.primaryAccent else palette.textMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (summary.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (summary.isFavorite) palette.primaryAccent else palette.textMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (summary.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.tags.forEach { tag ->
                        Surface(
                            color = palette.primaryAccent.copy(alpha = 0.12f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primaryAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePinClick: () -> Unit,
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
        isPinned -> BorderStroke(1.5.dp, palette.primaryAccent.copy(alpha = 0.65f))
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onTogglePinClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.notes_pin_folder),
                            tint = if (isPinned) palette.primaryAccent else palette.textMuted.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

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

@Composable
private fun TrashBinDialog(
    trashedNotes: List<NoteSummary>,
    onRestore: (NoteSummary) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = TabletNotesTheme.palette

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
                    text = stringResource(R.string.trash_dialog_title),
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.trash_dialog_retention_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted
                )

                if (trashedNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.trash_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashedNotes, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.courseName} • ${item.pageCount} sayfa",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textMuted
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onRestore(item) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.trash_restore_btn), style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { onDeleteForever(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = stringResource(R.string.trash_delete_forever_btn),
                                            tint = palette.secondaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onEmptyTrash,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.secondaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.trash_empty_all_btn),
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
