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
import com.tabletnotes.app.domain.model.EraserMode
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.PaperColor
import com.tabletnotes.app.domain.model.PaperTemplate
import com.tabletnotes.app.domain.model.PendingRecognizedShape
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.feature.editor.canvas.BitmapTileCache
import com.tabletnotes.app.feature.editor.history.EditorCommand
import com.tabletnotes.app.feature.editor.history.UndoRedoManager
import com.tabletnotes.app.feature.editor.shape.ShapeRecognizer
import com.tabletnotes.app.feature.editor.spatial.SpatialGridIndex
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Not Editörünün tüm çizim, kalem çeşitleri (İnce/Kalın Dolma Kalem, Kurşun, Marker, Sulu Boya),
 * Lazer İşaretçi, Şekil Algılama & Düzeltme, Çizgi & Alan Silgisi, Kare Seçim Modu,
 * bağımsız kağıt rengi/deseni, Odak Modu, Undo/Redo ve Zoom/Pan durumunu yönetir.
 */
class NoteEditorController(
    private val onContentMutated: (() -> Unit)? = null
) {

    companion object {
        const val MIN_ZOOM = 0.75f
        const val MAX_ZOOM = 2.20f
    }

    val spatialIndex = SpatialGridIndex(cellSize = 128f)
    val tileCache = BitmapTileCache(tileDocSize = 512f, tilePixelSize = 1024, maxCachedTiles = 64)
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

    // Araç, Kalem Çeşidi ve 3 Hızlı Erişim Renk Tuşu
    var activeTool by mutableStateOf(DrawingToolType.FINE_FOUNTAIN)
    var lastPenVariant by mutableStateOf(DrawingToolType.FINE_FOUNTAIN)
        private set

    val favoriteColors = mutableStateListOf(
        0xFFDC4C46.toInt(), // 1. Hızlı Renk: Kırmızı
        0xFF2B63D9.toInt(), // 2. Hızlı Renk: Mavi
        0xFF1A1C23.toInt()  // 3. Hızlı Renk: Siyah / Koyu Mürekkep
    )
    var activeFavoriteColorSlot by mutableIntStateOf(0)
        private set

    var selectedColorArgb by mutableIntStateOf(0xFFDC4C46.toInt())
    var selectedThickness by mutableFloatStateOf(4.0f)

    // Silgi Ayarları (Çizgi Silgisi / Alan Silgisi & Boyut)
    var eraserMode by mutableStateOf(EraserMode.STROKE_ERASER)
    var eraserRadius by mutableFloatStateOf(22f)

    // Lazer İşaretçi (Kalıcı değil, ekranda belirlenen süre sonra kaybolur)
    var laserDurationMillis by mutableLongStateOf(3_000L)
    val laserStrokes = mutableStateListOf<NoteStroke>()
    var lastLaserActivityMillis by mutableLongStateOf(0L)
        private set

    // Şekil Algılama Modu (Boyut/yer ayarlanıp dışarı tıklanınca onaylanır)
    var pendingShape by mutableStateOf<PendingRecognizedShape?>(null)
        private set

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

    private var lastViewWidth: Int = 1600
    private var lastViewHeight: Int = 1000

    // Yeni sayfa eklemek için aşağı kaydırma ilerlemesi
    var pullAddPageProgress by mutableFloatStateOf(0f)
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
            confirmPendingShape()
            clearLaserStrokes()
            lastPenVariant = penType
            activeTool = penType
            clearSelection()
        }
    }

    fun selectTool(tool: DrawingToolType) {
        if (pendingShape != null && tool != DrawingToolType.SHAPE_TOOL) {
            confirmPendingShape()
        }
        if (tool != DrawingToolType.LASER_POINTER) {
            clearLaserStrokes()
        }
        if (tool.isPenVariant) {
            lastPenVariant = tool
        }
        if (tool != DrawingToolType.LASSO_SELECT) {
            clearSelection()
        }
        activeTool = tool
        renderRevision++
    }

    fun setFavoriteColors(colors: List<Int>) {
        if (colors.size >= 3) {
            for (i in 0..2) {
                favoriteColors[i] = colors[i]
            }
            selectedColorArgb = favoriteColors[activeFavoriteColorSlot.coerceIn(0, 2)]
        }
    }

    fun selectFavoriteColorSlot(slotIndex: Int) {
        val idx = slotIndex.coerceIn(0, favoriteColors.lastIndex)
        activeFavoriteColorSlot = idx
        val color = favoriteColors[idx]
        selectedColorArgb = color
        pendingShape = pendingShape?.copy(colorArgb = color)
        if (selectedStrokeIds.isNotEmpty()) {
            recolorSelectedStrokes(color)
        }
        renderRevision++
    }

    fun updateFavoriteColor(slotIndex: Int, newColorArgb: Int) {
        val idx = slotIndex.coerceIn(0, favoriteColors.lastIndex)
        favoriteColors[idx] = newColorArgb
        activeFavoriteColorSlot = idx
        selectedColorArgb = newColorArgb
        pendingShape = pendingShape?.copy(colorArgb = newColorArgb)
        if (selectedStrokeIds.isNotEmpty()) {
            recolorSelectedStrokes(newColorArgb)
        }
        renderRevision++
    }

    fun loadDocument(document: NoteDocument) {
        confirmPendingShape()
        clearLaserStrokes()
        clearSelection()

        noteId = document.id
        noteTitle = document.title
        courseName = document.courseName.ifBlank { "Fizik" }
        paperColor = document.paperColor
        paperTemplate = document.paperTemplate
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

    fun toNoteDocument(nowMillis: Long = System.currentTimeMillis()): NoteDocument {
        // Bekleyen onaylanmamış bir şekil varsa belge kaydına dahil et
        val committed = spatialIndex.getAllStrokes().filterNot { it.id.startsWith("bench_") }
        val extraShape = pendingShape?.let { shape ->
            NoteStroke(
                id = UUID.randomUUID().toString(),
                pageIndex = shape.pageIndex,
                tool = DrawingToolType.PEN,
                colorArgb = shape.colorArgb,
                thickness = shape.thickness,
                points = shape.points
            )
        }
        return NoteDocument(
            id = noteId,
            title = noteTitle,
            pages = pages,
            strokes = if (extraShape != null) committed + extraShape else committed,
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

    fun updateViewportDimensions(width: Int, height: Int, forceCenter: Boolean = false) {
        if (width <= 0 || height <= 0) return
        lastViewWidth = width
        lastViewHeight = height
        if (forceCenter) {
            val fitZoom = ((width - 72f) / NotePage.DEFAULT_PAGE_WIDTH).coerceIn(MIN_ZOOM, 1.30f)
            val centeredPanX = ((width - NotePage.DEFAULT_PAGE_WIDTH * fitZoom) * 0.5f)
            updateZoomAndPan(newZoom = fitZoom, newPanX = centeredPanX, newPanY = 20f)
        }
    }

    fun updateZoomAndPan(newZoom: Float, newPanX: Float, newPanY: Float) {
        val clampedZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = clampedZoom

        val pageWidthScaled = NotePage.DEFAULT_PAGE_WIDTH * clampedZoom
        val totalHeightScaled = (pages.size * (NotePage.DEFAULT_PAGE_HEIGHT + NotePage.PAGE_GAP) - NotePage.PAGE_GAP) * clampedZoom

        val vw = lastViewWidth.toFloat().coerceAtLeast(600f)
        val vh = lastViewHeight.toFloat().coerceAtLeast(400f)

        // Sayfanın ekrandan tamamen kaybolmasını önleyen dengeli sınırlar
        panX = if (pageWidthScaled <= vw) {
            val margin = 80f
            val minPx = margin - pageWidthScaled + margin
            val maxPx = vw - margin * 2f
            if (minPx < maxPx) {
                newPanX.coerceIn(minPx, maxPx)
            } else {
                (vw - pageWidthScaled) * 0.5f
            }
        } else {
            val minPx = vw - pageWidthScaled - 64f
            val maxPx = 64f
            newPanX.coerceIn(minPx, maxPx)
        }

        val minPy = (vh * 0.45f) - totalHeightScaled
        val maxPy = vh * 0.35f
        panY = newPanY.coerceIn(min(minPy, maxPy), maxPy)
        renderRevision++
    }

    fun resetZoomAndPan() {
        updateViewportDimensions(lastViewWidth, lastViewHeight, forceCenter = true)
    }

    fun updatePullAddPageProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped != pullAddPageProgress) {
            pullAddPageProgress = clamped
        }
    }

    fun addPage(scrollToNewPage: Boolean = false) {
        val nextIndex = pages.size
        pages = pages + NotePage(pageIndex = nextIndex)
        pullAddPageProgress = 0f
        if (scrollToNewPage) {
            val topY = NotePage.pageTopY(nextIndex)
            updateZoomAndPan(zoom, panX, -(topY * zoom) + 24f)
        }
        syncCounters(notifyMutation = true)
    }

    fun commitStroke(stroke: NoteStroke) {
        if (stroke.points.isEmpty()) return
        spatialIndex.insert(stroke)
        tileCache.invalidateBounds(stroke.bounds)
        undoRedoManager.record(EditorCommand.AddStroke(stroke))
        syncCounters(notifyMutation = true)
    }

    // --- LAZER İŞARETÇİ İŞLEMLERİ ---

    fun addLaserStroke(stroke: NoteStroke) {
        if (stroke.points.isEmpty()) return
        laserStrokes.add(stroke)
        lastLaserActivityMillis = System.currentTimeMillis()
        renderRevision++
    }

    fun touchLaserActivity() {
        if (laserStrokes.isNotEmpty()) {
            lastLaserActivityMillis = System.currentTimeMillis()
        }
    }

    fun clearLaserStrokes() {
        if (laserStrokes.isNotEmpty()) {
            laserStrokes.clear()
            renderRevision++
        }
    }

    // --- ŞEKİL ALGILAMA VE DÜZELTME İŞLEMLERİ ---

    fun tryRecognizeAndSetPendingShape(
        rawPoints: List<StrokePoint>,
        pageIndex: Int
    ): PendingRecognizedShape? {
        val recognized = ShapeRecognizer.recognize(rawPoints) ?: return null
        // Sayfa sınırları içinde tut
        val topY = NotePage.pageTopY(pageIndex)
        val bottomY = topY + NotePage.DEFAULT_PAGE_HEIGHT
        val boundedPts = recognized.points.map { pt ->
            pt.copy(
                x = pt.x.coerceIn(6f, NotePage.DEFAULT_PAGE_WIDTH - 6f),
                y = pt.y.coerceIn(topY + 6f, bottomY - 6f)
            )
        }
        val pending = PendingRecognizedShape(
            shapeType = recognized.shapeType,
            labelTr = recognized.labelTr,
            pageIndex = pageIndex,
            colorArgb = selectedColorArgb,
            thickness = selectedThickness,
            points = boundedPts
        )
        pendingShape = pending
        renderRevision++
        return pending
    }

    fun translatePendingShape(dx: Float, dy: Float) {
        val shape = pendingShape ?: return
        val topY = NotePage.pageTopY(shape.pageIndex)
        val bottomY = topY + NotePage.DEFAULT_PAGE_HEIGHT
        val bounds = shape.points.let { pts -> BoundingBox.fromPoints(pts, 0f) }

        val allowedDx = dx.coerceIn(6f - bounds.minX, (NotePage.DEFAULT_PAGE_WIDTH - 6f) - bounds.maxX)
        val allowedDy = dy.coerceIn((topY + 6f) - bounds.minY, (bottomY - 6f) - bounds.maxY)

        val movedPts = shape.points.map { pt ->
            pt.copy(x = pt.x + allowedDx, y = pt.y + allowedDy)
        }
        pendingShape = shape.copy(
            points = movedPts,
            bounds = BoundingBox.fromPoints(movedPts, padding = 10f)
        )
        renderRevision++
    }

    fun scalePendingShape(factorX: Float, factorY: Float = factorX) {
        val shape = pendingShape ?: return
        val bounds = BoundingBox.fromPoints(shape.points, 0f)
        val cx = bounds.centerX
        val cy = bounds.centerY
        val fx = factorX.coerceIn(0.75f, 1.35f)
        val fy = factorY.coerceIn(0.75f, 1.35f)

        val topY = NotePage.pageTopY(shape.pageIndex)
        val bottomY = topY + NotePage.DEFAULT_PAGE_HEIGHT

        val scaledPts = shape.points.map { pt ->
            pt.copy(
                x = (cx + (pt.x - cx) * fx).coerceIn(6f, NotePage.DEFAULT_PAGE_WIDTH - 6f),
                y = (cy + (pt.y - cy) * fy).coerceIn(topY + 6f, bottomY - 6f)
            )
        }
        pendingShape = shape.copy(
            points = scaledPts,
            bounds = BoundingBox.fromPoints(scaledPts, padding = 10f)
        )
        renderRevision++
    }

    fun confirmPendingShape() {
        val shape = pendingShape ?: return
        pendingShape = null
        val committed = NoteStroke(
            id = UUID.randomUUID().toString(),
            pageIndex = shape.pageIndex,
            tool = DrawingToolType.PEN,
            colorArgb = shape.colorArgb,
            thickness = shape.thickness,
            points = shape.points
        )
        commitStroke(committed)
    }

    fun discardPendingShape() {
        if (pendingShape != null) {
            pendingShape = null
            renderRevision++
        }
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

    fun recolorSelectedStrokes(newColorArgb: Int) {
        val current = getSelectedStrokes()
        if (current.isEmpty()) return
        val updated = current.map { it.copy(colorArgb = newColorArgb) }
        replaceStrokesInternal(before = current, after = updated)
    }

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

    // --- ÇİZGİ SİLGİSİ VE ALAN SİLGİSİ İŞLEMLERİ ---

    fun eraseStrokesAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float = eraserRadius
    ): List<NoteStroke> {
        val hitStrokes = spatialIndex.findStrokesHitBySegment(x0, y0, x1, y1, radius)
        if (hitStrokes.isEmpty()) return emptyList()
        for (stroke in hitStrokes) {
            spatialIndex.remove(stroke.id)
            tileCache.invalidateBounds(stroke.bounds)
        }
        syncCounters(notifyMutation = false)
        return hitStrokes
    }

    /**
     * Alan Silgisi (Area Eraser): Sadece silgi dairesinin değdiği nokta segmentlerini temizler,
     * çizginin geri kalan bölümlerini ayrı alt çizgiler olarak korur.
     */
    fun eraseAreaAlongSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float = eraserRadius,
        beforeSnapshotMap: MutableMap<String, NoteStroke>,
        createdStrokeIds: MutableSet<String>
    ): Boolean {
        val hitStrokes = spatialIndex.findStrokesHitBySegment(x0, y0, x1, y1, radius)
        if (hitStrokes.isEmpty()) return false

        var anyChanged = false
        for (stroke in hitStrokes) {
            val threshold = radius + (stroke.thickness * 0.4f)
            val thresholdSq = threshold * threshold
            val survivingSegments = splitStrokeOutsideEraser(stroke.points, x0, y0, x1, y1, thresholdSq)

            // Hiçbir nokta silinmediyse atla
            if (survivingSegments.size == 1 && survivingSegments[0].size == stroke.points.size) {
                continue
            }

            anyChanged = true
            // İlk kez dokunuluyorsa orijinal (yeni üretilmemiş) çizgiyi undo yedeğine kaydet
            if (!createdStrokeIds.contains(stroke.id) && !beforeSnapshotMap.containsKey(stroke.id)) {
                beforeSnapshotMap[stroke.id] = stroke
            } else {
                createdStrokeIds.remove(stroke.id)
            }

            spatialIndex.remove(stroke.id)
            tileCache.invalidateBounds(stroke.bounds)

            for (seg in survivingSegments) {
                if (seg.isEmpty()) continue
                val fragment = stroke.copy(
                    id = UUID.randomUUID().toString(),
                    points = seg,
                    bounds = BoundingBox.fromPoints(seg, padding = stroke.thickness * 1.5f)
                )
                spatialIndex.insert(fragment)
                tileCache.invalidateBounds(fragment.bounds)
                createdStrokeIds.add(fragment.id)
            }
        }

        if (anyChanged) {
            syncCounters(notifyMutation = false)
        }
        return anyChanged
    }

    private fun splitStrokeOutsideEraser(
        pts: List<StrokePoint>,
        ex0: Float,
        ey0: Float,
        ex1: Float,
        ey1: Float,
        thresholdSq: Float
    ): List<List<StrokePoint>> {
        if (pts.isEmpty()) return emptyList()
        if (pts.size == 1) {
            val dSq = SpatialGridIndex.pointToSegmentDistanceSq(pts[0].x, pts[0].y, ex0, ey0, ex1, ey1)
            return if (dSq <= thresholdSq) emptyList() else listOf(pts)
        }

        val segments = ArrayList<MutableList<StrokePoint>>()
        var current = ArrayList<StrokePoint>()

        // Uzun segmentlerin ortasını kaçırmamak için ara örnekleme noktalarıyla kontrol et
        for (i in pts.indices) {
            val pt = pts[i]
            val insideEraser = SpatialGridIndex.pointToSegmentDistanceSq(pt.x, pt.y, ex0, ey0, ex1, ey1) <= thresholdSq
            val crossesNext = if (i < pts.lastIndex) {
                val next = pts[i + 1]
                SpatialGridIndex.segmentToSegmentDistanceSq(pt.x, pt.y, next.x, next.y, ex0, ey0, ex1, ey1) <= thresholdSq
            } else {
                false
            }

            if (insideEraser) {
                if (current.isNotEmpty()) {
                    segments.add(current)
                    current = ArrayList()
                }
            } else {
                current.add(pt)
                if (crossesNext && i < pts.lastIndex) {
                    val next = pts[i + 1]
                    val nextDistSq = SpatialGridIndex.pointToSegmentDistanceSq(next.x, next.y, ex0, ey0, ex1, ey1)
                    if (nextDistSq > thresholdSq) {
                        // İki nokta arasındaki doğru parçasını tam ortadan kes
                        if (current.isNotEmpty()) {
                            segments.add(current)
                            current = ArrayList()
                        }
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            segments.add(current)
        }
        return segments
    }

    fun recordEraseBatch(erasedStrokes: List<NoteStroke>) {
        if (erasedStrokes.isEmpty()) return
        undoRedoManager.record(EditorCommand.EraseStrokes(erasedStrokes))
        syncCounters(notifyMutation = true)
    }

    fun recordAreaEraseBatch(beforeStrokes: List<NoteStroke>, createdFragmentIds: Set<String>) {
        if (beforeStrokes.isEmpty() && createdFragmentIds.isEmpty()) return
        val afterStrokes = spatialIndex.getAllStrokes().filter { it.id in createdFragmentIds }
        undoRedoManager.record(EditorCommand.ReplaceStrokes(before = beforeStrokes, after = afterStrokes))
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
        val topY = NotePage.pageTopY(pageIdx)
        val bottomY = topY + NotePage.DEFAULT_PAGE_HEIGHT
        val updated = TextBlock(
            id = previous?.id ?: UUID.randomUUID().toString(),
            pageIndex = pageIdx,
            x = previous?.x ?: docX.coerceIn(24f, NotePage.DEFAULT_PAGE_WIDTH - 240f),
            y = previous?.y ?: docY.coerceIn(topY + 24f, bottomY - 90f),
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
        discardPendingShape()
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
        discardPendingShape()
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
