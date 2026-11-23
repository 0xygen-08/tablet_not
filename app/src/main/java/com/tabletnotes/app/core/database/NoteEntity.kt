package com.tabletnotes.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room yerel veritabanında saklanan Not Üst Verisi (Metadata).
 * Notun vektör çizgi verisi doğrudan veritabanı tablosunda değil, `filePath` konumundaki
 * sıkıştırılmış `.tnote` binary dosyasında saklanır.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
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
    val tagsCsv: String = "",
    val isTrashed: Boolean = false,
    val trashedAtMillis: Long? = null
)
