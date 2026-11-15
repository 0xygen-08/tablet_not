package com.tabletnotes.app.feature.editor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import com.tabletnotes.app.domain.model.PaperTemplate
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
 * Düşük gecikmeli Stylus + Parmak + Çoklu Kalem + Kare Seçim Modu (Lasso/Rectangle Selection)
 * ve Bağımsız Kağıt Rengi/Deseni (Noktalı, Kareli, Çizgili, Düz) destekli Tuval Görünümü.
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
    private var appliedRevision: Int = -1
    private var lastHudUpdateNano: Long = 0L

    // Aktif çizilmekte olan çizgi ("Wet Ink")
    private val activeRealPoints = ArrayList<StrokePoint>(256)
    private var activePredictedPoints: List<StrokePoint> = emptyList()
    private var activeToolInFlight: DrawingToolType = DrawingToolType.FINE_FOUNTAIN
    private var isStrokeInFlight: Boolean = false

    // Aktif silgi hareketi
    private val erasedInCurrentSwipe = LinkedHashMap<String, NoteStroke>()
    private var lastEraserDocX: Float = Float.NaN
    private var lastEraserDocY: Float = Float.NaN
    private var isEraserActive: Boolean = false

    // Kare Seçim Modu (Lasso / Rectangle Selection) Durumu
    private var lassoStartX: Float = 0f
    private var lassoStartY: Float = 0f
    private var isDraggingSelection: Boolean = false
    private var isResizingSelection: Boolean = false
    private var lastSelectionTouchDocX: Float = 0f
    private var lastSelectionTouchDocY: Float = 0f
    private var selectionMoveOriginalSnapshot: List<NoteStroke>? = null

    // Tek/İki parmak kaydırma (Pan) ve yakınlaştırma (Zoom) durumu
    private var isPanning: Boolean = false
    private var lastPanTouchX: Float = 0f
    private var lastPanTouchY: Float = 0f

    // FPS Ölçümü
    private var lastFrameNano: Long = 0L
    private var smoothedFps: Float = 60f

    // Boya (Paint) nesneleri
    private val pageBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x18000000
        style = Paint.Style.FILL
    }
    private val pageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    private val minorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }
    private val dotPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pageLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
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
    private val selectionBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x162962FF
        style = Paint.Style.FILL
    }
    private val selectionBoxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2962FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2962FF.toInt()
        style = Paint.Style.FILL
    }
    private val pageRect = RectF()
    private val selectionRectF = RectF()

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
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Sayfayı hem dikey hem yatay modda ekran ortasına hizala
            val fitZoom = ((w - 48f) / NotePage.DEFAULT_PAGE_WIDTH).coerceIn(0.65f, 1.35f)
            val centeredPanX = ((w - NotePage.DEFAULT_PAGE_WIDTH * fitZoom) * 0.5f).coerceAtLeast(16f)
            controller.updateZoomAndPan(newZoom = fitZoom, newPanX = centeredPanX, newPanY = 18f)
        }
    }

    fun syncState(revision: Int, palette: NoteThemePalette) {
        var changed = false
        if (themePalette != palette) {
            themePalette = palette
            changed = true
        }
        if (appliedRevision != revision) {
            appliedRevision = revision
            changed = true
        }
        if (changed) {
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

        isPanning = false
        val effectiveTool = if (decision == InputRouteDecision.HARDWARE_STYLUS_ERASER) {
            DrawingToolType.ERASER
        } else {
            controller.activeTool
        }

        when (effectiveTool) {
            DrawingToolType.TEXT -> handleTextToolTouch(event)
            DrawingToolType.ERASER -> handleEraserTouch(event)
            DrawingToolType.LASSO_SELECT -> handleLassoSelectionTouch(event)
            else -> handleInkDrawingTouch(event, effectiveTool)
        }
        return true
    }

    private fun handleLassoSelectionTouch(event: MotionEvent) {
        val docX = screenToDocX(event.x)
        val docY = screenToDocY(event.y)
        val existingBounds = controller.selectionBounds

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (existingBounds != null) {
                    val handleDist = hypot(docX - existingBounds.maxX, docY - existingBounds.maxY)
                    if (handleDist <= 28f) {
                        isResizingSelection = true
                        isDraggingSelection = false
                        lastSelectionTouchDocX = docX
                        lastSelectionTouchDocY = docY
                        return
                    }
                    if (existingBounds.contains(docX, docY)) {
                        isDraggingSelection = true
                        isResizingSelection = false
                        lastSelectionTouchDocX = docX
                        lastSelectionTouchDocY = docY
                        selectionMoveOriginalSnapshot = controller.getSelectedStrokes()
                        return
                    }
                }
                // Yeni kare seçim alanı başlat
                isDraggingSelection = false
                isResizingSelection = false
                lassoStartX = docX
                lassoStartY = docY
                controller.clearSelection()
                controller.updateLiveLassoRect(lassoStartX, lassoStartY, docX, docY)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizingSelection && existingBounds != null) {
                    val prevW = max(24f, lastSelectionTouchDocX - existingBounds.centerX)
                    val currW = max(24f, docX - existingBounds.centerX)
                    val ratio = (currW / prevW).coerceIn(0.85f, 1.18f)
                    if (kotlin.math.abs(ratio - 1f) > 0.03f) {
                        controller.scaleSelectedStrokes(ratio)
                        lastSelectionTouchDocX = docX
                        lastSelectionTouchDocY = docY
                        invalidate()
                    }
                } else if (isDraggingSelection) {
                    val dx = docX - lastSelectionTouchDocX
                    val dy = docY - lastSelectionTouchDocY
                    if (hypot(dx, dy) >= 0.8f) {
                        controller.translateSelectedStrokes(dx, dy, recordUndo = false)
                        lastSelectionTouchDocX = docX
                        lastSelectionTouchDocY = docY
                        invalidate()
                    }
                } else {
                    controller.updateLiveLassoRect(lassoStartX, lassoStartY, docX, docY)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingSelection) {
                    val orig = selectionMoveOriginalSnapshot
                    if (orig != null) {
                        controller.translateSelectedStrokes(0f, 0f, recordUndo = true, originalSnapshot = orig)
                    }
                    selectionMoveOriginalSnapshot = null
                    isDraggingSelection = false
                } else if (isResizingSelection) {
                    isResizingSelection = false
                } else {
                    controller.finishLassoSelection()
                }
                invalidate()
            }
        }
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

        val viewportDocBounds = BoundingBox(
            minX = -px / z,
            minY = -py / z,
            maxX = (width - px) / z,
            maxY = (height - py) / z
        )

        canvas.save()
        canvas.translate(px, py)
        canvas.scale(z, z)

        // 1. Bağımsız Kağıt Rengi ve Deseniyle Sayfaları Çiz (Noktalı, Kareli, Çizgili, Düz)
        drawVisiblePages(canvas, viewportDocBounds)

        // 2. Sabitlenmiş Çizgileri Bitmap Karo Önbelleğinden Çiz
        controller.tileCache.drawVisibleTiles(
            canvas = canvas,
            viewportDocBounds = viewportDocBounds,
            spatialIndex = controller.spatialIndex
        )

        // 3. Metin Kutularını Çiz
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

        // 5. Kare Seçim Modu (Lasso / Selection Rectangle & Resize Handles) Çiz
        drawSelectionOverlay(canvas)

        // 6. Silgi aktifse silgi dairesini göster
        if (isEraserActive && !lastEraserDocX.isNaN()) {
            eraserCursorPaint.color = palette.secondaryAccent.toArgbInt()
            val radius = max(14f, controller.selectedThickness * 2.5f)
            canvas.drawCircle(lastEraserDocX, lastEraserDocY, radius, eraserCursorPaint)
        }

        canvas.restore()
    }

    private fun drawSelectionOverlay(canvas: Canvas) {
        val activeLasso = controller.liveLassoRect
        if (activeLasso != null) {
            selectionRectF.set(activeLasso.minX, activeLasso.minY, activeLasso.maxX, activeLasso.maxY)
            canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxFillPaint)
            canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxStrokePaint)
        }

        val selBounds = controller.selectionBounds
        if (selBounds != null) {
            selectionRectF.set(selBounds.minX, selBounds.minY, selBounds.maxX, selBounds.maxY)
            canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxFillPaint)
            canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxStrokePaint)

            // 4 Köşe Tutamacı (Sağ alt köşe boyutlandırma tutamacıdır)
            val r = 7.5f
            canvas.drawCircle(selBounds.minX, selBounds.minY, r, selectionHandlePaint)
            canvas.drawCircle(selBounds.maxX, selBounds.minY, r, selectionHandlePaint)
            canvas.drawCircle(selBounds.minX, selBounds.maxY, r, selectionHandlePaint)
            canvas.drawCircle(selBounds.maxX, selBounds.maxY, r * 1.35f, selectionHandlePaint)
        }
    }

    private fun drawVisiblePages(
        canvas: Canvas,
        viewportDocBounds: BoundingBox
    ) {
        val pColor = controller.paperColor
        val pTemplate = controller.paperTemplate

        pageBgPaint.color = pColor.bgArgb
        pageBorderPaint.color = pColor.majorPatternArgb
        minorGridPaint.color = pColor.minorPatternArgb
        majorGridPaint.color = pColor.majorPatternArgb
        dotPatternPaint.color = pColor.majorPatternArgb
        pageLabelPaint.color = pColor.majorPatternArgb

        val gridStep = 28f
        val pages = controller.pages

        for (page in pages) {
            val topY = NotePage.pageTopY(page.pageIndex)
            val bottomY = topY + page.heightPt
            val pageBounds = BoundingBox(0f, topY, page.widthPt, bottomY)
            if (!pageBounds.intersects(viewportDocBounds)) continue

            // Hafif sayfa gölgesi + 24dp yuvarlatılmış kağıt köşeleri (Taslak 3 ile uyumlu)
            pageRect.set(4f, topY + 6f, page.widthPt + 4f, bottomY + 6f)
            canvas.drawRoundRect(pageRect, 24f, 24f, pageShadowPaint)

            pageRect.set(0f, topY, page.widthPt, bottomY)
            canvas.drawRoundRect(pageRect, 24f, 24f, pageBgPaint)

            when (pTemplate) {
                PaperTemplate.DOTTED -> {
                    var gy = topY + gridStep
                    while (gy < bottomY - 12f) {
                        var gx = gridStep
                        while (gx < page.widthPt - 12f) {
                            canvas.drawCircle(gx, gy, 1.8f, dotPatternPaint)
                            gx += gridStep
                        }
                        gy += gridStep
                    }
                }
                PaperTemplate.GRID -> {
                    var col = 0
                    var gx = gridStep
                    while (gx < page.widthPt) {
                        val isMajor = col % 5 == 0
                        canvas.drawLine(gx, topY, gx, bottomY, if (isMajor) majorGridPaint else minorGridPaint)
                        gx += gridStep
                        col++
                    }
                    var row = 0
                    var gy = topY + gridStep
                    while (gy < bottomY) {
                        val isMajor = row % 5 == 0
                        canvas.drawLine(0f, gy, page.widthPt, gy, if (isMajor) majorGridPaint else minorGridPaint)
                        gy += gridStep
                        row++
                    }
                }
                PaperTemplate.LINED -> {
                    val lineSpacing = 36f
                    var gy = topY + 64f
                    while (gy < bottomY - 24f) {
                        canvas.drawLine(32f, gy, page.widthPt - 32f, gy, minorGridPaint)
                        gy += lineSpacing
                    }
                }
                PaperTemplate.BLANK -> {
                    // Düz boş kağıt
                }
            }

            canvas.drawRoundRect(pageRect, 24f, 24f, pageBorderPaint)
            canvas.drawText(
                "#${page.pageIndex + 1}",
                page.widthPt - 58f,
                topY + 36f,
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
        if (!isStrokeInFlight && !isPanning && !isEraserActive && !isDraggingSelection) return
        val now = System.nanoTime()
        if (lastFrameNano != 0L) {
            val dtMs = (now - lastFrameNano) / 1_000_000f
            if (dtMs in 1f..250f) {
                val instantFps = 1000f / dtMs
                smoothedFps = (smoothedFps * 0.85f) + (instantFps * 0.15f)
            }
        }
        lastFrameNano = now
        if (now - lastHudUpdateNano >= 500_000_000L) {
            lastHudUpdateNano = now
            val roundedFps = smoothedFps.roundToInt().coerceIn(30, 120)
            val tiles = controller.tileCache.cachedTileCount
            post {
                controller.updateFps(fps = roundedFps, tiles = tiles)
            }
        }
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
