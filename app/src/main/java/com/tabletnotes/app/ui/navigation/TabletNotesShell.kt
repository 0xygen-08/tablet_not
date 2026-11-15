package com.tabletnotes.app.ui.navigation

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.chat.ui.ChatScreen
import com.tabletnotes.app.feature.chat.ui.ChatViewModel
import com.tabletnotes.app.feature.editor.ui.NoteEditorScreen
import com.tabletnotes.app.feature.editor.ui.NotesWorkspaceViewModel
import com.tabletnotes.app.feature.editor.ui.formatFileSize
import com.tabletnotes.app.feature.nearby.ui.NearbyShareScreen
import com.tabletnotes.app.feature.nearby.ui.NearbyShareViewModel
import java.io.File

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
    var selectedTab by rememberSaveable { mutableStateOf(TopTabDestination.NOTES) }
    var showNearbyModal by rememberSaveable { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showShareFromEditorDialog by rememberSaveable { mutableStateOf(false) }
    var showNotesSearchBar by rememberSaveable { mutableStateOf(false) }
    var notesSearchQuery by rememberSaveable { mutableStateOf("") }

    val isEditorOpen by workspaceViewModel.isEditorOpen.collectAsState()
    val noteSummaries by workspaceViewModel.noteSummaries.collectAsState()
    val customCourses by workspaceViewModel.customCourses.collectAsState()
    val selectedCourse by workspaceViewModel.selectedCourseFilter.collectAsState()
    val autosaveStatus by workspaceViewModel.autosaveStatus.collectAsState()
    val currentUser by chatViewModel.currentUser.collectAsState()
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
                NoteEditorScreen(
                    controller = workspaceViewModel.editorController,
                    autosaveStatus = autosaveStatus,
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
                            color = TabletNotesTheme.palette.surfaceElevated,
                            border = BorderStroke(1.dp, TabletNotesTheme.palette.borderSubtle),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = TabletNotesTheme.palette.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.nearby_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = TabletNotesTheme.palette.textPrimary
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
                    // ÜST NAVİGASYON ÇUBUĞU (Taslak 1 ve 2: Sol Profil | Orta Notlar-Arkadaşlar-Gruplar Pill | Sağ Arama & ≡ Menü)
                    TabletTopNavigationBar(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        currentTheme = currentTheme,
                        onSelectTheme = onSelectTheme,
                        onCycleTheme = onCycleTheme,
                        onProfileClick = { showProfileDialog = true },
                        onToggleSearch = {
                            showNotesSearchBar = !showNotesSearchBar
                            if (!showNotesSearchBar) notesSearchQuery = ""
                        },
                        onOpenNearbyShare = { showNearbyModal = true },
                        onNewFolderClick = { showNewFolderDialog = true }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            TopTabDestination.NOTES -> NotesAndCoursesWorkspace(
                                notes = noteSummaries,
                                courses = customCourses,
                                selectedCourse = selectedCourse,
                                showSearchBar = showNotesSearchBar,
                                searchQuery = notesSearchQuery,
                                onSearchQueryChange = { notesSearchQuery = it },
                                onSelectCourse = { workspaceViewModel.selectCourseFolder(it) },
                                onOpenNote = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                                onDeleteNote = { noteId -> workspaceViewModel.deleteNote(noteId) },
                                onCreateNewNote = {
                                    workspaceViewModel.createAndOpenNewNote(
                                        defaultTitlePrefix = defaultNoteTitlePrefix,
                                        targetCourse = selectedCourse ?: "Fizik"
                                    )
                                }
                            )

                            TopTabDestination.FRIENDS_CHAT -> ChatScreen(
                                viewModel = chatViewModel,
                                onOpenNoteInEditor = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                                showGroupsOnly = false
                            )

                            TopTabDestination.GROUPS_CHAT -> ChatScreen(
                                viewModel = chatViewModel,
                                onOpenNoteInEditor = { noteId -> workspaceViewModel.openExistingNote(noteId) },
                                showGroupsOnly = true
                            )
                        }
                    }
                }
            }
        }
    }

    // --- DİYALOGLAR ---

    // 1. Profil & E2EE Kimlik Diyaloğu
    if (showProfileDialog) {
        var nameInput by rememberSaveable { mutableStateOf(currentUser.displayName) }
        var emailInput by rememberSaveable { mutableStateOf(currentUser.emailOrPhone) }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(stringResource(R.string.chat_auth_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.chat_auth_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text(stringResource(R.string.chat_auth_email_phone_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "E2EE Anahtar Parmak İzi: ${currentUser.keyFingerprint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabletNotesTheme.palette.textMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.signInOrUpdateProfile(nameInput, emailInput)
                        showProfileDialog = false
                    }
                ) {
                    Text(stringResource(R.string.chat_auth_save_login))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text(stringResource(R.string.editor_text_dialog_cancel))
                }
            }
        )
    }

    // 2. Yeni Ders Klasörü Ekleme Diyaloğu
    if (showNewFolderDialog) {
        var folderName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
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
                    }
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

    // 3. Editör İçinden Doğrudan Not Paylaşma Diyaloğu (Arkadaşa, Gruba veya Yakındaki Tablete)
    if (showShareFromEditorDialog) {
        val activeNoteId = workspaceViewModel.editorController.noteId
        val activeSummary = noteSummaries.firstOrNull { it.id == activeNoteId }
        val palette = TabletNotesTheme.palette

        AlertDialog(
            onDismissRequest = { showShareFromEditorDialog = false },
            title = { Text(stringResource(R.string.action_share_note)) },
            text = {
                Column(
                    modifier = Modifier
                        .widthIn(min = 360.dp)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.padding(12.dp),
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                color = palette.surface,
                                border = BorderStroke(1.dp, palette.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textPrimary
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
    onCycleTheme: () -> Unit,
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
        // SOL: Yuvarlak Profil Butonu (Taslak 1 ve 2 ile Birebir — 38dp)
        Surface(
            onClick = onProfileClick,
            shape = CircleShape,
            color = palette.surfaceElevated,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = stringResource(R.string.chat_auth_button),
                    tint = palette.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // ORTA: Tam Yuvarlak (Pill) Sekme Çubuğu ("Notlar | Arkadaşlar | Gruplar" — Kompakt Tablet Boyutu)
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

        // SAĞ: Arama (Büyüteç) ve Üç Çizgi (≡) Menü Butonu
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
                    // Tema Seçenekleri (Gece, Kağıt, Pastel, Sistem)
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

@Composable
private fun NotesAndCoursesWorkspace(
    notes: List<NoteSummary>,
    courses: List<String>,
    selectedCourse: String?,
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectCourse: (String?) -> Unit,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onCreateNewNote: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Ders klasörlerini ve her dersteki not sayılarını birleştir
    val allCourseNames = remember(courses, notes) {
        val merged = LinkedHashSet<String>()
        merged.addAll(courses)
        notes.forEach { if (it.courseName.isNotBlank()) merged.add(it.courseName) }
        merged.toList()
    }

    val filteredNotes = remember(notes, selectedCourse, searchQuery) {
        notes.filter { note ->
            val matchesCourse = selectedCourse == null || note.courseName.equals(selectedCourse, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                note.title.contains(searchQuery.trim(), ignoreCase = true) ||
                note.courseName.contains(searchQuery.trim(), ignoreCase = true)
            matchesCourse && matchesQuery
        }
    }

    var portraitShowingCourseDetail by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !isLandscape && portraitShowingCourseDetail) {
        portraitShowingCourseDetail = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // İsteğe Bağlı Kompakt Arama Çubuğu
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

            if (isLandscape) {
                // YATAY (LANDSCAPE) TABLET MODU: Sol Ders Klasörleri Grid + Sağ Seçili Dersin Notları
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Sol Panel: 24dp Yuvarlatılmış Ders Klasörü Kartları (Fizik, Kimya, Mat., Tarih, İng.)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 76.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(allCourseNames, key = { _, name -> name }) { index, courseName ->
                            val count = notes.count { it.courseName.equals(courseName, ignoreCase = true) }
                            val isSelected = selectedCourse.equals(courseName, ignoreCase = true)
                            CourseFolderCard(
                                courseName = courseName,
                                noteCount = count,
                                index = index,
                                isSelected = isSelected,
                                onClick = {
                                    onSelectCourse(courseName)
                                }
                            )
                        }
                    }

                    // Sağ Panel: Seçili Ders Klasörünün İçindeki Notlar
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(palette.cardCornerRadius),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        CourseNotesDetailList(
                            courseName = selectedCourse ?: stringResource(R.string.notes_all_courses),
                            notes = filteredNotes,
                            showBackButton = false,
                            onBackToFolders = {},
                            onOpenNote = onOpenNote,
                            onDeleteNote = onDeleteNote
                        )
                    }
                }
            } else {
                // DİKEY (PORTRAIT) TABLET MODU: Üstte 3 Sütunlu Geniş Ders Klasörleri Grid + Altta Seçili Dersin Notları
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(276.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(allCourseNames, key = { _, name -> name }) { index, courseName ->
                            val count = notes.count { it.courseName.equals(courseName, ignoreCase = true) }
                            val isSelected = selectedCourse.equals(courseName, ignoreCase = true)
                            CourseFolderCard(
                                courseName = courseName,
                                noteCount = count,
                                index = index,
                                isSelected = isSelected,
                                onClick = {
                                    onSelectCourse(courseName)
                                }
                            )
                        }
                    }

                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(palette.cardCornerRadius),
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CourseNotesDetailList(
                            courseName = selectedCourse ?: stringResource(R.string.notes_all_courses),
                            notes = filteredNotes,
                            showBackButton = false,
                            onBackToFolders = {},
                            onOpenNote = onOpenNote,
                            onDeleteNote = onDeleteNote
                        )
                    }
                }
            }
        }

        // SAĞ ALT KÖŞE + SQUIRCLE BUTONU (Taslak 1 ile Birebir Uyumlu — 54dp)
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

/**
 * Taslak 1, 2 ve 3'teki 24dp yuvarlatılmış Ders Klasörü Kartı (Fizik, Kimya, Mat., Tarih, İng.).
 * Sol üstte renkli ders noktası, altta ders adı ve not sayısı yer alır.
 */
@Composable
private fun CourseFolderCard(
    courseName: String,
    noteCount: Int,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val courseStyle = CourseFolderPalette.styleForCourse(courseName, index)

    val cardBg = when {
        palette.usePastelFolderCards -> courseStyle.pastelSurfaceLight
        palette.isDark -> palette.surfaceElevated
        else -> palette.surfaceElevated
    }
    val dotColor = if (palette.isDark) courseStyle.dotDark else courseStyle.dotLight
    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, palette.primaryAccent)
        palette.usePastelFolderCards -> null
        else -> BorderStroke(1.dp, palette.borderSubtle)
    }

    val displayCount = remember(courseName, noteCount) {
        // Kullanıcının gerçek not sayısını göster
        noteCount
    }

    Surface(
        onClick = onClick,
        color = cardBg,
        shape = RoundedCornerShape(palette.cardCornerRadius), // 24dp
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Sol Üst Renkli Ders Noktası
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // Alt: Ders Adı ve "X not" Bilgisi
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.notes_folder_count_format, displayCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CourseNotesDetailList(
    courseName: String,
    notes: List<NoteSummary>,
    showBackButton: Boolean,
    onBackToFolders: () -> Unit,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    val palette = TabletNotesTheme.palette
    val courseStyle = CourseFolderPalette.styleForCourse(courseName)
    val dotColor = if (palette.isDark) courseStyle.dotDark else courseStyle.dotLight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showBackButton) {
                Surface(
                    onClick = onBackToFolders,
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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Text(
                text = courseName,
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.notes_folder_count_format, notes.size),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted
            )
        }

        HorizontalDivider(color = palette.borderSubtle)

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.notes_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteListItemRow(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onDelete = { onDeleteNote(note.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteListItemRow(
    note: NoteSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val thumbBitmap = remember(note.thumbnailPath, note.updatedAtMillis) {
        note.thumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    Surface(
        onClick = onOpen,
        color = palette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = Color(0xFFFAF7EE),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.size(width = 58.dp, height = 74.dp)
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap.asImageBitmap(),
                        contentDescription = note.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${note.courseName} › ${note.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${note.pageCount} sayfa • ${note.courseName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.notes_delete_note),
                    tint = palette.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
