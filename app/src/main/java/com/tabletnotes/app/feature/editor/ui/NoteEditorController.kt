package com.tabletnotes.app.feature.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.feature.editor.canvas.BitmapTileCache
import com.tabletnotes.app.feature.editor.history.EditorCommand
import com.tabletnotes.app.feature.editor.history.UndoRedoManager
import com.tabletnotes.app.feature.editor.spatial.SpatialGridIndex
import java.util.UUID
import kotlin.math.max
import kotlin.math.sin

/**
 * Not Editörünün tüm çizim, uzamsal indeks, karo önbelleği (Tile Cache),
 * geri/ileri alma (Undo/Redo), yakınlaştırma/kaydırma (Zoom/Pan) ve belge durumunu yönetir.
 */
class NoteEditorController(
    private val onContentMutated: (() -> Unit)? = null
) {

    val spatialIndex = SpatialGridIndex(cellSize = 128f)
    val tileCache = BitmapTileCache(tileDocSize = 512f, tilePixelSize = 512, maxCachedTiles = 64)
    private val undoRedoManager = UndoRedoManager(maxHistorySize = 250)

    // Belge Kimliği ve Başlığı
    var noteId by mutableStateOf("note_default")
        private set
    var noteTitle by mutableStateOf("Fizik — Vektörler ve Kuvvet")
        private set
    var createdAtMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    // Araç ve Fırça Seçimleri
    var activeTool by mutableStateOf(DrawingToolType.PEN)
    var selectedColorArgb by mutableIntStateOf(0xFFEF5350.toInt()) // Stitch Coral Red
    var selectedThickness by mutableFloatStateOf(4.5f)
    var stylusOnlyMode by mutableStateOf(false)

    // Kamera (Viewport Zoom & Pan)
    var zoom by mutableFloatStateOf(1.0f)
        private set
    var panX by mutableFloatStateOf(60f)
        private set
    var panY by mutableFloatStateOf(24f)
        private set

    // Çoklu Sayfa ve Metin Kutuları
    var pages by mutableStateOf(
        listOf(
            NotePage(pageIndex = 0),
            NotePage(pageIndex = 1)
        )
    )
        private set

    var textBlocks by mutableStateOf<List<TextBlock>>(emptyList())
        private set

    // Performans ve Durum Sayaçları
    var strokeCount by mutableIntStateOf(0)
        private set
    var cachedTileCount by mutableIntStateOf(0)
        private set
    var currentFps by mutableIntStateOf(60)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var renderRevision by mutableIntStateOf(0)
        private set

    /**
     * Diskten okunan bir [NoteDocument] nesnesini editöre yükler.
     */
    fun loadDocument(document: NoteDocument) {
        noteId = document.id
        noteTitle = document.title
        createdAtMillis = document.createdAtMillis
        pages = document.pages.ifEmpty { listOf(NotePage(0)) }
        textBlocks = document.textBlocks

        spatialIndex.clear()
        spatialIndex.insertAll(document.strokes)
        tileCache.clearAll()
        undoRedoManager.clear()
        resetZoomAndPan()
        syncCounters(notifyMutation = false)
    }

    /**
     * Editördeki anlık çizgileri, sayfaları ve metin kutularını [NoteDocument] olarak dışa aktarır.
     */
    fun toNoteDocument(nowMillis: Long = System.currentTimeMillis()): NoteDocument {
        return NoteDocument(
            id = noteId,
            title = noteTitle,
            pages = pages,
            strokes = spatialIndex.getAllStrokes(),
            textBlocks = textBlocks,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = nowMillis
        )
    }

    fun updateTitle(newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty() && clean != noteTitle) {
            noteTitle = clean
            syncCounters(notifyMutation = true)
        }
    }

    fun updateFps(fps: Int, tiles: Int) {
        currentFps = fps.coerceIn(1, 240)
        cachedTileCount = tiles
    }

    fun updateZoomAndPan(newZoom: Float, newPanX: Float, newPanY: Float) {
        zoom = newZoom.coerceIn(0.45f, 3.5f)
        val totalHeight = pages.size * (NotePage.DEFAULT_PAGE_HEIGHT + NotePage.PAGE_GAP)
        panX = newPanX.coerceIn(-NotePage.DEFAULT_PAGE_WIDTH * zoom * 0.8f, 900f)
        panY = newPanY.coerceIn(-totalHeight * zoom * 0.9f, 300f)
        renderRevision++
    }

    fun resetZoomAndPan() {
        zoom = 1.0f
        panX = 60f
        panY = 24f
        renderRevision++
    }

    fun addPage() {
        val nextIndex = pages.size
        pages = pages + NotePage(pageIndex = nextIndex)
        syncCounters(notifyMutation = true)
    }

    fun commitStroke(stroke: NoteStroke) {
        if (stroke.points.isEmpty()) return
        spatialIndex.insert(stroke)
        tileCache.invalidateBounds(stroke.bounds)
        undoRedoManager.record(EditorCommand.AddStroke(stroke))
        syncCounters(notifyMutation = true)
    }

    fun eraseStrokesAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        eraserRadius: Float = 16f
    ): List<NoteStroke> {
        val hitStrokes = spatialIndex.findStrokesHitBySegment(x0, y0, x1, y1, eraserRadius)
        if (hitStrokes.isEmpty()) return emptyList()
        for (stroke in hitStrokes) {
            spatialIndex.remove(stroke.id)
            tileCache.invalidateBounds(stroke.bounds)
        }
        syncCounters(notifyMutation = false)
        return hitStrokes
    }

    fun recordEraseBatch(erasedStrokes: List<NoteStroke>) {
        if (erasedStrokes.isEmpty()) return
        undoRedoManager.record(EditorCommand.EraseStrokes(erasedStrokes))
        syncCounters(notifyMutation = true)
    }

    fun upsertTextBlock(
        existingId: String?,
        docX: Float,
        docY: Float,
        text: String,
        colorArgb: Int
    ) {
        val trimmed = text.trim()
        val previous = existingId?.let { id -> textBlocks.firstOrNull { it.id == id } }
        if (trimmed.isEmpty()) {
            if (previous != null) {
                deleteTextBlock(previous.id)
            }
            return
        }
        val pageIdx = NotePage.pageIndexAtY(docY, pages.size)
        val updated = TextBlock(
            id = previous?.id ?: UUID.randomUUID().toString(),
            pageIndex = pageIdx,
            x = previous?.x ?: docX.coerceIn(24f, NotePage.DEFAULT_PAGE_WIDTH - 220f),
            y = previous?.y ?: docY.coerceAtLeast(24f),
            text = trimmed,
            colorArgb = colorArgb
        )
        textBlocks = if (previous != null) {
            textBlocks.map { if (it.id == updated.id) updated else it }
        } else {
            textBlocks + updated
        }
        undoRedoManager.record(EditorCommand.UpsertTextBlock(previous = previous, updated = updated))
        syncCounters(notifyMutation = true)
    }

    fun deleteTextBlock(id: String) {
        val previous = textBlocks.firstOrNull { it.id == id } ?: return
        textBlocks = textBlocks.filterNot { it.id == id }
        undoRedoManager.record(EditorCommand.UpsertTextBlock(previous = previous, updated = null))
        syncCounters(notifyMutation = true)
    }

    fun undo() {
        when (val cmd = undoRedoManager.popUndo() ?: return) {
            is EditorCommand.AddStroke -> {
                spatialIndex.remove(cmd.stroke.id)
                tileCache.invalidateBounds(cmd.stroke.bounds)
            }
            is EditorCommand.BatchAddStrokes -> {
                for (s in cmd.strokes) {
                    spatialIndex.remove(s.id)
                }
                tileCache.clearAll()
            }
            is EditorCommand.EraseStrokes -> {
                for (s in cmd.erasedStrokes) {
                    spatialIndex.insert(s)
                    tileCache.invalidateBounds(s.bounds)
                }
            }
            is EditorCommand.UpsertTextBlock -> {
                textBlocks = when {
                    cmd.previous == null && cmd.updated != null ->
                        textBlocks.filterNot { it.id == cmd.updated.id }
                    cmd.previous != null && cmd.updated == null ->
                        textBlocks + cmd.previous
                    cmd.previous != null && cmd.updated != null ->
                        textBlocks.map { if (it.id == cmd.previous.id) cmd.previous else it }
                    else -> textBlocks
                }
            }
        }
        syncCounters(notifyMutation = true)
    }

    fun redo() {
        when (val cmd = undoRedoManager.popRedo() ?: return) {
            is EditorCommand.AddStroke -> {
                spatialIndex.insert(cmd.stroke)
                tileCache.invalidateBounds(cmd.stroke.bounds)
            }
            is EditorCommand.BatchAddStrokes -> {
                spatialIndex.insertAll(cmd.strokes)
                tileCache.clearAll()
            }
            is EditorCommand.EraseStrokes -> {
                for (s in cmd.erasedStrokes) {
                    spatialIndex.remove(s.id)
                    tileCache.invalidateBounds(s.bounds)
                }
            }
            is EditorCommand.UpsertTextBlock -> {
                textBlocks = when {
                    cmd.previous == null && cmd.updated != null ->
                        textBlocks + cmd.updated
                    cmd.previous != null && cmd.updated == null ->
                        textBlocks.filterNot { it.id == cmd.previous.id }
                    cmd.previous != null && cmd.updated != null ->
                        textBlocks.map { if (it.id == cmd.updated.id) cmd.updated else it }
                    else -> textBlocks
                }
            }
        }
        syncCounters(notifyMutation = true)
    }

    fun clearAll() {
        val currentStrokes = spatialIndex.getAllStrokes()
        if (currentStrokes.isNotEmpty()) {
            for (s in currentStrokes) {
                spatialIndex.remove(s.id)
            }
            tileCache.clearAll()
            undoRedoManager.record(EditorCommand.EraseStrokes(currentStrokes))
        }
        syncCounters(notifyMutation = true)
    }

    fun generateTenThousandStrokesBenchmark() {
        while (pages.size < 4) {
            pages = pages + NotePage(pageIndex = pages.size)
        }
        val paletteColors = intArrayOf(
            0xFFEF5350.toInt(),
            0xFF60A5FA.toInt(),
            0xFF34D399.toInt(),
            0xFFFBBF24.toInt(),
            0xFFA78BFA.toInt()
        )
        val generated = ArrayList<NoteStroke>(10_000)
        val cols = 100
        val rows = 100
        val marginX = 36f
        val usableWidth = NotePage.DEFAULT_PAGE_WIDTH - (marginX * 2)
        val totalHeight = pages.size * (NotePage.DEFAULT_PAGE_HEIGHT + NotePage.PAGE_GAP) - 120f

        var index = 0
        for (r in 0 until rows) {
            val baseY = 40f + (r.toFloat() / rows) * totalHeight
            val pageIdx = NotePage.pageIndexAtY(baseY, pages.size)
            for (c in 0 until cols) {
                val baseX = marginX + (c.toFloat() / cols) * usableWidth
                val pts = ArrayList<StrokePoint>(6)
                for (p in 0 until 6) {
                    val t = p / 5f
                    val px = baseX + t * 14f
                    val py = baseY + sin((t * 3.14f) + (c * 0.2f)) * 5f
                    val pressure = 0.35f + 0.5f * sin(t * 3.14f)
                    pts.add(
                        StrokePoint(
                            x = px,
                            y = py,
                            pressure = pressure,
                            tiltRadians = 0.15f,
                            timestampMillis = index.toLong()
                        )
                    )
                }
                val color = paletteColors[index % paletteColors.size]
                generated.add(
                    NoteStroke(
                        id = "bench_${UUID.randomUUID()}",
                        pageIndex = pageIdx,
                        tool = DrawingToolType.PEN,
                        colorArgb = color,
                        thickness = 2.4f,
                        points = pts
                    )
                )
                index++
            }
        }

        spatialIndex.insertAll(generated)
        tileCache.clearAll()
        undoRedoManager.record(EditorCommand.BatchAddStrokes(generated))
        syncCounters(notifyMutation = true)
    }

    private fun syncCounters(notifyMutation: Boolean) {
        strokeCount = spatialIndex.size
        cachedTileCount = tileCache.cachedTileCount
        canUndo = undoRedoManager.canUndo
        canRedo = undoRedoManager.canRedo
        renderRevision++
        if (notifyMutation) {
            onContentMutated?.invoke()
        }
    }
}
