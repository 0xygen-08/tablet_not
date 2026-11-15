package com.tabletnotes.app.feature.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.feature.editor.canvas.BitmapTileCache
import com.tabletnotes.app.feature.editor.history.EditorCommand
import com.tabletnotes.app.feature.editor.history.UndoRedoManager
import com.tabletnotes.app.feature.editor.spatial.SpatialGridIndex
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Not Editörünün tüm çizim, kalem çeşitleri (İnce/Kalın Dolma Kalem, Kurşun, Marker, Sulu Boya),
 * Kare Seçim Modu (Taşıma, Kopyalama, Boyutlandırma, Renk Değiştirme, Silme),
 * bağımsız kağıt rengi/deseni, Odak Modu (Focus Mode), Undo/Redo ve Zoom/Pan durumunu yönetir.
 */
class NoteEditorController(
    private val onContentMutated: (() -> Unit)? = null
) {

    val spatialIndex = SpatialGridIndex(cellSize = 128f)
    val tileCache = BitmapTileCache(tileDocSize = 512f, tilePixelSize = 512, maxCachedTiles = 64)
    private val undoRedoManager = UndoRedoManager(maxHistorySize = 250)

    // Belge Kimliği, Ders Klasörü, Başlık ve Bağımsız Kağıt Ayarları
    var noteId by mutableStateOf("note_physics_vectors")
        private set
    var noteTitle by mutableStateOf("Vektörler")
        private set
    var courseName by mutableStateOf("Fizik")
        private set
    var paperColor by mutableStateOf(PaperColor.CREAM)
        private set
    var paperTemplate by mutableStateOf(PaperTemplate.DOTTED)
        private set
    var createdAtMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    // Odak Modu (Focus Mode — Yüzen araç çubuğunu gizler/gösterir)
    var isFocusMode by mutableStateOf(false)

    // Araç, Kalem Çeşidi ve 3 Hızlı Erişim Renk Tuşu (Taslak 3 ile birebir uyumlu)
    var activeTool by mutableStateOf(DrawingToolType.FINE_FOUNTAIN)
    var lastPenVariant by mutableStateOf(DrawingToolType.FINE_FOUNTAIN)
        private set

    val favoriteColors = mutableStateListOf(
        0xFFDC4C46.toInt(), // 1. Hızlı Renk: Kırmızı (Taslak 3)
        0xFF2B63D9.toInt(), // 2. Hızlı Renk: Mavi (Taslak 3)
        0xFF1A1C23.toInt()  // 3. Hızlı Renk: Siyah / Koyu Mürekkep (Taslak 3)
    )
    var activeFavoriteColorSlot by mutableIntStateOf(0)
        private set

    var selectedColorArgb by mutableIntStateOf(0xFFDC4C46.toInt())
    var selectedThickness by mutableFloatStateOf(4.0f)
    var stylusOnlyMode by mutableStateOf(false)

    // Kare Seçim Modu (Selection Mode) Durumu
    var selectedStrokeIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectionBounds by mutableStateOf<BoundingBox?>(null)
        private set
    var liveLassoRect by mutableStateOf<BoundingBox?>(null)
        private set

    // Kamera (Viewport Zoom & Pan)
    var zoom by mutableFloatStateOf(1.0f)
        private set
    var panX by mutableFloatStateOf(48f)
        private set
    var panY by mutableFloatStateOf(20f)
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

    fun selectPenVariant(penType: DrawingToolType) {
        if (penType.isPenVariant) {
            lastPenVariant = penType
            activeTool = penType
            clearSelection()
        }
    }

    fun selectTool(tool: DrawingToolType) {
        if (tool.isPenVariant) {
            lastPenVariant = tool
        }
        if (tool != DrawingToolType.LASSO_SELECT) {
            clearSelection()
        }
        activeTool = tool
    }

    fun selectFavoriteColorSlot(slotIndex: Int) {
        val idx = slotIndex.coerceIn(0, favoriteColors.lastIndex)
        activeFavoriteColorSlot = idx
        val color = favoriteColors[idx]
        selectedColorArgb = color
        if (selectedStrokeIds.isNotEmpty()) {
            recolorSelectedStrokes(color)
        }
    }

    fun updateFavoriteColor(slotIndex: Int, newColorArgb: Int) {
        val idx = slotIndex.coerceIn(0, favoriteColors.lastIndex)
        favoriteColors[idx] = newColorArgb
        activeFavoriteColorSlot = idx
        selectedColorArgb = newColorArgb
        if (selectedStrokeIds.isNotEmpty()) {
            recolorSelectedStrokes(newColorArgb)
        }
    }

    fun loadDocument(document: NoteDocument) {
        noteId = document.id
        noteTitle = document.title
        courseName = document.courseName.ifBlank { "Fizik" }
        paperColor = document.paperColor
        paperTemplate = document.paperTemplate
        createdAtMillis = document.createdAtMillis
        pages = document.pages.ifEmpty { listOf(NotePage(0)) }
        textBlocks = document.textBlocks
        clearSelection()

        spatialIndex.clear()
        spatialIndex.insertAll(document.strokes)
        tileCache.clearAll()
        undoRedoManager.clear()
        resetZoomAndPan()
        syncCounters(notifyMutation = false)
    }

    fun toNoteDocument(nowMillis: Long = System.currentTimeMillis()): NoteDocument {
        return NoteDocument(
            id = noteId,
            title = noteTitle,
            pages = pages,
            strokes = spatialIndex.getAllStrokes().filterNot { it.id.startsWith("bench_") },
            textBlocks = textBlocks,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = nowMillis,
            courseName = courseName,
            paperColor = paperColor,
            paperTemplate = paperTemplate
        )
    }

    fun updateTitleAndCourse(newTitle: String, newCourse: String = courseName) {
        val cleanTitle = newTitle.trim().ifEmpty { noteTitle }
        val cleanCourse = newCourse.trim().ifEmpty { courseName }
        if (cleanTitle != noteTitle || cleanCourse != courseName) {
            noteTitle = cleanTitle
            courseName = cleanCourse
            syncCounters(notifyMutation = true)
        }
    }

    fun updateTitle(newTitle: String) {
        updateTitleAndCourse(newTitle = newTitle, newCourse = courseName)
    }

    fun updatePaperStyle(newColor: PaperColor, newTemplate: PaperTemplate) {
        if (paperColor != newColor || paperTemplate != newTemplate) {
            paperColor = newColor
            paperTemplate = newTemplate
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
        panX = 48f
        panY = 20f
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

    // --- KARE SEÇİM MODU (LASSO / RECTANGLE SELECTION) İŞLEMLERİ ---

    fun updateLiveLassoRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        liveLassoRect = BoundingBox(
            minX = min(x0, x1),
            minY = min(y0, y1),
            maxX = max(x0, x1),
            maxY = max(y0, y1)
        )
        renderRevision++
    }

    fun finishLassoSelection() {
        val rect = liveLassoRect
        liveLassoRect = null
        if (rect == null || (rect.width < 8f && rect.height < 8f)) {
            clearSelection()
            return
        }
        val hits = spatialIndex.queryViewport(rect)
        if (hits.isEmpty()) {
            clearSelection()
        } else {
            selectedStrokeIds = hits.map { it.id }.toSet()
            selectionBounds = BoundingBox.union(hits.map { it.bounds })?.inflated(10f)
            renderRevision++
        }
    }

    fun clearSelection() {
        if (selectedStrokeIds.isNotEmpty() || selectionBounds != null || liveLassoRect != null) {
            selectedStrokeIds = emptySet()
            selectionBounds = null
            liveLassoRect = null
            renderRevision++
        }
    }

    fun getSelectedStrokes(): List<NoteStroke> {
        if (selectedStrokeIds.isEmpty()) return emptyList()
        val all = spatialIndex.getAllStrokes()
        return all.filter { it.id in selectedStrokeIds }
    }

    /**
     * Seçili çizgileri `(dx, dy)` kadar taşır.
     */
    fun translateSelectedStrokes(dx: Float, dy: Float, recordUndo: Boolean = false, originalSnapshot: List<NoteStroke>? = null) {
        val current = getSelectedStrokes()
        if (current.isEmpty()) return
        val updated = current.map { stroke ->
            val movedPts = stroke.points.map { pt ->
                pt.copy(x = pt.x + dx, y = pt.y + dy)
            }
            val pageIdx = NotePage.pageIndexAtY(movedPts.first().y, pages.size)
            stroke.copy(
                pageIndex = pageIdx,
                points = movedPts,
                bounds = BoundingBox.fromPoints(movedPts, padding = stroke.thickness * 1.5f)
            )
        }
        for (old in current) {
            tileCache.invalidateBounds(old.bounds)
            spatialIndex.remove(old.id)
        }
        for (nw in updated) {
            spatialIndex.insert(nw)
            tileCache.invalidateBounds(nw.bounds)
        }
        selectionBounds = BoundingBox.union(updated.map { it.bounds })?.inflated(10f)
        if (recordUndo && originalSnapshot != null) {
            undoRedoManager.record(EditorCommand.ReplaceStrokes(before = originalSnapshot, after = updated))
            syncCounters(notifyMutation = true)
        } else {
            syncCounters(notifyMutation = false)
        }
    }

    /**
     * Seçili çizgileri merkez noktası etrafında `scaleFactor` oranında büyütür veya küçültür.
     */
    fun scaleSelectedStrokes(scaleFactor: Float) {
        val current = getSelectedStrokes()
        val bounds = selectionBounds ?: return
        if (current.isEmpty()) return
        val factor = scaleFactor.coerceIn(0.5f, 2.0f)
        val cx = bounds.centerX
        val cy = bounds.centerY

        val updated = current.map { stroke ->
            val scaledPts = stroke.points.map { pt ->
                pt.copy(
                    x = cx + (pt.x - cx) * factor,
                    y = cy + (pt.y - cy) * factor
                )
            }
            val newThickness = (stroke.thickness * factor).coerceIn(1.2f, 28f)
            stroke.copy(
                thickness = newThickness,
                points = scaledPts,
                bounds = BoundingBox.fromPoints(scaledPts, padding = newThickness * 1.5f)
            )
        }
        replaceStrokesInternal(before = current, after = updated)
    }

    /**
     * Seçili çizgileri kopyalayıp çoğaltır (`+28, +28` ofset ile) ve yeni kopyayı seçili hale getirir.
     */
    fun duplicateSelectedStrokes() {
        val current = getSelectedStrokes()
        if (current.isEmpty()) return
        val clones = current.map { stroke ->
            val shiftedPts = stroke.points.map { pt ->
                pt.copy(x = pt.x + 28f, y = pt.y + 28f)
            }
            stroke.copy(
                id = UUID.randomUUID().toString(),
                points = shiftedPts,
                bounds = BoundingBox.fromPoints(shiftedPts, padding = stroke.thickness * 1.5f)
            )
        }
        spatialIndex.insertAll(clones)
        for (c in clones) {
            tileCache.invalidateBounds(c.bounds)
        }
        undoRedoManager.record(EditorCommand.BatchAddStrokes(clones))
        selectedStrokeIds = clones.map { it.id }.toSet()
        selectionBounds = BoundingBox.union(clones.map { it.bounds })?.inflated(10f)
        syncCounters(notifyMutation = true)
    }

    /**
     * Seçili çizgilerin rengini değiştirir.
     */
    fun recolorSelectedStrokes(newColorArgb: Int) {
        val current = getSelectedStrokes()
        if (current.isEmpty()) return
        val updated = current.map { it.copy(colorArgb = newColorArgb) }
        replaceStrokesInternal(before = current, after = updated)
    }

    /**
     * Seçili çizgileri siler.
     */
    fun deleteSelectedStrokes() {
        val current = getSelectedStrokes()
        if (current.isEmpty()) return
        for (s in current) {
            spatialIndex.remove(s.id)
            tileCache.invalidateBounds(s.bounds)
        }
        undoRedoManager.record(EditorCommand.EraseStrokes(current))
        clearSelection()
        syncCounters(notifyMutation = true)
    }

    private fun replaceStrokesInternal(before: List<NoteStroke>, after: List<NoteStroke>) {
        for (old in before) {
            tileCache.invalidateBounds(old.bounds)
            spatialIndex.remove(old.id)
        }
        for (nw in after) {
            spatialIndex.insert(nw)
            tileCache.invalidateBounds(nw.bounds)
        }
        undoRedoManager.record(EditorCommand.ReplaceStrokes(before = before, after = after))
        selectedStrokeIds = after.map { it.id }.toSet()
        selectionBounds = BoundingBox.union(after.map { it.bounds })?.inflated(10f)
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
        clearSelection()
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
            is EditorCommand.ReplaceStrokes -> {
                for (s in cmd.after) {
                    spatialIndex.remove(s.id)
                    tileCache.invalidateBounds(s.bounds)
                }
                for (s in cmd.before) {
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
        clearSelection()
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
            is EditorCommand.ReplaceStrokes -> {
                for (s in cmd.before) {
                    spatialIndex.remove(s.id)
                    tileCache.invalidateBounds(s.bounds)
                }
                for (s in cmd.after) {
                    spatialIndex.insert(s)
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
        clearSelection()
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
            0xFFDC4C46.toInt(),
            0xFF2B63D9.toInt(),
            0xFF16794B.toInt(),
            0xFF6B38E8.toInt(),
            0xFF944E12.toInt()
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
                        tool = DrawingToolType.FINE_FOUNTAIN,
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
        syncCounters(notifyMutation = false)
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
