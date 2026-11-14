package com.tabletnotes.app.feature.editor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletnotes.app.core.storage.LowStorageException
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.format.json.NoteJsonCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AutosaveStatus {
    data object Idle : AutosaveStatus
    data object Saving : AutosaveStatus
    data class Saved(val sizeBytes: Long) : AutosaveStatus
    data object LowStorageError : AutosaveStatus
}

@HiltViewModel
class NotesWorkspaceViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    val noteSummaries: StateFlow<List<NoteSummary>> = noteRepository
        .observeNoteSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen: StateFlow<Boolean> = _isEditorOpen.asStateFlow()

    private val _autosaveStatus = MutableStateFlow<AutosaveStatus>(AutosaveStatus.Idle)
    val autosaveStatus: StateFlow<AutosaveStatus> = _autosaveStatus.asStateFlow()

    val editorController = NoteEditorController(
        onContentMutated = { scheduleDebouncedAutosave() }
    )

    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            noteRepository.ensureSampleNotesSeeded()
        }
    }

    fun openExistingNote(noteId: String) {
        viewModelScope.launch {
            val loaded = noteRepository.loadNoteDocument(noteId)
            if (loaded != null) {
                editorController.loadDocument(loaded)
                val summary = noteSummaries.value.firstOrNull { it.id == noteId }
                _autosaveStatus.value = AutosaveStatus.Saved(summary?.fileSizeBytes ?: 0L)
                _isEditorOpen.value = true
            }
        }
    }

    fun createAndOpenNewNote(defaultTitlePrefix: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextNumber = noteSummaries.value.size + 1
            val newDoc = NoteDocument(
                id = "note_${UUID.randomUUID().toString().take(8)}",
                title = String.format(defaultTitlePrefix, nextNumber),
                pages = listOf(NotePage(0), NotePage(1)),
                strokes = emptyList(),
                textBlocks = emptyList(),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            editorController.loadDocument(newDoc)
            _isEditorOpen.value = true
            performImmediateAutosave()
        }
    }

    fun closeEditorAndSaveImmediately() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            performImmediateAutosave()
            _isEditorOpen.value = false
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
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
            delay(650L)
            performImmediateAutosave()
        }
    }

    private suspend fun performImmediateAutosave() {
        try {
            _autosaveStatus.value = AutosaveStatus.Saving
            val snapshot = editorController.toNoteDocument()
            val savedSummary = noteRepository.saveNoteDocumentAtomically(snapshot)
            _autosaveStatus.value = AutosaveStatus.Saved(savedSummary.fileSizeBytes)
        } catch (_: LowStorageException) {
            _autosaveStatus.value = AutosaveStatus.LowStorageError
        } catch (_: Exception) {
            _autosaveStatus.value = AutosaveStatus.Idle
        }
    }
}

