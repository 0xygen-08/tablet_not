package com.tabletnotes.app.domain.repository

import com.tabletnotes.app.domain.model.NoteDocument
import kotlinx.coroutines.flow.Flow

/**
 * Not listesi ve ders klasörü ekranında gösterilen özet üst veri modeli.
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
    val courseName: String = "Fizik"
)

/**
 * Yerel not saklama, okuma ve otomatik kaydetme arayüzü.
 */
interface NoteRepository {
    fun observeNoteSummaries(): Flow<List<NoteSummary>>
    suspend fun loadNoteDocument(noteId: String): NoteDocument?
    suspend fun saveNoteDocumentAtomically(document: NoteDocument): NoteSummary
    suspend fun deleteNote(noteId: String)
    suspend fun ensureSampleNotesSeeded()
}
