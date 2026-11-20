package com.tabletnotes.app.feature.editor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.tabletnotes.app.core.designsystem.theme.NoteThemePalette
import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.EraserMode
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
 * Düşük gecikmeli Stylus + Parmak (Her zaman gezinme/yakınlaştırma) + Lazer İşaretçi +
 * Şekil Algılama/Düzenleme + Çizgi/Alan Silgisi + Sayfa İçi Sınırlandırma destekli Tuval Görünümü.
 */
@SuppressLint("ViewConstructor")
class NoteEditorCanvasView(
    context: Context,
    private val controller: NoteEditorController,
    private val onTapForTextBlock: (docX: Float, docY: Float) -> Unit
) : View(context) {

    private val palmFilter = PalmRejectionFilter()
    private val inkEngine = LowLatencyInkEngine(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val clearLaserRunnable = Runnable {
        val elapsed = System.currentTimeMillis() - controller.lastLaserActivityMillis
        if (controller.laserStrokes.isNotEmpty() && elapsed >= controller.laserDurationMillis - 40L) {
            controller.clearLaserStrokes()
            invalidate()
        } else if (controller.laserStrokes.isNotEmpty()) {
            scheduleLaserTimeout()
        }
    }

    private var themePalette: NoteThemePalette? = null
    private var appliedRevision: Int = -1
    private var lastHudUpdateNano: Long = 0L

    // Aktif çizilmekte olan çizgi ("Wet Ink")
    private val activeRealPoints = ArrayList<StrokePoint>(256)
    private var activePredictedPoints: List<StrokePoint> = emptyList()
    private var activeToolInFlight: DrawingToolType = DrawingToolType.FINE_FOUNTAIN
    private var activeStrokePageIndex: Int = 0
    private var isStrokeInFlight: Boolean = false

    // Aktif silgi hareketi
    private val erasedInCurrentSwipe = LinkedHashMap<String, NoteStroke>()
    private val areaBeforeSnapshotMap = LinkedHashMap<String, NoteStroke>()
    private val areaCreatedIds = HashSet<String>()
    private var areaEraseHadChanges: Boolean = false
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

    // Şekil Algılama (Pending Shape) Taşıma / Boyutlandırma Durumu
    private var isDraggingPendingShape: Boolean = false
    private var isResizingPendingShape: Boolean = false
    private var lastShapeTouchDocX: Float = 0f
    private var lastShapeTouchDocY: Float = 0f

    // Tek/İki parmak kaydırma (Pan) ve yakınlaştırma (Zoom) durumu
    private var isPanning: Boolean = false
    private var lastPanTouchX: Float = 0f
    private var lastPanTouchY: Float = 0f
    private var lastScaleFocusX: Float = 0f
    private var lastScaleFocusY: Float = 0f

    // En alt sayfada aşağı kaydırarak yeni sayfa ekleme (Overscroll Pull) durumu
    private var pullOverscrollPx: Float = 0f
    private var pullOverscrollStartMillis: Long = 0L

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
    private val shapeHintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2962FF.toInt()
        textSize = 22f
        isFakeBoldText = true
    }
    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x66FF2A3B
    }
    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFF1E38.toInt()
    }
    private val laserCenterWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val pullPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pullTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val pageRect = RectF()
    private val selectionRectF = RectF()
    private val pageClipPath = Path()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelInFlightDrawingIfAny()
                isPanning = true
                lastScaleFocusX = detector.focusX
                lastScaleFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val rawFactor = detector.scaleFactor
                if (!rawFactor.isFinite() || rawFactor <= 0f) return false

                val scaleFactor = rawFactor.coerceIn(0.88f, 1.14f)
                val oldZoom = controller.zoom
                val newZoom = (oldZoom * scaleFactor).coerceIn(
                    NoteEditorController.MIN_ZOOM,
                    NoteEditorController.MAX_ZOOM
                )

                val focusX = detector.focusX
                val focusY = detector.focusY
                val focusDx = focusX - lastScaleFocusX
                val focusDy = focusY - lastScaleFocusY
                lastScaleFocusX = focusX
                lastScaleFocusY = focusY

                // Hem odak noktasından zoom yap hem iki parmağın kayma yönünü pürüzsüz uygula
                val docFocusX = (lastScaleFocusX - focusDx - controller.panX) / oldZoom
                val docFocusY = (lastScaleFocusY - focusDy - controller.panY) / oldZoom
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

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacks(clearLaserRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            controller.updateViewportDimensions(w, h, forceCenter = (oldw == 0 || oldw != w))
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
        // Bekleyen şekil varsa: hem parmak hem kalem şeklin içinden taşıyabilir veya köşesinden boyutlandırabilir
        if (controller.pendingShape != null && handlePendingShapeTouchIfHit(event)) {
            return true
        }

        val decision = palmFilter.evaluate(event = event, stylusOnlyMode = true)

        if (decision == InputRouteDecision.REJECTED_PALM) {
            cancelInFlightDrawingIfAny()
            return true
        }

        if (decision == InputRouteDecision.PAN_ZOOM) {
            // Kare seçim modunda aktif kutu içindeyse parmakla da taşınabilsin
            if (controller.activeTool == DrawingToolType.LASSO_SELECT &&
                controller.selectionBounds != null &&
                event.pointerCount == 1 &&
                isSelectionGestureHitOrOngoing(event)
            ) {
                handleLassoSelectionTouch(event)
                return true
            }

            cancelInFlightDrawingIfAny()
            scaleDetector.onTouchEvent(event)
            handlePanTouchEvent(event)
            return true
        }

        isPanning = false
        resetPullToAddPage()
        val effectiveTool = if (decision == InputRouteDecision.HARDWARE_STYLUS_ERASER) {
            DrawingToolType.ERASER
        } else {
            controller.activeTool
        }

        when (effectiveTool) {
            DrawingToolType.TEXT -> handleTextToolTouch(event)
            DrawingToolType.ERASER -> handleEraserTouch(event)
            DrawingToolType.LASSO_SELECT -> handleLassoSelectionTouch(event)
            DrawingToolType.LASER_POINTER -> handleInkDrawingTouch(event, DrawingToolType.LASER_POINTER)
            DrawingToolType.SHAPE_TOOL -> handleInkDrawingTouch(event, DrawingToolType.SHAPE_TOOL)
            else -> handleInkDrawingTouch(event, effectiveTool)
        }
        return true
    }

    // --- BEKLEYEN ŞEKİL TAŞIMA / BOYUTLANDIRMA ---

    private fun handlePendingShapeTouchIfHit(event: MotionEvent): Boolean {
        val pending = controller.pendingShape ?: return false
        if (event.pointerCount >= 2) {
            // 2 parmak zoom/pan her zaman önceliklidir
            isDraggingPendingShape = false
            isResizingPendingShape = false
            return false
        }

        val docX = screenToDocX(event.x)
        val docY = screenToDocY(event.y)
        val bounds = pending.bounds

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handleThreshold = 34f / controller.zoom
                val hitBottomRight = hypot(docX - bounds.maxX, docY - bounds.maxY) <= handleThreshold
                val hitTopLeft = hypot(docX - bounds.minX, docY - bounds.minY) <= handleThreshold
                val hitTopRight = hypot(docX - bounds.maxX, docY - bounds.minY) <= handleThreshold
                val hitBottomLeft = hypot(docX - bounds.minX, docY - bounds.maxY) <= handleThreshold

                if (hitBottomRight || hitTopLeft || hitTopRight || hitBottomLeft) {
                    isResizingPendingShape = true
                    isDraggingPendingShape = false
                    lastShapeTouchDocX = docX
                    lastShapeTouchDocY = docY
                    return true
                }
                if (bounds.inflated(16f).contains(docX, docY)) {
                    isDraggingPendingShape = true
                    isResizingPendingShape = false
                    lastShapeTouchDocX = docX
                    lastShapeTouchDocY = docY
                    return true
                }

                // Şekil dışına dokunuldu -> şekli kalıcı olarak onayla!
                controller.confirmPendingShape()
                invalidate()
                // Eğer kullanıcı başka kalem aracıyla değil parmakla dokunduysa gezinme devam etsin
                return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizingPendingShape) {
                    val prevDist = max(24f, hypot(lastShapeTouchDocX - bounds.centerX, lastShapeTouchDocY - bounds.centerY))
                    val currDist = max(24f, hypot(docX - bounds.centerX, docY - bounds.centerY))
                    val ratio = (currDist / prevDist).coerceIn(0.85f, 1.18f)
                    if (kotlin.math.abs(ratio - 1f) > 0.015f) {
                        controller.scalePendingShape(ratio)
                        lastShapeTouchDocX = docX
                        lastShapeTouchDocY = docY
                        invalidate()
                    }
                    return true
                }
                if (isDraggingPendingShape) {
                    val dx = docX - lastShapeTouchDocX
                    val dy = docY - lastShapeTouchDocY
                    if (hypot(dx, dy) >= 0.5f) {
                        controller.translatePendingShape(dx, dy)
                        lastShapeTouchDocX = docX
                        lastShapeTouchDocY = docY
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingPendingShape || isResizingPendingShape) {
                    isDraggingPendingShape = false
                    isResizingPendingShape = false
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    private fun isSelectionGestureHitOrOngoing(event: MotionEvent): Boolean {
        if (isDraggingSelection || isResizingSelection) return true
        val bounds = controller.selectionBounds ?: return false
        val docX = screenToDocX(event.x)
        val docY = screenToDocY(event.y)
        return bounds.inflated(28f / controller.zoom).contains(docX, docY)
    }

    private fun handleLassoSelectionTouch(event: MotionEvent) {
        val docX = screenToDocX(event.x)
        val docY = screenToDocY(event.y)
        val existingBounds = controller.selectionBounds

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (existingBounds != null) {
                    val handleDist = hypot(docX - existingBounds.maxX, docY - existingBounds.maxY)
                    if (handleDist <= 32f / controller.zoom) {
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
                lastPanTouchX = averageTouchX(event, excludeIndex = -1)
                lastPanTouchY = averageTouchY(event, excludeIndex = -1)
                isPanning = true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Kalkan parmağı hariç tutarak kalan parmağın konumuna zıplamadan adapte ol
                val upIndex = event.actionIndex
                lastPanTouchX = averageTouchX(event, excludeIndex = upIndex)
                lastPanTouchY = averageTouchY(event, excludeIndex = upIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && isPanning) {
                    val currX = averageTouchX(event, excludeIndex = -1)
                    val currY = averageTouchY(event, excludeIndex = -1)
                    val dx = currX - lastPanTouchX
                    val dy = currY - lastPanTouchY
                    lastPanTouchX = currX
                    lastPanTouchY = currY

                    val prevPanY = controller.panY
                    controller.updateZoomAndPan(
                        newZoom = controller.zoom,
                        newPanX = controller.panX + dx,
                        newPanY = controller.panY + dy
                    )

                    // En alt sayfanın altına gelindiğinde kullanıcı aşağı çekmeye devam ederse yeni sayfa ekleme
                    checkPullDownForNewPage(dy = dy, prevPanY = prevPanY)
                    invalidate()
                } else {
                    lastPanTouchX = averageTouchX(event, excludeIndex = -1)
                    lastPanTouchY = averageTouchY(event, excludeIndex = -1)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                resetPullToAddPage()
                invalidate()
            }
        }
    }

    private fun checkPullDownForNewPage(dy: Float, prevPanY: Float) {
        val lastPageIdx = (controller.pages.size - 1).coerceAtLeast(0)
        val lastPageBottomDocY = NotePage.pageTopY(lastPageIdx) + NotePage.DEFAULT_PAGE_HEIGHT
        val lastPageBottomScreenY = controller.panY + lastPageBottomDocY * controller.zoom

        // Kullanıcı parmağını yukarı doğru (dy < 0) çekerek sayfaların en altına dayandığında
        if (dy < -1.5f && lastPageBottomScreenY <= height * 0.72f) {
            val now = System.currentTimeMillis()
            if (pullOverscrollStartMillis == 0L) {
                pullOverscrollStartMillis = now
            }
            pullOverscrollPx += -dy
            val holdDuration = now - pullOverscrollStartMillis
            val distanceProgress = (pullOverscrollPx / 160f).coerceIn(0f, 1f)
            val timeProgress = (holdDuration / 500f).coerceIn(0f, 1f)
            val combinedProgress = (distanceProgress * 0.65f + timeProgress * 0.35f).coerceIn(0f, 1f)
            controller.updatePullAddPageProgress(combinedProgress)

            if (pullOverscrollPx >= 155f && holdDuration >= 360L) {
                resetPullToAddPage()
                controller.addPage(scrollToNewPage = true)
            }
        } else if (dy > 6f || lastPageBottomScreenY > height * 0.85f || prevPanY < controller.panY) {
            resetPullToAddPage()
        }
    }

    private fun resetPullToAddPage() {
        pullOverscrollPx = 0f
        pullOverscrollStartMillis = 0L
        controller.updatePullAddPageProgress(0f)
    }

    private fun handleTextToolTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val docX = screenToDocX(event.x)
            val docY = screenToDocY(event.y)
            if (isPointInsideAnyPage(docX, docY) != null) {
                onTapForTextBlock(docX, docY)
            }
        }
    }

    private fun handleEraserTouch(event: MotionEvent) {
        val radius = controller.eraserRadius
        val isAreaMode = controller.eraserMode == EraserMode.AREA_ERASER

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                erasedInCurrentSwipe.clear()
                areaBeforeSnapshotMap.clear()
                areaCreatedIds.clear()
                areaEraseHadChanges = false
                isEraserActive = true

                val docX = screenToDocX(event.x)
                val docY = screenToDocY(event.y)
                lastEraserDocX = docX
                lastEraserDocY = docY

                if (isAreaMode) {
                    if (controller.eraseAreaAlongSegment(docX, docY, docX, docY, radius, areaBeforeSnapshotMap, areaCreatedIds)) {
                        areaEraseHadChanges = true
                    }
                } else {
                    val hits = controller.eraseStrokesAlongSegment(docX, docY, docX, docY, radius)
                    for (h in hits) erasedInCurrentSwipe[h.id] = h
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isEraserActive) return
                for (h in 0 until event.historySize) {
                    val hx = screenToDocX(event.getHistoricalX(0, h))
                    val hy = screenToDocY(event.getHistoricalY(0, h))
                    performEraserStep(lastEraserDocX, lastEraserDocY, hx, hy, radius, isAreaMode)
                    lastEraserDocX = hx
                    lastEraserDocY = hy
                }
                val cx = screenToDocX(event.x)
                val cy = screenToDocY(event.y)
                performEraserStep(lastEraserDocX, lastEraserDocY, cx, cy, radius, isAreaMode)
                lastEraserDocX = cx
                lastEraserDocY = cy
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (isEraserActive) {
                    if (isAreaMode && areaEraseHadChanges) {
                        controller.recordAreaEraseBatch(
                            beforeStrokes = areaBeforeSnapshotMap.values.toList(),
                            createdFragmentIds = areaCreatedIds.toSet()
                        )
                    } else if (!isAreaMode) {
                        controller.recordEraseBatch(erasedInCurrentSwipe.values.toList())
                    }
                }
                clearEraserBuffers()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                clearEraserBuffers()
                invalidate()
            }
        }
    }

    private fun performEraserStep(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        isAreaMode: Boolean
    ) {
        if (isAreaMode) {
            if (controller.eraseAreaAlongSegment(x0, y0, x1, y1, radius, areaBeforeSnapshotMap, areaCreatedIds)) {
                areaEraseHadChanges = true
            }
        } else {
            val hits = controller.eraseStrokesAlongSegment(x0, y0, x1, y1, radius)
            for (stroke in hits) erasedInCurrentSwipe[stroke.id] = stroke
        }
    }

    private fun clearEraserBuffers() {
        erasedInCurrentSwipe.clear()
        areaBeforeSnapshotMap.clear()
        areaCreatedIds.clear()
        areaEraseHadChanges = false
        isEraserActive = false
    }

    private fun handleInkDrawingTouch(event: MotionEvent, tool: DrawingToolType) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val startDocX = screenToDocX(event.x)
                val startDocY = screenToDocY(event.y)
                // Çizim sadece kağıt sayfalarının sınırları içinde başlatılabilir!
                val pageIdx = isPointInsideAnyPage(startDocX, startDocY) ?: return

                activeStrokePageIndex = pageIdx
                activeRealPoints.clear()
                activePredictedPoints = emptyList()
                activeToolInFlight = tool
                isStrokeInFlight = true

                if (tool == DrawingToolType.LASER_POINTER) {
                    mainHandler.removeCallbacks(clearLaserRunnable)
                    controller.touchLaserActivity()
                }

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
                activeRealPoints.addAll(clampPointsToPage(realPts, pageIdx))
                activePredictedPoints = clampPointsToPage(predPts, pageIdx)
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
                val clampedReal = clampPointsToPage(realPts, activeStrokePageIndex)
                for (pt in clampedReal) {
                    val last = activeRealPoints.lastOrNull()
                    if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 0.35f) {
                        activeRealPoints.add(pt)
                    }
                }
                activePredictedPoints = clampPointsToPage(predPts, activeStrokePageIndex)
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
                val clampedReal = clampPointsToPage(realPts, activeStrokePageIndex)
                for (pt in clampedReal) {
                    val last = activeRealPoints.lastOrNull()
                    if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 0.25f) {
                        activeRealPoints.add(pt)
                    }
                }
                activePredictedPoints = emptyList()
                inkEngine.finishActiveStroke()
                isStrokeInFlight = false

                if (activeRealPoints.isNotEmpty()) {
                    val captured = ArrayList(activeRealPoints)
                    activeRealPoints.clear()

                    when (activeToolInFlight) {
                        DrawingToolType.LASER_POINTER -> {
                            val laserStroke = NoteStroke(
                                id = UUID.randomUUID().toString(),
                                pageIndex = activeStrokePageIndex,
                                tool = DrawingToolType.PEN,
                                colorArgb = 0xFFFF1E38.toInt(),
                                thickness = max(4.5f, controller.selectedThickness),
                                points = captured
                            )
                            controller.addLaserStroke(laserStroke)
                            scheduleLaserTimeout()
                        }
                        DrawingToolType.SHAPE_TOOL -> {
                            val recognized = controller.tryRecognizeAndSetPendingShape(
                                rawPoints = captured,
                                pageIndex = activeStrokePageIndex
                            )
                            if (recognized == null) {
                                controller.commitStroke(
                                    NoteStroke(
                                        id = UUID.randomUUID().toString(),
                                        pageIndex = activeStrokePageIndex,
                                        tool = DrawingToolType.PEN,
                                        colorArgb = controller.selectedColorArgb,
                                        thickness = controller.selectedThickness,
                                        points = captured
                                    )
                                )
                            }
                        }
                        else -> {
                            controller.commitStroke(
                                NoteStroke(
                                    id = UUID.randomUUID().toString(),
                                    pageIndex = activeStrokePageIndex,
                                    tool = activeToolInFlight,
                                    colorArgb = controller.selectedColorArgb,
                                    thickness = controller.selectedThickness,
                                    points = captured
                                )
                            )
                        }
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelInFlightDrawingIfAny()
            }
        }
    }

    private fun scheduleLaserTimeout() {
        mainHandler.removeCallbacks(clearLaserRunnable)
        mainHandler.postDelayed(clearLaserRunnable, controller.laserDurationMillis)
    }

    private fun isPointInsideAnyPage(docX: Float, docY: Float): Int? {
        if (docX < 0f || docX > NotePage.DEFAULT_PAGE_WIDTH) return null
        for (page in controller.pages) {
            val topY = NotePage.pageTopY(page.pageIndex)
            val bottomY = topY + page.heightPt
            if (docY in topY..bottomY) {
                return page.pageIndex
            }
        }
        return null
    }

    private fun clampPointsToPage(points: List<StrokePoint>, pageIndex: Int): List<StrokePoint> {
        val topY = NotePage.pageTopY(pageIndex)
        val bottomY = topY + NotePage.DEFAULT_PAGE_HEIGHT
        return points.map { pt ->
            pt.copy(
                x = pt.x.coerceIn(4f, NotePage.DEFAULT_PAGE_WIDTH - 4f),
                y = pt.y.coerceIn(topY + 4f, bottomY - 4f)
            )
        }
    }

    private fun cancelInFlightDrawingIfAny() {
        if (isStrokeInFlight || isEraserActive) {
            activeRealPoints.clear()
            activePredictedPoints = emptyList()
            isStrokeInFlight = false
            clearEraserBuffers()
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

        // 1. Bağımsız Kağıt Rengi ve Deseniyle Sayfaları Çiz
        drawVisiblePages(canvas, viewportDocBounds)

        // 2. Çizgileri ve Metin Kutularını Sayfa Sınırlarına Kırparak Çiz (Bulanıklığı Sıfırlar!)
        drawCrispCommittedContent(canvas, palette, viewportDocBounds)

        // 3. Aktif Çizilmekte Olan Çizgiyi (Wet Ink) Sayfa İçine Kırparak Çiz
        if (isStrokeInFlight && activeRealPoints.isNotEmpty()) {
            val topY = NotePage.pageTopY(activeStrokePageIndex)
            pageRect.set(0f, topY, NotePage.DEFAULT_PAGE_WIDTH, topY + NotePage.DEFAULT_PAGE_HEIGHT)
            pageClipPath.reset()
            pageClipPath.addRoundRect(pageRect, 24f, 24f, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(pageClipPath)
            if (activeToolInFlight == DrawingToolType.LASER_POINTER) {
                drawLaserStrokePoints(canvas, activeRealPoints)
            } else {
                StrokeRenderer.drawWetStrokePoints(
                    canvas = canvas,
                    tool = activeToolInFlight,
                    colorArgb = controller.selectedColorArgb,
                    thickness = controller.selectedThickness,
                    realPoints = activeRealPoints,
                    predictedPoints = activePredictedPoints
                )
            }
            canvas.restore()
        }

        // 4. Geçici Lazer İşaretçi Çizgilerini Çiz (Kalıcı değildir)
        if (controller.laserStrokes.isNotEmpty()) {
            for (laserStroke in controller.laserStrokes) {
                drawLaserStrokePoints(canvas, laserStroke.points)
            }
        }

        // 5. Bekleyen Şekil Algılama Önizlemesi & Köşe Tutamaçları
        drawPendingShapeOverlay(canvas)

        // 6. Kare Seçim Modu (Lasso / Selection Rectangle & Resize Handles) Çiz
        drawSelectionOverlay(canvas)

        // 7. Silgi aktifse silgi dairesini göster
        if (isEraserActive && !lastEraserDocX.isNaN()) {
            eraserCursorPaint.color = palette.secondaryAccent.toArgbInt()
            canvas.drawCircle(lastEraserDocX, lastEraserDocY, controller.eraserRadius, eraserCursorPaint)
        }

        // 8. En altta aşağı kaydırarak yeni sayfa ekleme göstergesi
        drawPullToAddPageIndicator(canvas, palette)

        canvas.restore()
    }

    /**
     * Kalem kaldırıldığında çizgilerin bulanıklaşmasını önlemek için normal belgelerde (<2500 çizgi)
     * düşük çözünürlüklü bitmap karo yerine doğrudan kristal netliğinde vektör çizimi yapar;
     * 10.000 çizgi stres testi gibi ağır durumlarda 2x süper-örneklemeli BitmapTileCache kullanır.
     */
    private fun drawCrispCommittedContent(
        canvas: Canvas,
        palette: NoteThemePalette,
        viewportDocBounds: BoundingBox
    ) {
        val visiblePages = controller.pages.filter { page ->
            val topY = NotePage.pageTopY(page.pageIndex)
            BoundingBox(0f, topY, page.widthPt, topY + page.heightPt).intersects(viewportDocBounds)
        }

        for (page in visiblePages) {
            val topY = NotePage.pageTopY(page.pageIndex)
            pageRect.set(0f, topY, page.widthPt, topY + page.heightPt)
            pageClipPath.reset()
            pageClipPath.addRoundRect(pageRect, 24f, 24f, Path.Direction.CW)

            canvas.save()
            canvas.clipPath(pageClipPath)

            val pageDocBounds = BoundingBox(0f, topY, page.widthPt, topY + page.heightPt)
            val intersectedViewport = BoundingBox(
                minX = max(pageDocBounds.minX, viewportDocBounds.minX),
                minY = max(pageDocBounds.minY, viewportDocBounds.minY),
                maxX = minOf(pageDocBounds.maxX, viewportDocBounds.maxX),
                maxY = minOf(pageDocBounds.maxY, viewportDocBounds.maxY)
            )

            if (controller.strokeCount <= 2_500) {
                val strokes = controller.spatialIndex.queryViewport(intersectedViewport)
                for (i in strokes.indices) {
                    StrokeRenderer.drawStroke(canvas, strokes[i])
                }
            } else {
                controller.tileCache.drawVisibleTiles(
                    canvas = canvas,
                    viewportDocBounds = intersectedViewport,
                    spatialIndex = controller.spatialIndex
                )
            }

            drawTextBlocksForPage(canvas, palette, intersectedViewport)
            canvas.restore()

            // Sayfa kenarlığını en üste tekrar çiz ki klip kenarı temiz görünsün
            canvas.drawRoundRect(pageRect, 24f, 24f, pageBorderPaint)
        }
    }

    private fun drawLaserStrokePoints(canvas: Canvas, points: List<StrokePoint>) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, 12f, laserGlowPaint)
            canvas.drawCircle(points[0].x, points[0].y, 5f, laserCorePaint)
            return
        }
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        laserGlowPaint.strokeWidth = 14f
        laserCorePaint.strokeWidth = 6.5f
        laserCenterWhitePaint.strokeWidth = 2.5f
        canvas.drawPath(path, laserGlowPaint)
        canvas.drawPath(path, laserCorePaint)
        canvas.drawPath(path, laserCenterWhitePaint)
    }

    private fun drawPendingShapeOverlay(canvas: Canvas) {
        val pending = controller.pendingShape ?: return
        val previewStroke = NoteStroke(
            id = "pending_shape",
            pageIndex = pending.pageIndex,
            tool = DrawingToolType.PEN,
            colorArgb = pending.colorArgb,
            thickness = pending.thickness,
            points = pending.points
        )
        StrokeRenderer.drawStroke(canvas, previewStroke)

        val b = pending.bounds
        selectionRectF.set(b.minX, b.minY, b.maxX, b.maxY)
        canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxFillPaint)
        canvas.drawRoundRect(selectionRectF, 8f, 8f, selectionBoxStrokePaint)

        val r = 9f
        canvas.drawCircle(b.minX, b.minY, r, selectionHandlePaint)
        canvas.drawCircle(b.maxX, b.minY, r, selectionHandlePaint)
        canvas.drawCircle(b.minX, b.maxY, r, selectionHandlePaint)
        canvas.drawCircle(b.maxX, b.maxY, r * 1.25f, selectionHandlePaint)

        canvas.drawText(
            "${pending.labelTr} • Boyutlandır / Taşı (Onay için dışarı dokun)",
            b.minX,
            (b.minY - 12f).coerceAtLeast(24f),
            shapeHintTextPaint
        )
    }

    private fun drawPullToAddPageIndicator(canvas: Canvas, palette: NoteThemePalette) {
        val progress = controller.pullAddPageProgress
        if (progress <= 0.05f) return

        val lastIdx = (controller.pages.size - 1).coerceAtLeast(0)
        val bottomY = NotePage.pageTopY(lastIdx) + NotePage.DEFAULT_PAGE_HEIGHT
        val centerX = NotePage.DEFAULT_PAGE_WIDTH * 0.5f

        pullPillPaint.color = palette.primaryAccent.toArgbInt()
        pullTextPaint.color = palette.onPrimaryAccent.toArgbInt()

        pageRect.set(centerX - 240f, bottomY + 20f, centerX + 240f, bottomY + 68f)
        canvas.drawRoundRect(pageRect, 24f, 24f, pullPillPaint)
        val label = if (progress >= 0.85f) {
            "+ Yeni Sayfa Ekleniyor..."
        } else {
            "+ Yeni sayfa eklemek için çekin (${(progress * 100).roundToInt()}%)"
        }
        canvas.drawText(label, centerX, bottomY + 52f, pullTextPaint)
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
                PaperTemplate.BLANK -> Unit
            }

            canvas.drawText(
                "#${page.pageIndex + 1}",
                page.widthPt - 58f,
                topY + 36f,
                pageLabelPaint
            )
        }
    }

    private fun drawTextBlocksForPage(
        canvas: Canvas,
        palette: NoteThemePalette,
        pageDocBounds: BoundingBox
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
            if (!blockBounds.intersects(pageDocBounds)) continue

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

    private fun averageTouchX(event: MotionEvent, excludeIndex: Int): Float {
        var sum = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == excludeIndex) continue
            sum += event.getX(i)
            count++
        }
        return if (count > 0) sum / count else event.x
    }

    private fun averageTouchY(event: MotionEvent, excludeIndex: Int): Float {
        var sum = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == excludeIndex) continue
            sum += event.getY(i)
            count++
        }
        return if (count > 0) sum / count else event.y
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
        val a = (alpha * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
