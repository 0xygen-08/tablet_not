package com.tabletnotes.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        ChatContactEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class TabletNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun chatDao(): ChatDao
}
