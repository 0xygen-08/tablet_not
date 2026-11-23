package com.tabletnotes.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE notes ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN trashedAtMillis INTEGER DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideTabletNotesDatabase(
        @ApplicationContext context: Context
    ): TabletNotesDatabase {
        return Room.databaseBuilder(
            context,
            TabletNotesDatabase::class.java,
            "tablet_notes.db"
        )
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNoteDao(database: TabletNotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideChatDao(database: TabletNotesDatabase): ChatDao = database.chatDao()
}
