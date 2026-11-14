package com.tabletnotes.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TabletNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
