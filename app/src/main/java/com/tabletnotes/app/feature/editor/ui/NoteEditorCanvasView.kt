package com.tabletnotes.app.feature.editor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.tabletnotes.app.core.designsystem.theme.NoteThemePalette
import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.editor.canvas.StrokeRenderer
import com.tabletnotes.app.feature.editor.ink.InputRouteDecision
import com.tabletnotes.app.feature.editor.ink.LowLatencyInkEngine
import com.tabletnotes.app.feature.editor.ink.PalmRejectionFilter
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Düşük gecikmeli Stylus + Parmak + Çoklu Sayfa Tuval Görünümü (Custom Android View).
 *
 * Neden doğrudan Android `View` + `Canvas`?
 * - `MotionEvent.getHistoricalX/Y` ve `MotionEventPredictor` örneklerine sıfır kopyalama ile erişir.
 * - Aktif çizgiyi ("wet ink") Jetpack Ink (`androidx.ink`) ile çizerken, sabitlenmiş 10.000+ çizgiyi
 *   ("dry ink") `BitmapTileCache` üzerinden sadece görünüm alanına (Viewport) düşen karolarla çizer.
 */
@SuppressLint("ViewConstructor")
class NoteEditorCanvasView(
    context: Context,
    private val controller: NoteEditorController,
    private val onTapForTextBlock: (docX: Float, docY: Float) -> Unit
) : View(context) {

    private val palmFilter = PalmRejectionFilter()
    private val inkEngine = LowLatencyInkEngine(this)

    private var themePalette: NoteThemePalette? = null

    // Aktif çizilmekte olan çizgi ("Wet Ink")
    private val activeRealPoints = ArrayList<StrokePoint>(256)
    private var activePredictedPoints: List<StrokePoint> = emptyList()
    private var activeToolInFlight: DrawingToolType = DrawingToolType.PEN
    private var isStrokeInFlight: Boolean = false

    // Aktif silgi hareketi sırasında silinen çizgiler (tek Undo kaydı için)
    private val erasedInCurrentSwipe = LinkedHashMap<String, NoteStroke>()
    private var lastEraserDocX: Float = Float.NaN
    private var lastEraserDocY: Float = Float.NaN
    private var isEraserActive: Boolean = false

    // Tek/İki parmak kaydırma (Pan) ve yakınlaştırma (Zoom) durumu
    private var isPanning: Boolean = false
    private var lastPanTouchX: Float = 0f
    private var lastPanTouchY: Float = 0f

    // FPS Ölçümü
    private var lastFrameNano: Long = 0L
    private var smoothedFps: Float = 60f

    // Boya (Paint) nesneleri — GC yükü oluşturmamak için önceden ayrılır
    private val pageBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val minorGridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val majorGridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }
    private val pageLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }
    private val textBlockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBlockBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val textBlockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }
    private val eraserCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val pageRect = RectF()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelInFlightDrawingIfAny()
                isPanning = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor.coerceIn(0.85f, 1.18f)
                val oldZoom = controller.zoom
                val newZoom = (oldZoom * scaleFactor).coerceIn(0.45f, 3.5f)
                val focusX = detector.focusX
                val focusY = detector.focusY

                // Odak noktasını sabit tutarak yakınlaştır
                val docFocusX = (focusX - controller.panX) / oldZoom
                val docFocusY = (focusY - controller.panY) / oldZoom
                val newPanX = focusX - docFocusX * newZoom
                val newPanY = focusY - docFocusY * newZoom

                controller.updateZoomAndPan(newZoom, newPanX, newPanY)
                invalidate()
                return true
            }
        }
    )

    init {
        isFocusable = true
        isClickable = true
        // Düşük gecikmeli çizim için donanım hızlandırmalı katman
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun updatePalette(palette: NoteThemePalette) {
        if (themePalette != palette) {
            themePalette = palette
            invalidate()
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        palmFilter.onHoverEvent(event)
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val decision = palmFilter.evaluate(
            event = event,
            stylusOnlyMode = controller.stylusOnlyMode
        )

        if (decision == InputRouteDecision.REJECTED_PALM) {
            cancelInFlightDrawingIfAny()
            return true
        }

        if (decision == InputRouteDecision.PAN_ZOOM) {
            cancelInFlightDrawingIfAny()
            scaleDetector.onTouchEvent(event)
            handlePanTouchEvent(event)
            return true
        }

        // Buraya gelindiyse kalem (veya izin verilen tek parmak/fare) ile çizim/silme/metin ekleme yapılır
        isPanning = false
        val effectiveTool = if (decision == InputRouteDecision.HARDWARE_STYLUS_ERASER) {
            DrawingToolType.ERASER
        } else {
            controller.activeTool
        }

        when (effectiveTool) {
            DrawingToolType.TEXT -> handleTextToolTouch(event)
            DrawingToolType.ERASER -> handleEraserTouch(event)
            DrawingToolType.PEN, DrawingToolType.HIGHLIGHTER ->
                handleInkDrawingTouch(event, effectiveTool)
        }
        return true
    }

    private fun handlePanTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                lastPanTouchX = averageTouchX(event)
                lastPanTouchY = averageTouchY(event)
                isPanning = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && isPanning) {
                    val currX = averageTouchX(event)
                    val currY = averageTouchY(event)
                    val dx = currX - lastPanTouchX
                    val dy = currY - lastPanTouchY
                    lastPanTouchX = currX
                    lastPanTouchY = currY
                    controller.updateZoomAndPan(
                        newZoom = controller.zoom,
                        newPanX = controller.panX + dx,
                        newPanY = controller.panY + dy
                    )
                    invalidate()
                } else {
                    lastPanTouchX = averageTouchX(event)
                    lastPanTouchY = averageTouchY(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
            }
        }
    }

    private fun handleTextToolTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val docX = screenToDocX(event.x)
            val docY = screenToDocY(event.y)
            onTapForTextBlock(docX, docY)
        }
    }

    private fun handleEraserTouch(event: MotionEvent) {
        val eraserRadiusDoc = max(14f, controller.selectedThickness * 2.5f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                erasedInCurrentSwipe.clear()
                isEraserActive = true
                val docX = screenToDocX(event.x)
                val docY = screenToDocY(event.y)
                lastEraserDocX = docX
                lastEraserDocY = docY
                val hits = controller.eraseStrokesAlongSegment(docX, docY, docX, docY, eraserRadiusDoc)
                for (h in hits) erasedInCurrentSwipe[h.id] = h
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isEraserActive) return
                for (h in 0 until event.historySize) {
                    val hx = screenToDocX(event.getHistoricalX(0, h))
                    val hy = screenToDocY(event.getHistoricalY(0, h))
                    val hits = controller.eraseStrokesAlongSegment(
                        lastEraserDocX,
                        lastEraserDocY,
                        hx,
                        hy,
                        eraserRadiusDoc
                    )
                    for (stroke in hits) erasedInCurrentSwipe[stroke.id] = stroke
                    lastEraserDocX = hx
                    lastEraserDocY = hy
                }
                val cx = screenToDocX(event.x)
                val cy = screenToDocY(event.y)
                val hits = controller.eraseStrokesAlongSegment(
                    lastEraserDocX,
                    lastEraserDocY,
                    cx,
                    cy,
                    eraserRadiusDoc
                )
                for (stroke in hits) erasedInCurrentSwipe[stroke.id] = stroke
                lastEraserDocX = cx
                lastEraserDocY = cy
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (isEraserActive) {
                    controller.recordEraseBatch(erasedInCurrentSwipe.values.toList())
                }
                erasedInCurrentSwipe.clear()
                isEraserActive = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                erasedInCurrentSwipe.clear()
                isEraserActive = false
                invalidate()
            }
        }
    }

    private fun handleInkDrawingTouch(event: MotionEvent, tool: DrawingToolType) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeRealPoints.clear()
                activePredictedPoints = emptyList()
                activeToolInFlight = tool
                isStrokeInFlight = true

                inkEngine.startStroke(
                    tool = tool,
                    colorArgb = controller.selectedColorArgb,
                    thickness = controller.selectedThickness,
                    startTimeMillis = event.eventTime
                )

                val (realPts, predPts) = inkEngine.extractSamplesAndPredict(
                    event = event,
                    pointerIndex = 0,
                    screenToDocX = ::screenToDocX,
                    screenToDocY = ::screenToDocY
                )
                activeRealPoints.addAll(realPts)
                activePredictedPoints = predPts
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isStrokeInFlight) return
                val (realPts, predPts) = inkEngine.extractSamplesAndPredict(
                    event = event,
                    pointerIndex = 0,
                    screenToDocX = ::screenToDocX,
                    screenToDocY = ::screenToDocY
                )
                for (pt in realPts) {
                    val last = activeRealPoints.lastOrNull()
                    if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 0.35f) {
                        activeRealPoints.add(pt)
                    }
                }
                activePredictedPoints = predPts
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!isStrokeInFlight) return
                val (realPts, _) = inkEngine.extractSamplesAndPredict(
                    event = event,
                    pointerIndex = 0,
                    screenToDocX = ::screenToDocX,
                    screenToDocY = ::screenToDocY
                )
                for (pt in realPts) {
                    val last = activeRealPoints.lastOrNull()
                    if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 0.25f) {
                        activeRealPoints.add(pt)
                    }
                }
                activePredictedPoints = emptyList()
                inkEngine.finishActiveStroke()
                isStrokeInFlight = false

                if (activeRealPoints.isNotEmpty()) {
                    val firstPt = activeRealPoints.first()
                    val pageIdx = NotePage.pageIndexAtY(firstPt.y, controller.pages.size)
                    val committed = NoteStroke(
                        id = UUID.randomUUID().toString(),
                        pageIndex = pageIdx,
                        tool = activeToolInFlight,
                        colorArgb = controller.selectedColorArgb,
                        thickness = controller.selectedThickness,
                        points = ArrayList(activeRealPoints)
                    )
                    activeRealPoints.clear()
                    controller.commitStroke(committed)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelInFlightDrawingIfAny()
            }
        }
    }

    private fun cancelInFlightDrawingIfAny() {
        if (isStrokeInFlight || isEraserActive) {
            activeRealPoints.clear()
            activePredictedPoints = emptyList()
            isStrokeInFlight = false
            isEraserActive = false
            erasedInCurrentSwipe.clear()
            inkEngine.finishActiveStroke()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        measureFrameFps()

        val palette = themePalette ?: return
        val z = controller.zoom
        val px = controller.panX
        val py = controller.panY

        // Görünüm alanının (Viewport) belge uzayındaki sınırlayıcı kutusu (Viewport Culling için)
        val viewportDocBounds = BoundingBox(
            minX = -px / z,
            minY = -py / z,
            maxX = (width - px) / z,
            maxY = (height - py) / z
        )

        canvas.save()
        canvas.translate(px, py)
        canvas.scale(z, z)

        // 1. Dikey Çoklu Sayfaları ve Kareli Defter Dokusunu Çiz (Sadece görünür sayfalar)
        drawVisiblePages(canvas, palette, viewportDocBounds)

        // 2. Sabitlenmiş (Committed / Dry) Çizgileri Bitmap Karo Önbelleğinden (Tile Cache) Çiz
        controller.tileCache.drawVisibleTiles(
            canvas = canvas,
            viewportDocBounds = viewportDocBounds,
            spatialIndex = controller.spatialIndex
        )

        // 3. Metin Kutularını (Text Blocks) Çiz
        drawTextBlocks(canvas, palette, viewportDocBounds)

        // 4. Aktif Çizilmekte Olan Çizgiyi (Wet Ink + Tahmin Edilen Noktalar) Çiz
        if (isStrokeInFlight && activeRealPoints.isNotEmpty()) {
            StrokeRenderer.drawWetStrokePoints(
                canvas = canvas,
                tool = activeToolInFlight,
                colorArgb = controller.selectedColorArgb,
                thickness = controller.selectedThickness,
                realPoints = activeRealPoints,
                predictedPoints = activePredictedPoints
            )
        }

        // 5. Silgi aktifse silgi dairesini göster
        if (isEraserActive && !lastEraserDocX.isNaN()) {
            eraserCursorPaint.color = palette.secondaryAccent.toArgbInt()
            val radius = max(14f, controller.selectedThickness * 2.5f)
            canvas.drawCircle(lastEraserDocX, lastEraserDocY, radius, eraserCursorPaint)
        }

        canvas.restore()
    }

    private fun drawVisiblePages(
        canvas: Canvas,
        palette: NoteThemePalette,
        viewportDocBounds: BoundingBox
    ) {
        pageBgPaint.color = palette.canvasBackground.toArgbInt()
        pageBorderPaint.color = palette.border.toArgbInt()
        minorGridPaint.color = palette.canvasMinorGrid.toArgbInt()
        majorGridPaint.color = palette.canvasMajorGrid.toArgbInt()
        pageLabelPaint.color = palette.textMuted.toArgbInt()

        val gridStep = 32f
        val pages = controller.pages

        for (page in pages) {
            val topY = NotePage.pageTopY(page.pageIndex)
            val bottomY = topY + page.heightPt
            val pageBounds = BoundingBox(0f, topY, page.widthPt, bottomY)
            if (!pageBounds.intersects(viewportDocBounds)) continue

            pageRect.set(0f, topY, page.widthPt, bottomY)
            canvas.drawRoundRect(pageRect, 20f, 20f, pageBgPaint)

            // Sayfa içi kareli ızgara (Graph Grid)
            var col = 0
            var gx = gridStep
            while (gx < page.widthPt) {
                val isMajor = col % 5 == 0
                canvas.drawLine(
                    gx,
                    topY,
                    gx,
                    bottomY,
                    if (isMajor) majorGridPaint else minorGridPaint
                )
                gx += gridStep
                col++
            }

            var row = 0
            var gy = topY + gridStep
            while (gy < bottomY) {
                val isMajor = row % 5 == 0
                canvas.drawLine(
                    0f,
                    gy,
                    page.widthPt,
                    gy,
                    if (isMajor) majorGridPaint else minorGridPaint
                )
                gy += gridStep
                row++
            }

            canvas.drawRoundRect(pageRect, 20f, 20f, pageBorderPaint)
            canvas.drawText(
                "#${page.pageIndex + 1}",
                page.widthPt - 64f,
                topY + 38f,
                pageLabelPaint
            )
        }
    }

    private fun drawTextBlocks(
        canvas: Canvas,
        palette: NoteThemePalette,
        viewportDocBounds: BoundingBox
    ) {
        textBlockBgPaint.color = palette.surfaceElevated.toArgbInt()
        textBlockBorderPaint.color = palette.border.toArgbInt()

        for (block in controller.textBlocks) {
            val blockBounds = BoundingBox(
                minX = block.x,
                minY = block.y,
                maxX = block.x + block.width,
                maxY = block.y + 80f
            )
            if (!blockBounds.intersects(viewportDocBounds)) continue

            textBlockPaint.color = block.colorArgb
            val lines = block.text.split('\n')
            val boxHeight = max(52f, lines.size * 34f + 20f)
            pageRect.set(block.x, block.y, block.x + block.width, block.y + boxHeight)
            canvas.drawRoundRect(pageRect, 12f, 12f, textBlockBgPaint)
            canvas.drawRoundRect(pageRect, 12f, 12f, textBlockBorderPaint)

            var textY = block.y + 32f
            for (line in lines) {
                canvas.drawText(line, block.x + 14f, textY, textBlockPaint)
                textY += 32f
            }
        }
    }

    private fun measureFrameFps() {
        val now = System.nanoTime()
        if (lastFrameNano != 0L) {
            val dtMs = (now - lastFrameNano) / 1_000_000f
            if (dtMs in 1f..250f) {
                val instantFps = 1000f / dtMs
                smoothedFps = (smoothedFps * 0.85f) + (instantFps * 0.15f)
            }
        }
        lastFrameNano = now
        controller.updateFps(
            fps = smoothedFps.roundToInt().coerceIn(30, 120),
            tiles = controller.tileCache.cachedTileCount
        )
    }

    private fun screenToDocX(screenX: Float): Float =
        (screenX - controller.panX) / controller.zoom

    private fun screenToDocY(screenY: Float): Float =
        (screenY - controller.panY) / controller.zoom

    private fun averageTouchX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount.coerceAtLeast(1)
    }

    private fun averageTouchY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount.coerceAtLeast(1)
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        val a = (alpha * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

