package com.tabletnotes.app.data.repository

import android.content.Context
import com.tabletnotes.app.core.common.IoDispatcher
import com.tabletnotes.app.core.database.NoteDao
import com.tabletnotes.app.core.database.NoteEntity
import com.tabletnotes.app.core.storage.AtomicNoteFileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TagNormalizer
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import com.tabletnotes.app.feature.format.thumbnail.NoteThumbnailGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
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

    override fun observeTrashedNoteSummaries(): Flow<List<NoteSummary>> {
        return noteDao.observeTrashedNotes().map { list ->
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
        withContext(ioDispatcher + NonCancellable) {
            val existingRow = noteDao.getNoteById(document.id)
            val binaryBytes = NoteBinaryCodec.encode(document, compress = true)
            val noteFile = fileStore.writeNoteBytesAtomically(document.id, binaryBytes)

            val initialEntity = NoteEntity(
                id = document.id,
                title = document.title,
                pageCount = document.pages.size.coerceAtLeast(1),
                strokeCount = document.strokes.size,
                filePath = noteFile.absolutePath,
                thumbnailPath = null,
                fileSizeBytes = binaryBytes.size.toLong(),
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = document.updatedAtMillis,
                courseName = document.courseName,
                isFavorite = document.isFavorite,
                isPinned = document.isPinned,
                tagsCsv = TagNormalizer.toCsv(document.tags),
                isTrashed = existingRow?.isTrashed ?: false,
                trashedAtMillis = existingRow?.trashedAtMillis
            )
            noteDao.upsertNote(initialEntity)

            val thumbFile = runCatching {
                val pngBytes = NoteThumbnailGenerator.renderThumbnailPngBytes(document)
                fileStore.writeThumbnailBytesAtomically(document.id, pngBytes)
            }.getOrNull()

            if (thumbFile != null) {
                val updatedEntity = initialEntity.copy(thumbnailPath = thumbFile.absolutePath)
                noteDao.upsertNote(updatedEntity)
                updatedEntity.toSummary()
            } else {
                initialEntity.toSummary()
            }
        }

    override suspend fun updateNoteFlagsAndTags(
        noteId: String,
        isFavorite: Boolean,
        isPinned: Boolean,
        tags: List<String>
    ) = withContext(ioDispatcher + NonCancellable) {
        val cleanTags = tags.map { TagNormalizer.normalize(it) }.filter { it.isNotEmpty() }.distinct()
        noteDao.updateFlagsAndTags(noteId, isFavorite, isPinned, TagNormalizer.toCsv(cleanTags))
        val doc = loadNoteDocument(noteId)
        if (doc != null) {
            val updatedDoc = doc.copy(
                isFavorite = isFavorite,
                isPinned = isPinned,
                tags = cleanTags
            )
            val bytes = NoteBinaryCodec.encode(updatedDoc, compress = true)
            fileStore.writeNoteBytesAtomically(noteId, bytes)
        }
    }

    override suspend fun moveNoteToTrash(noteId: String) = withContext(ioDispatcher + NonCancellable) {
        noteDao.moveToTrash(noteId, System.currentTimeMillis())
    }

    override suspend fun restoreNoteFromTrash(noteId: String) = withContext(ioDispatcher + NonCancellable) {
        noteDao.restoreFromTrash(noteId)
    }

    override suspend fun permanentlyDeleteNote(noteId: String) = withContext(ioDispatcher + NonCancellable) {
        noteDao.deleteNoteById(noteId)
        fileStore.deleteNoteFiles(noteId)
    }

    override suspend fun emptyTrash() = withContext(ioDispatcher + NonCancellable) {
        val trashed = noteDao.getTrashedNotesOnce()
        for (entity in trashed) {
            noteDao.deleteNoteById(entity.id)
            fileStore.deleteNoteFiles(entity.id)
        }
    }

    override suspend fun purgeExpiredTrashedNotes(retentionDays: Int) =
        withContext(ioDispatcher + NonCancellable) {
            val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86_400_000L)
            val trashed = noteDao.getTrashedNotesOnce()
            for (entity in trashed) {
                val trashedAt = entity.trashedAtMillis ?: continue
                if (trashedAt <= cutoff) {
                    noteDao.deleteNoteById(entity.id)
                    fileStore.deleteNoteFiles(entity.id)
                }
            }
        }

    override suspend fun deleteNote(noteId: String) = moveNoteToTrash(noteId)

    override suspend fun ensureSampleNotesSeeded() = withContext(ioDispatcher) {
        val existing = noteDao.getAllNotesOnce()
        if (existing.isNotEmpty()) {
            for (entity in existing) {
                if (entity.strokeCount > 5_000) {
                    val doc = loadNoteDocument(entity.id)
                    if (doc != null) {
                        val cleanedStrokes = doc.strokes.filterNot { it.id.startsWith("bench_") }
                        if (cleanedStrokes.size != doc.strokes.size) {
                            saveNoteDocumentAtomically(
                                doc.copy(
                                    pages = doc.pages.take(2).ifEmpty { listOf(NotePage(0)) },
                                    strokes = cleanedStrokes
                                )
                            )
                        }
                    }
                }
            }
            return@withContext
        }

        val now = System.currentTimeMillis()
        val sampleDocs = listOf(
            createMockupPhysicsVectorsNote(now),
            createPhysicsKinematicsNote(now - 1_800_000L),
            createChemistryNote(now - 3_600_000L),
            createCalculusNote(now - 5_400_000L),
            createHistoryNote(now - 7_200_000L),
            createEnglishNote(now - 9_000_000L)
        )

        for (doc in sampleDocs.reversed()) {
            saveNoteDocumentAtomically(doc)
        }
    }

    /**
     * Taslak 3. ekrandaki "Fizik › Vektörler" çizimini (kırmızı dalga, mavi satırlar,
     * kırmızı dik eksenler ve bileşke vektör okları) birebir oluşturan örnek not.
     */
    private fun createMockupPhysicsVectorsNote(now: Long): NoteDocument {
        val redColor = 0xFFDC4C46.toInt()
        val blueColor = 0xFF2B63D9.toInt()

        // Üst sol kırmızı dalga çizgisi
        val wavePts = (0..32).map { i ->
            val t = i / 32f
            StrokePoint(
                x = 95f + t * 145f,
                y = 95f - sin(t * 9.42f) * 14f,
                pressure = 0.58f,
                timestampMillis = i * 5L
            )
        }
        // Üst sağ kırmızı düz çizgi
        val topRedLine = listOf(
            StrokePoint(280f, 96f, 0.6f, timestampMillis = 10L),
            StrokePoint(435f, 96f, 0.6f, timestampMillis = 20L)
        )
        // İki mavi yatay çizgi
        val blueLine1 = listOf(
            StrokePoint(95f, 155f, 0.6f, timestampMillis = 30L),
            StrokePoint(575f, 155f, 0.6f, timestampMillis = 40L)
        )
        val blueLine2 = listOf(
            StrokePoint(95f, 200f, 0.6f, timestampMillis = 50L),
            StrokePoint(455f, 200f, 0.6f, timestampMillis = 60L)
        )

        // Sol L-eksen (yukarı ok + sağa ok)
        val leftAxisBody = listOf(
            StrokePoint(130f, 300f, 0.65f, timestampMillis = 70L),
            StrokePoint(130f, 560f, 0.65f, timestampMillis = 80L),
            StrokePoint(415f, 560f, 0.65f, timestampMillis = 90L)
        )
        val leftTopArrow = listOf(
            StrokePoint(116f, 326f, 0.6f, timestampMillis = 95L),
            StrokePoint(130f, 298f, 0.65f, timestampMillis = 100L),
            StrokePoint(144f, 326f, 0.6f, timestampMillis = 105L)
        )
        val leftRightArrow = listOf(
            StrokePoint(390f, 546f, 0.6f, timestampMillis = 110L),
            StrokePoint(418f, 560f, 0.65f, timestampMillis = 115L),
            StrokePoint(390f, 574f, 0.6f, timestampMillis = 120L)
        )

        // Sağ 45 derece vektör açısı + yatay bileşen
        val rightAngleBody = listOf(
            StrokePoint(640f, 385f, 0.65f, timestampMillis = 130L),
            StrokePoint(500f, 560f, 0.65f, timestampMillis = 140L),
            StrokePoint(735f, 560f, 0.65f, timestampMillis = 150L)
        )
        val rightDiagArrow = listOf(
            StrokePoint(614f, 394f, 0.6f, timestampMillis = 155L),
            StrokePoint(642f, 383f, 0.65f, timestampMillis = 160L),
            StrokePoint(634f, 412f, 0.6f, timestampMillis = 165L)
        )
        val rightHorizArrow = listOf(
            StrokePoint(712f, 546f, 0.6f, timestampMillis = 170L),
            StrokePoint(738f, 560f, 0.65f, timestampMillis = 175L),
            StrokePoint(712f, 574f, 0.6f, timestampMillis = 180L)
        )

        // Alt artı (+) koordinat ekseni ve mavi vektör izdüşümü
        val bottomVertAxis = listOf(
            StrokePoint(430f, 610f, 0.65f, timestampMillis = 190L),
            StrokePoint(430f, 800f, 0.65f, timestampMillis = 200L)
        )
        val bottomVertArrow = listOf(
            StrokePoint(416f, 634f, 0.6f, timestampMillis = 205L),
            StrokePoint(430f, 608f, 0.65f, timestampMillis = 210L),
            StrokePoint(444f, 634f, 0.6f, timestampMillis = 215L)
        )
        val bottomHorizLeft = listOf(
            StrokePoint(285f, 705f, 0.65f, timestampMillis = 220L),
            StrokePoint(340f, 705f, 0.65f, timestampMillis = 225L)
        )
        val bottomHorizBlue = listOf(
            StrokePoint(340f, 705f, 0.65f, timestampMillis = 230L),
            StrokePoint(410f, 705f, 0.65f, timestampMillis = 235L)
        )
        val bottomHorizRight = listOf(
            StrokePoint(410f, 705f, 0.65f, timestampMillis = 240L),
            StrokePoint(565f, 705f, 0.65f, timestampMillis = 245L)
        )

        return NoteDocument(
            id = "note_physics_vectors",
            title = "Vektörler",
            courseName = "Fizik",
            paperColor = PaperColor.CREAM,
            paperTemplate = PaperTemplate.DOTTED,
            pages = listOf(NotePage(0), NotePage(1)),
            strokes = listOf(
                NoteStroke("s_wave", 0, DrawingToolType.FINE_FOUNTAIN, redColor, 4.0f, wavePts),
                NoteStroke("s_top_red", 0, DrawingToolType.PEN, redColor, 3.8f, topRedLine),
                NoteStroke("s_blue_1", 0, DrawingToolType.PEN, blueColor, 3.8f, blueLine1),
                NoteStroke("s_blue_2", 0, DrawingToolType.PEN, blueColor, 3.8f, blueLine2),
                NoteStroke("s_l_axis", 0, DrawingToolType.PEN, redColor, 4.0f, leftAxisBody),
                NoteStroke("s_l_arr1", 0, DrawingToolType.PEN, redColor, 4.0f, leftTopArrow),
                NoteStroke("s_l_arr2", 0, DrawingToolType.PEN, redColor, 4.0f, leftRightArrow),
                NoteStroke("s_r_axis", 0, DrawingToolType.PEN, redColor, 4.0f, rightAngleBody),
                NoteStroke("s_r_arr1", 0, DrawingToolType.PEN, redColor, 4.0f, rightDiagArrow),
                NoteStroke("s_r_arr2", 0, DrawingToolType.PEN, redColor, 4.0f, rightHorizArrow),
                NoteStroke("s_b_vert", 0, DrawingToolType.PEN, redColor, 4.0f, bottomVertAxis),
                NoteStroke("s_b_arr", 0, DrawingToolType.PEN, redColor, 4.0f, bottomVertArrow),
                NoteStroke("s_b_hl", 0, DrawingToolType.PEN, redColor, 4.0f, bottomHorizLeft),
                NoteStroke("s_b_hb", 0, DrawingToolType.PEN, blueColor, 4.2f, bottomHorizBlue),
                NoteStroke("s_b_hr", 0, DrawingToolType.PEN, redColor, 4.0f, bottomHorizRight)
            ),
            textBlocks = emptyList(),
            createdAtMillis = now - 7_200_000L,
            updatedAtMillis = now
        )
    }

    private fun createPhysicsKinematicsNote(now: Long): NoteDocument {
        val curvePts = (0..30).map { i ->
            val t = i / 30f
            StrokePoint(
                x = 120f + t * 420f,
                y = 360f - (t * t) * 200f,
                pressure = 0.55f,
                timestampMillis = i * 8L
            )
        }
        return NoteDocument(
            id = "note_physics_kinematics",
            title = "Kuvvet ve Hareket",
            courseName = "Fizik",
            paperColor = PaperColor.CREAM,
            paperTemplate = PaperTemplate.GRID,
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_kin_1", 0, DrawingToolType.FINE_FOUNTAIN, 0xFF2B63D9.toInt(), 4.2f, curvePts)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_kin",
                    pageIndex = 0,
                    x = 120f,
                    y = 90f,
                    width = 340f,
                    text = "F_net = m · a  (Newton II. Yasası)",
                    colorArgb = 0xFF1E293B.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 8_000_000L,
            updatedAtMillis = now
        )
    }

    private fun createChemistryNote(now: Long): NoteDocument {
        val hexPts = (0..6).map { i ->
            val angle = (i * 60.0) * (Math.PI / 180.0)
            StrokePoint(
                x = 280f + (cos(angle) * 90.0).toFloat(),
                y = 260f + (sin(angle) * 90.0).toFloat(),
                pressure = 0.6f,
                timestampMillis = i * 15L
            )
        }
        return NoteDocument(
            id = "note_chem_organic",
            title = "Organik Bileşikler ve Bağlar",
            courseName = "Kimya",
            paperColor = PaperColor.WHITE,
            paperTemplate = PaperTemplate.DOTTED,
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_chem_hex", 0, DrawingToolType.MARKER, 0xFF16794B.toInt(), 3.6f, hexPts)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_chem",
                    pageIndex = 0,
                    x = 140f,
                    y = 90f,
                    width = 380f,
                    text = "Aromatik Halkalar — Benzen (C6H6)",
                    colorArgb = 0xFF16794B.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 9_500_000L,
            updatedAtMillis = now
        )
    }

    private fun createCalculusNote(now: Long): NoteDocument {
        val parabolaPoints = (0..36).map { i ->
            val t = (i - 18) / 18f
            StrokePoint(
                x = 280f + t * 180f,
                y = 320f - (t * t) * 180f,
                pressure = 0.5f + 0.3f * (1f - kotlin.math.abs(t)),
                timestampMillis = i * 10L
            )
        }
        return NoteDocument(
            id = "note_calculus_derivative",
            title = "Türev ve İntegral",
            courseName = "Mat.",
            paperColor = PaperColor.CREAM,
            paperTemplate = PaperTemplate.GRID,
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_parabola", 0, DrawingToolType.CALLIGRAPHY, 0xFF6B38E8.toInt(), 4.2f, parabolaPoints)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_calc",
                    pageIndex = 0,
                    x = 140f,
                    y = 80f,
                    width = 380f,
                    text = "f'(x) = lim [f(x+h) - f(x)] / h",
                    colorArgb = 0xFF6B38E8.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 10_800_000L,
            updatedAtMillis = now
        )
    }

    private fun createHistoryNote(now: Long): NoteDocument {
        val timelinePts = listOf(
            StrokePoint(120f, 220f, 0.6f, timestampMillis = 10L),
            StrokePoint(620f, 220f, 0.6f, timestampMillis = 40L)
        )
        return NoteDocument(
            id = "note_history_1",
            title = "Milli Mücadele Kronolojisi",
            courseName = "Tarih",
            paperColor = PaperColor.CREAM,
            paperTemplate = PaperTemplate.LINED,
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_hist_line", 0, DrawingToolType.HIGHLIGHTER, 0xFFFA984C.toInt(), 10f, timelinePts)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_hist",
                    pageIndex = 0,
                    x = 120f,
                    y = 120f,
                    width = 440f,
                    text = "19 Mayıs 1919 — Kongreler ve Cepheler Özeti",
                    colorArgb = 0xFF944E12.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 12_000_000L,
            updatedAtMillis = now
        )
    }

    private fun createEnglishNote(now: Long): NoteDocument {
        val underlinePts = listOf(
            StrokePoint(120f, 180f, 0.6f, timestampMillis = 10L),
            StrokePoint(440f, 180f, 0.6f, timestampMillis = 30L)
        )
        return NoteDocument(
            id = "note_english_1",
            title = "Academic Essay & Vocabulary",
            courseName = "İng.",
            paperColor = PaperColor.WHITE,
            paperTemplate = PaperTemplate.LINED,
            pages = listOf(NotePage(0)),
            strokes = listOf(
                NoteStroke("s_eng_ul", 0, DrawingToolType.FINE_FOUNTAIN, 0xFFA82258.toInt(), 3.5f, underlinePts)
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_eng",
                    pageIndex = 0,
                    x = 120f,
                    y = 110f,
                    width = 440f,
                    text = "Cohesion & Coherence: Furthermore, Nevertheless, Consequently",
                    colorArgb = 0xFFA82258.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = now - 14_000_000L,
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
        updatedAtMillis = updatedAtMillis,
        courseName = courseName,
        isFavorite = isFavorite,
        isPinned = isPinned,
        tags = TagNormalizer.parseCsv(tagsCsv),
        isTrashed = isTrashed,
        trashedAtMillis = trashedAtMillis
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
