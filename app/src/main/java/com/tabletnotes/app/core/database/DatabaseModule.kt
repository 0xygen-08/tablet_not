package com.tabletnotes.app.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTabletNotesDatabase(
        @ApplicationContext context: Context
    ): TabletNotesDatabase {
        return Room.databaseBuilder(
            context,
            TabletNotesDatabase::class.java,
            "tablet_notes.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideNoteDao(database: TabletNotesDatabase): NoteDao = database.noteDao()
}
