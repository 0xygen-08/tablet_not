package com.tabletnotes.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAtMillis DESC")
    fun observeTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesOnce(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isTrashed = 1")
    suspend fun getTrashedNotesOnce(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Upsert
    suspend fun upsertNote(entity: NoteEntity)

    @Query("UPDATE notes SET isTrashed = 1, trashedAtMillis = :trashedAtMillis, isPinned = 0 WHERE id = :noteId")
    suspend fun moveToTrash(noteId: String, trashedAtMillis: Long)

    @Query("UPDATE notes SET isTrashed = 0, trashedAtMillis = NULL WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: String)

    @Query("UPDATE notes SET isFavorite = :isFavorite, isPinned = :isPinned, tagsCsv = :tagsCsv WHERE id = :noteId")
    suspend fun updateFlagsAndTags(noteId: String, isFavorite: Boolean, isPinned: Boolean, tagsCsv: String)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)
}
