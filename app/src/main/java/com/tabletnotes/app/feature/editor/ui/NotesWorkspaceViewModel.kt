package com.tabletnotes.app.feature.editor.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.core.storage.AtomicNoteFileStore
import com.tabletnotes.app.core.storage.LowStorageException
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.domain.model.StylusButtonAction
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.format.json.NoteJsonCodec
import com.tabletnotes.app.feature.format.pdf.NotePdfPortability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AutosaveStatus {
    data object Idle : AutosaveStatus
    data object Saving : AutosaveStatus
    data class Saved(val sizeBytes: Long) : AutosaveStatus
    data object LowStorageError : AutosaveStatus
}

@HiltViewModel
class NotesWorkspaceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val noteRepository: NoteRepository,
    private val fileStore: AtomicNoteFileStore
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("tablet_notes_state", Context.MODE_PRIVATE)

    val noteSummaries: StateFlow<List<NoteSummary>> = noteRepository
        .observeNoteSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val trashedNotes: StateFlow<List<NoteSummary>> = noteRepository
        .observeTrashedNoteSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _customCourses = MutableStateFlow(loadSavedCourses())
    val customCourses: StateFlow<List<String>> = _customCourses.asStateFlow()

    private val _pinnedCourses = MutableStateFlow(loadPinnedCourses())
    val pinnedCourses: StateFlow<Set<String>> = _pinnedCourses.asStateFlow()

    private val _selectedCourseFilter = MutableStateFlow<String?>(
        prefs.getString(KEY_SELECTED_COURSE, "Fizik")
    )
    val selectedCourseFilter: StateFlow<String?> = _selectedCourseFilter.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen: StateFlow<Boolean> = _isEditorOpen.asStateFlow()

    private val _autosaveStatus = MutableStateFlow<AutosaveStatus>(AutosaveStatus.Idle)
    val autosaveStatus: StateFlow<AutosaveStatus> = _autosaveStatus.asStateFlow()

    private val _workspaceNoticeRes = MutableStateFlow<Int?>(null)
    val workspaceNoticeRes: StateFlow<Int?> = _workspaceNoticeRes.asStateFlow()

    private val _isPortabilityBusy = MutableStateFlow(false)
    val isPortabilityBusy: StateFlow<Boolean> = _isPortabilityBusy.asStateFlow()

    val editorController = NoteEditorController(
        onContentMutated = {
            saveFavoriteColorsToPrefs()
            scheduleDebouncedAutosave()
        }
    )

    private var autosaveJob: Job? = null

    init {
        restoreFavoriteColorsFromPrefs()
        editorController.stylusSideButtonAction = loadStylusButtonAction()
        viewModelScope.launch {
            noteRepository.ensureSampleNotesSeeded()
            noteRepository.purgeExpiredTrashedNotes(retentionDays = 30)

            // Uygulama en son açık notla kapandıysa doğrudan o nottan devam et
            val wasEditorOpen = prefs.getBoolean(KEY_LAST_EDITOR_OPEN, false)
            val lastNoteId = prefs.getString(KEY_LAST_OPEN_NOTE_ID, null)
            if (wasEditorOpen && !lastNoteId.isNullOrBlank()) {
                val loaded = noteRepository.loadNoteDocument(lastNoteId)
                if (loaded != null) {
                    editorController.loadDocument(loaded)
                    _selectedCourseFilter.value = loaded.courseName
                    _autosaveStatus.value = AutosaveStatus.Saved(0L)
                    _isEditorOpen.value = true
                } else {
                    persistLastOpenEditorState(isOpen = false, noteId = null)
                }
            }
        }
    }

    fun dismissWorkspaceNotice() {
        _workspaceNoticeRes.value = null
    }

    fun getSavedThemePreset(): ThemePreset {
        val raw = prefs.getString(KEY_THEME_PRESET, ThemePreset.NIGHT_DARK.name)
        return runCatching { ThemePreset.valueOf(raw ?: "") }.getOrDefault(ThemePreset.NIGHT_DARK)
    }

    fun saveThemePreset(preset: ThemePreset) {
        prefs.edit().putString(KEY_THEME_PRESET, preset.name).apply()
    }

    fun setStylusSideButtonAction(action: StylusButtonAction) {
        editorController.stylusSideButtonAction = action
        prefs.edit().putInt(KEY_STYLUS_BUTTON_ACTION, action.wireId).apply()
    }

    fun selectCourseFolder(courseName: String?) {
        _selectedCourseFilter.value = courseName
        prefs.edit().putString(KEY_SELECTED_COURSE, courseName).apply()
    }

    fun toggleCoursePinned(courseName: String) {
        val current = _pinnedCourses.value.toMutableSet()
        val existingMatch = current.firstOrNull { it.equals(courseName, ignoreCase = true) }
        if (existingMatch != null) {
            current.remove(existingMatch)
        } else {
            current.add(courseName)
        }
        _pinnedCourses.value = current
        prefs.edit().putStringSet(KEY_PINNED_COURSES, current).apply()
    }

    fun addCourseFolder(newCourseName: String) {
        val clean = newCourseName.trim()
        if (clean.isEmpty()) return
        val current = _customCourses.value
        if (current.none { it.equals(clean, ignoreCase = true) }) {
            val updated = current + clean
            _customCourses.value = updated
            saveCoursesToPrefs(updated)
        }
        selectCourseFolder(clean)
    }

    fun deleteCourseFolder(courseName: String) {
        val updated = _customCourses.value.filterNot { it.equals(courseName, ignoreCase = true) }
        _customCourses.value = updated
        saveCoursesToPrefs(updated)

        if (_pinnedCourses.value.any { it.equals(courseName, ignoreCase = true) }) {
            val nextPinned = _pinnedCourses.value.filterNot { it.equals(courseName, ignoreCase = true) }.toSet()
            _pinnedCourses.value = nextPinned
            prefs.edit().putStringSet(KEY_PINNED_COURSES, nextPinned).apply()
        }

        if (_selectedCourseFilter.value.equals(courseName, ignoreCase = true)) {
            selectCourseFolder(updated.firstOrNull())
        }

        // Bu klasörün içindeki tüm alt notları kalıcı silmek yerine Çöp Kutusuna taşı
        viewModelScope.launch(NonCancellable) {
            val toTrash = noteSummaries.value.filter { it.courseName.equals(courseName, ignoreCase = true) }
            for (note in toTrash) {
                if (_isEditorOpen.value && editorController.noteId == note.id) {
                    _isEditorOpen.value = false
                    persistLastOpenEditorState(isOpen = false, noteId = null)
                }
                noteRepository.moveNoteToTrash(note.id)
            }
            if (toTrash.isNotEmpty()) {
                _workspaceNoticeRes.value = R.string.trash_moved_notice
            }
        }
    }

    /**
     * Kullanıcı bir ders klasörüne tıkladığında en son değişiklik yaptığı (veya sabitlenmiş) alt nottan
     * doğrudan başlar; klasör boşsa ilk alt notu oluşturup açar.
     */
    fun openCourseFolder(courseName: String, defaultTitlePrefix: String) {
        selectCourseFolder(courseName)
        val notesInCourse = noteSummaries.value
            .filter { it.courseName.equals(courseName, ignoreCase = true) }
            .sortedWith(compareByDescending<NoteSummary> { it.isPinned }.thenByDescending { it.updatedAtMillis })

        val mostRecent = notesInCourse.firstOrNull()
        if (mostRecent != null) {
            openExistingNote(mostRecent.id)
        } else {
            createAndOpenNewNote(defaultTitlePrefix = defaultTitlePrefix, targetCourse = courseName)
        }
    }

    fun openExistingNote(noteId: String) {
        viewModelScope.launch {
            if (_isEditorOpen.value && editorController.noteId != noteId) {
                performImmediateAutosave()
            }
            val loaded = noteRepository.loadNoteDocument(noteId)
            if (loaded != null) {
                editorController.loadDocument(loaded)
                selectCourseFolder(loaded.courseName)
                val summary = noteSummaries.value.firstOrNull { it.id == noteId }
                _autosaveStatus.value = AutosaveStatus.Saved(summary?.fileSizeBytes ?: 0L)
                _isEditorOpen.value = true
                persistLastOpenEditorState(isOpen = true, noteId = noteId)
            }
        }
    }

    fun createAndOpenNewNote(
        defaultTitlePrefix: String,
        targetCourse: String? = null
    ) {
        viewModelScope.launch {
            if (_isEditorOpen.value) {
                performImmediateAutosave()
            }
            val now = System.currentTimeMillis()
            val course = (targetCourse ?: _selectedCourseFilter.value ?: "Fizik").ifBlank { "Fizik" }
            addCourseFolder(course)
            val nextNumber = noteSummaries.value.count { it.courseName.equals(course, ignoreCase = true) } + 1
            val newDoc = NoteDocument(
                id = "note_${UUID.randomUUID().toString().take(8)}",
                title = String.format(defaultTitlePrefix, nextNumber),
                courseName = course,
                paperColor = PaperColor.CREAM,
                paperTemplate = PaperTemplate.DOTTED,
                pages = listOf(NotePage(0), NotePage(1)),
                strokes = emptyList(),
                textBlocks = emptyList(),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            editorController.loadDocument(newDoc)
            _isEditorOpen.value = true
            persistLastOpenEditorState(isOpen = true, noteId = newDoc.id)
            performImmediateAutosave()
        }
    }

    fun toggleNoteFavorite(summary: NoteSummary) {
        val nextFav = !summary.isFavorite
        if (_isEditorOpen.value && editorController.noteId == summary.id && editorController.isFavorite != nextFav) {
            editorController.toggleFavorite()
        }
        viewModelScope.launch(NonCancellable) {
            noteRepository.updateNoteFlagsAndTags(
                noteId = summary.id,
                isFavorite = nextFav,
                isPinned = summary.isPinned,
                tags = summary.tags
            )
        }
    }

    fun toggleNotePinned(summary: NoteSummary) {
        val nextPin = !summary.isPinned
        if (_isEditorOpen.value && editorController.noteId == summary.id && editorController.isPinned != nextPin) {
            editorController.togglePinned()
        }
        viewModelScope.launch(NonCancellable) {
            noteRepository.updateNoteFlagsAndTags(
                noteId = summary.id,
                isFavorite = summary.isFavorite,
                isPinned = nextPin,
                tags = summary.tags
            )
        }
    }

    fun closeEditorAndSaveImmediately() {
        autosaveJob?.cancel()
        saveFavoriteColorsToPrefs()
        val snapshot = editorController.toNoteDocument()
        _isEditorOpen.value = false
        persistLastOpenEditorState(isOpen = false, noteId = null)
        viewModelScope.launch(NonCancellable) {
            try {
                val savedSummary = noteRepository.saveNoteDocumentAtomically(snapshot)
                _autosaveStatus.value = AutosaveStatus.Saved(savedSummary.fileSizeBytes)
            } catch (_: LowStorageException) {
                _autosaveStatus.value = AutosaveStatus.LowStorageError
            } catch (_: Exception) {
                _autosaveStatus.value = AutosaveStatus.Idle
            }
        }
    }

    /**
     * Uygulama arka plana atıldığında veya kapatılırken son değişikliği kayıpsız diske yazar.
     */
    fun flushPendingSaveImmediate() {
        saveFavoriteColorsToPrefs()
        if (!_isEditorOpen.value) return
        autosaveJob?.cancel()
        persistLastOpenEditorState(isOpen = true, noteId = editorController.noteId)
        val snapshot = editorController.toNoteDocument()
        viewModelScope.launch(NonCancellable) {
            runCatching {
                val summary = noteRepository.saveNoteDocumentAtomically(snapshot)
                _autosaveStatus.value = AutosaveStatus.Saved(summary.fileSizeBytes)
            }
        }
    }

    fun saveNowAndGetSummary(onSaved: (NoteSummary) -> Unit) {
        viewModelScope.launch {
            try {
                val snapshot = editorController.toNoteDocument()
                val summary = noteRepository.saveNoteDocumentAtomically(snapshot)
                _autosaveStatus.value = AutosaveStatus.Saved(summary.fileSizeBytes)
                onSaved(summary)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Notu doğrudan silmek yerine 30 günlük Çöp Kutusuna taşır.
     */
    fun deleteNote(noteId: String) {
        viewModelScope.launch(NonCancellable) {
            noteRepository.moveNoteToTrash(noteId)
            _workspaceNoticeRes.value = R.string.trash_moved_notice
            if (_isEditorOpen.value && editorController.noteId == noteId) {
                val replacement = noteSummaries.value
                    .filterNot { it.id == noteId }
                    .firstOrNull { it.courseName.equals(editorController.courseName, ignoreCase = true) }
                if (replacement != null) {
                    openExistingNote(replacement.id)
                } else {
                    _isEditorOpen.value = false
                    persistLastOpenEditorState(isOpen = false, noteId = null)
                }
            }
        }
    }

    fun restoreNoteFromTrash(summary: NoteSummary) {
        viewModelScope.launch(NonCancellable) {
            noteRepository.restoreNoteFromTrash(summary.id)
            addCourseFolder(summary.courseName)
            _workspaceNoticeRes.value = R.string.trash_restored_notice
        }
    }

    fun permanentlyDeleteNote(noteId: String) {
        viewModelScope.launch(NonCancellable) {
            noteRepository.permanentlyDeleteNote(noteId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(NonCancellable) {
            noteRepository.emptyTrash()
        }
    }

    // --- GALERİ GÖRSELİ, PDF İÇE AKTARMA VE PDF/PNG DIŞA AKTARMA ---

    fun importGalleryImageIntoActiveNote(uri: Uri) {
        val currentNoteId = editorController.noteId
        val targetPage = editorController.getCurrentVisiblePageIndex()
        viewModelScope.launch {
            _isPortabilityBusy.value = true
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    NotePdfPortability.importGalleryImageToAsset(
                        context = appContext,
                        imageUri = uri,
                        noteId = currentNoteId,
                        fileStore = fileStore
                    )
                }.getOrNull()
            }
            _isPortabilityBusy.value = false
            if (imported != null) {
                val (filePath, aspectRatio) = imported
                editorController.addImageBlock(
                    filePath = filePath,
                    aspectRatio = aspectRatio,
                    targetPageIndex = targetPage
                )
                _workspaceNoticeRes.value = R.string.portability_image_added_notice
            } else {
                _workspaceNoticeRes.value = R.string.portability_operation_failed
            }
        }
    }

    fun importPdfAsNewNote(uri: Uri, targetCourse: String? = null) {
        viewModelScope.launch {
            if (_isEditorOpen.value) {
                performImmediateAutosave()
            }
            _isPortabilityBusy.value = true
            val newNoteId = "note_${UUID.randomUUID().toString().take(8)}"
            val course = (targetCourse ?: _selectedCourseFilter.value ?: "Fizik").ifBlank { "Fizik" }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val title = NotePdfPortability.resolveDisplayName(appContext, uri)
                    val importedPages = NotePdfPortability.importPdfToPageAssets(
                        context = appContext,
                        pdfUri = uri,
                        noteId = newNoteId,
                        fileStore = fileStore,
                        startingPageIndex = 0
                    )
                    if (importedPages.isEmpty()) error("Empty PDF")
                    title to importedPages
                }
            }
            _isPortabilityBusy.value = false

            result.onSuccess { (title, importedPages) ->
                val now = System.currentTimeMillis()
                addCourseFolder(course)
                val newDoc = NoteDocument(
                    id = newNoteId,
                    title = title,
                    courseName = course,
                    paperColor = PaperColor.WHITE,
                    paperTemplate = PaperTemplate.BLANK,
                    pages = importedPages,
                    strokes = emptyList(),
                    textBlocks = emptyList(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                editorController.loadDocument(newDoc)
                _isEditorOpen.value = true
                persistLastOpenEditorState(isOpen = true, noteId = newDoc.id)
                performImmediateAutosave()
                _workspaceNoticeRes.value = R.string.portability_pdf_imported_notice
            }.onFailure {
                _workspaceNoticeRes.value = R.string.portability_operation_failed
            }
        }
    }

    fun appendPdfPagesToActiveNote(uri: Uri) {
        val currentNoteId = editorController.noteId
        val startIndex = editorController.pages.size
        viewModelScope.launch {
            _isPortabilityBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val importedPages = NotePdfPortability.importPdfToPageAssets(
                        context = appContext,
                        pdfUri = uri,
                        noteId = currentNoteId,
                        fileStore = fileStore,
                        startingPageIndex = startIndex
                    )
                    if (importedPages.isEmpty()) error("Empty PDF")
                    importedPages
                }
            }
            _isPortabilityBusy.value = false

            result.onSuccess { importedPages ->
                editorController.appendPages(importedPages, scrollToFirstNew = true)
                _workspaceNoticeRes.value = R.string.portability_pdf_imported_notice
            }.onFailure {
                _workspaceNoticeRes.value = R.string.portability_operation_failed
            }
        }
    }

    fun exportActiveNoteToPdf(uri: Uri) {
        val snapshot = editorController.toNoteDocument()
        viewModelScope.launch {
            _isPortabilityBusy.value = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        NotePdfPortability.exportNoteToPdf(snapshot, out)
                    } ?: error("Cannot open output stream")
                }.isSuccess
            }
            _isPortabilityBusy.value = false
            _workspaceNoticeRes.value = if (ok) {
                R.string.portability_export_pdf_success
            } else {
                R.string.portability_operation_failed
            }
        }
    }

    fun exportCurrentPageToPng(uri: Uri) {
        val snapshot = editorController.toNoteDocument()
        val pageIndex = editorController.getCurrentVisiblePageIndex()
        viewModelScope.launch {
            _isPortabilityBusy.value = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        NotePdfPortability.exportPageToPng(snapshot, pageIndex, out)
                    } ?: error("Cannot open output stream")
                }.isSuccess
            }
            _isPortabilityBusy.value = false
            _workspaceNoticeRes.value = if (ok) {
                R.string.portability_export_png_success
            } else {
                R.string.portability_operation_failed
            }
        }
    }

    fun buildJsonDebugSnapshot(): String {
        val currentDoc = editorController.toNoteDocument()
        return NoteJsonCodec.encodeToJsonString(currentDoc, indentSpaces = 2)
    }

    private fun scheduleDebouncedAutosave() {
        autosaveJob?.cancel()
        _autosaveStatus.value = AutosaveStatus.Saving
        autosaveJob = viewModelScope.launch {
            delay(250L)
            performImmediateAutosave()
        }
    }

    private suspend fun performImmediateAutosave() {
        try {
            _autosaveStatus.value = AutosaveStatus.Saving
            val snapshot = editorController.toNoteDocument()
            val savedSummary = withContext(NonCancellable) {
                noteRepository.saveNoteDocumentAtomically(snapshot)
            }
            _autosaveStatus.value = AutosaveStatus.Saved(savedSummary.fileSizeBytes)
        } catch (_: LowStorageException) {
            _autosaveStatus.value = AutosaveStatus.LowStorageError
        } catch (_: Exception) {
            _autosaveStatus.value = AutosaveStatus.Idle
        }
    }

    private fun loadSavedCourses(): List<String> {
        val stored = prefs.getString(KEY_COURSES_ORDERED, null)
        if (stored == null) {
            return listOf("Fizik", "Kimya", "Mat.", "Tarih", "İng.")
        }
        return stored.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun loadPinnedCourses(): Set<String> =
        prefs.getStringSet(KEY_PINNED_COURSES, emptySet())?.toSet() ?: emptySet()

    private fun loadStylusButtonAction(): StylusButtonAction =
        StylusButtonAction.fromWireId(prefs.getInt(KEY_STYLUS_BUTTON_ACTION, StylusButtonAction.ERASER.wireId))

    private fun saveCoursesToPrefs(courses: List<String>) {
        prefs.edit().putString(KEY_COURSES_ORDERED, courses.joinToString("|")).apply()
    }

    private fun persistLastOpenEditorState(isOpen: Boolean, noteId: String?) {
        prefs.edit()
            .putBoolean(KEY_LAST_EDITOR_OPEN, isOpen)
            .putString(KEY_LAST_OPEN_NOTE_ID, noteId)
            .apply()
    }

    private fun saveFavoriteColorsToPrefs() {
        if (editorController.favoriteColors.size >= 3) {
            prefs.edit()
                .putInt(KEY_FAV_COLOR_0, editorController.favoriteColors[0])
                .putInt(KEY_FAV_COLOR_1, editorController.favoriteColors[1])
                .putInt(KEY_FAV_COLOR_2, editorController.favoriteColors[2])
                .apply()
        }
    }

    private fun restoreFavoriteColorsFromPrefs() {
        if (prefs.contains(KEY_FAV_COLOR_0)) {
            editorController.setFavoriteColors(
                listOf(
                    prefs.getInt(KEY_FAV_COLOR_0, 0xFFDC4C46.toInt()),
                    prefs.getInt(KEY_FAV_COLOR_1, 0xFF2B63D9.toInt()),
                    prefs.getInt(KEY_FAV_COLOR_2, 0xFF1A1C23.toInt())
                )
            )
        }
    }

    override fun onCleared() {
        flushPendingSaveImmediate()
        super.onCleared()
    }

    companion object {
        private const val KEY_COURSES_ORDERED = "saved_courses_ordered"
        private const val KEY_PINNED_COURSES = "saved_pinned_courses"
        private const val KEY_SELECTED_COURSE = "saved_selected_course"
        private const val KEY_THEME_PRESET = "saved_theme_preset"
        private const val KEY_STYLUS_BUTTON_ACTION = "stylus_side_button_action"
        private const val KEY_LAST_EDITOR_OPEN = "last_editor_open"
        private const val KEY_LAST_OPEN_NOTE_ID = "last_open_note_id"
        private const val KEY_FAV_COLOR_0 = "fav_color_0"
        private const val KEY_FAV_COLOR_1 = "fav_color_1"
        private const val KEY_FAV_COLOR_2 = "fav_color_2"
    }
}
