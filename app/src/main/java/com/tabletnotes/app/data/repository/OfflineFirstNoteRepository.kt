package com.tabletnotes.app.data.repository

import android.content.Context
import com.tabletnotes.app.core.common.IoDispatcher
import com.tabletnotes.app.core.database.NoteDao
import com.tabletnotes.app.core.database.NoteEntity
import com.tabletnotes.app.core.storage.AtomicNoteFileStore
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import com.tabletnotes.app.feature.format.thumbnail.NoteThumbnailGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class OfflineFirstNoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val fileStore: AtomicNoteFileStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteRepository {

    override fun observeNoteSummaries(): Flow<List<NoteSummary>> {
        return noteDao.observeAllNotes().map { list ->
            list.map { it.toSummary() }
        }
    }

    override suspend fun loadNoteDocument(noteId: String): NoteDocument? =
        withContext(ioDispatcher) {
            runCatching {
                val bytes = fileStore.readNoteBytes(noteId)
                NoteBinaryCodec.decode(bytes)
            }.getOrNull()
        }

    override suspend fun saveNoteDocumentAtomically(document: NoteDocument): NoteSummary =
        withContext(ioDispatcher) {
            // 1. Vektör not verisini özel .tnote binary formatına kodla ve atomik olarak diske yaz
            val binaryBytes = NoteBinaryCodec.encode(document, compress = true)
            val noteFile = fileStore.writeNoteBytesAtomically(document.id, binaryBytes)

            // 2. Liste önizlemesi için küçük PNG thumbnail üret ve atomik olarak diske yaz
            val thumbFile = runCatching {
                val pngBytes = NoteThumbnailGenerator.renderThumbnailPngBytes(document)
                fileStore.writeThumbnailBytesAtomically(document.id, pngBytes)
            }.getOrNull()

            // 3. Room veritabanındaki üst veriyi güncelle
            val entity = NoteEntity(
                id = document.id,
                title = document.title,
                pageCount = document.pages.size.coerceAtLeast(1),
                strokeCount = document.strokes.size,
                filePath = noteFile.absolutePath,
                thumbnailPath = thumbFile?.absolutePath,
                fileSizeBytes = binaryBytes.size.toLong(),
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = document.updatedAtMillis
            )
            noteDao.upsertNote(entity)
            entity.toSummary()
        }

    override suspend fun deleteNote(noteId: String) = withContext(ioDispatcher) {
        noteDao.deleteNoteById(noteId)
        fileStore.deleteNoteFiles(noteId)
    }

    override suspend fun ensureSampleNotesSeeded() = withContext(ioDispatcher) {
        val existing = noteDao.getAllNotesOnce()
        if (existing.isNotEmpty()) return@withContext

        val now = System.currentTimeMillis()
        val physicsDoc = createSamplePhysicsNote(now)
        val calculusDoc = createSampleCalculusNote(now - 3_600_000L)

        saveNoteDocumentAtomically(calculusDoc)
        saveNoteDocumentAtomically(physicsDoc)
    }

    private fun createSamplePhysicsNote(now: Long): NoteDocument {
        val axisPoints = listOf(
            StrokePoint(120f, 340f, 0.55f, timestampMillis = 10L),
            StrokePoint(120f, 120f, 0.65f, timestampMillis = 20L),
            StrokePoint(120f, 340f, 0.65f, timestampMillis = 30L),
            StrokePoint(420f, 340f, 0.60f, timestampMillis = 40L)
        )
        val vectorRPoints = (0..24).map { i ->
            val t = i / 24f
            StrokePoint(
                x = 120f + t * 240f,
                y = 340f - t * 170f,
                pressure = 0.45f + 0.45f * sin(t * 3.1415f),
                tiltRadians = 0.2f,
                timestampMillis = i * 8L
            )
        }
        val wavePoints = (0..40).map { i ->
            val t = i / 40f
            StrokePoint(
                x = 480f + t * 320f,
                y = 230f + sin(t * 6.283f) * 55f * cos(t * 1.5f),
                pressure = 0.4f + 0.4f * sin(t * 3.14f),
                timestampMillis = i * 8L
            )
        }

        return NoteDocument(
            id = "note_physics_vectors",
            title = "Fizik — Vektörler ve Kuvvet",
            pages = listOf(NotePage(0), NotePage(1)),
            strokes = listOf(
                NoteStroke("s_axis", 0, DrawingToolType.PEN, 0xFF8C93A4.toInt(), 3.2f, axisPoints),
                NoteStroke("s_vec", 0, DrawingToolType.PEN, 0xFFEF5350.toInt(), 5.0f, vectorRPoints),
                NoteStroke("s_wave", 0, DrawingToolType.PEN, 0xFF60A5FA.toInt(), 4.2f, wavePoints)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_vec",
                    pageIndex = 0,
                    x = 140f,
                    y = 70f,
                    text = "R = A + B  (Bileşke Vektör)",
                    colorArgb = 0xFFEF5350.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 7_200_000L,
            updatedAtMillis = now
        )
    }

    private fun createSampleCalculusNote(now: Long): NoteDocument {
        val parabolaPoints = (0..36).map { i ->
            val t = (i - 18) / 18f
            StrokePoint(
                x = 280f + t * 180f,
                y = 320f - (t * t) * 180f,
                pressure = 0.5f + 0.3f * (1f - kotlin.math.abs(t)),
                timestampMillis = i * 10L
            )
        }
        val tangentPoints = listOf(
            StrokePoint(220f, 360f, 0.6f, timestampMillis = 10L),
            StrokePoint(460f, 160f, 0.7f, timestampMillis = 50L)
        )
        return NoteDocument(
            id = "note_calculus_derivative",
            title = "Matematik — Türev ve İntegral",
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_parabola", 0, DrawingToolType.PEN, 0xFF34D399.toInt(), 4.5f, parabolaPoints),
                NoteStroke("s_tangent", 0, DrawingToolType.HIGHLIGHTER, 0xFFFBBF24.toInt(), 8.0f, tangentPoints)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_calc",
                    pageIndex = 0,
                    x = 140f,
                    y = 80f,
                    text = "f'(x) = lim [f(x+h) - f(x)] / h",
                    colorArgb = 0xFF60A5FA.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 3_600_000L,
            updatedAtMillis = now
        )
    }

    private fun NoteEntity.toSummary() = NoteSummary(
        id = id,
        title = title,
        pageCount = pageCount,
        strokeCount = strokeCount,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        fileSizeBytes = fileSizeBytes,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Module
@InstallIn(SingletonComponent::class)
object StorageAndRepositoryModule {

    @Provides
    @Singleton
    fun provideAtomicNoteFileStore(
        @ApplicationContext context: Context
    ): AtomicNoteFileStore {
        return AtomicNoteFileStore(rootDir = context.filesDir)
    }

    @Provides
    @Singleton
    fun provideNoteRepository(
        impl: OfflineFirstNoteRepository
    ): NoteRepository = impl
}

