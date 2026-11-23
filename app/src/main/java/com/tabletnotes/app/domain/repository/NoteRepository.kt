package com.tabletnotes.app.domain.repository

import com.tabletnotes.app.domain.model.NoteDocument
import kotlinx.coroutines.flow.Flow

/**
 * Not listesi, favori/etiket filtresi ve çöp kutusu ekranında gösterilen özet üst veri modeli.
 */
data class NoteSummary(
    val id: String,
    val title: String,
    val pageCount: Int,
    val strokeCount: Int,
    val filePath: String,
    val thumbnailPath: String?,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val courseName: String = "Fizik",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val isTrashed: Boolean = false,
    val trashedAtMillis: Long? = null
)

/**
 * Yerel not saklama, okuma, çöp kutusu ve otomatik kaydetme arayüzü.
 */
interface NoteRepository {
    fun observeNoteSummaries(): Flow<List<NoteSummary>>
    fun observeTrashedNoteSummaries(): Flow<List<NoteSummary>>
    suspend fun loadNoteDocument(noteId: String): NoteDocument?
    suspend fun saveNoteDocumentAtomically(document: NoteDocument): NoteSummary
    suspend fun updateNoteFlagsAndTags(
        noteId: String,
        isFavorite: Boolean,
        isPinned: Boolean,
        tags: List<String>
    )
    suspend fun moveNoteToTrash(noteId: String)
    suspend fun restoreNoteFromTrash(noteId: String)
    suspend fun permanentlyDeleteNote(noteId: String)
    suspend fun emptyTrash()
    suspend fun purgeExpiredTrashedNotes(retentionDays: Int = 30)
    suspend fun deleteNote(noteId: String)
    suspend fun ensureSampleNotesSeeded()
}
