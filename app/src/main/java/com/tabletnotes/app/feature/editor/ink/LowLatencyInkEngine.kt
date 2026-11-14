package com.tabletnotes.app.feature.editor.ink

import android.graphics.Canvas
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.input.motionprediction.MotionEventPredictor
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.StrokePoint

/**
 * Düşük gecikmeli çizim (Wet Ink) motoru:
 * 1. `MotionEvent` içindeki tüm tarihsel (historical) noktaları okur (120Hz/240Hz stylus örneklemesi).
 * 2. `androidx.input.motionprediction.MotionEventPredictor` ile gelecek kareyi tahmin eder (Prediction).
 * 3. Aktif çizgiyi `androidx.ink` (`InProgressStroke` + `StockBrushes` + `CanvasStrokeRenderer`) ile
 *    gerçek zamanlı oluşturur.
 */
class LowLatencyInkEngine(hostView: View) {

    private val predictor: MotionEventPredictor? = runCatching {
        MotionEventPredictor.newInstance(hostView)
    }.getOrNull()

    private val canvasStrokeRenderer: CanvasStrokeRenderer? = runCatching {
        CanvasStrokeRenderer.create()
    }.getOrNull()

    private val inProgressStroke: InProgressStroke? = runCatching {
        InProgressStroke()
    }.getOrNull()

    private val strokeTransformMatrix = Matrix()
    private var strokeStartTimeMillis: Long = 0L
    private var hasEnqueuedInputs: Boolean = false

    /**
     * Yeni bir aktif çizgi (wet stroke) başlatır.
     */
    fun startStroke(
        tool: DrawingToolType,
        colorArgb: Int,
        thickness: Float,
        startTimeMillis: Long
    ) {
        strokeStartTimeMillis = startTimeMillis
        hasEnqueuedInputs = false
        val activeStroke = inProgressStroke ?: return
        runCatching {
            val family = when (tool) {
                DrawingToolType.HIGHLIGHTER -> StockBrushes.highlighterLatest
                else -> StockBrushes.pressurePenLatest
            }
            val effectiveColor = if (tool == DrawingToolType.HIGHLIGHTER) {
                // Fosforlu kalem için yarı saydam renk
                (colorArgb and 0x00FFFFFF) or (0x66 shl 24)
            } else {
                colorArgb
            }
            val effectiveSize = if (tool == DrawingToolType.HIGHLIGHTER) {
                thickness * 2.4f
            } else {
                thickness
            }
            val brush = Brush.createWithColorIntArgb(
                family = family,
                colorIntArgb = effectiveColor,
                size = effectiveSize.coerceAtLeast(1f),
                epsilon = 0.1f
            )
            activeStroke.start(brush)
        }
    }

    /**
     * `MotionEvent` içinden hem tarihsel (historical) gerçek noktaları hem de
     * `MotionEventPredictor` tarafından tahmin edilen (predicted) noktaları belge uzayına çevirir.
     */
    fun extractSamplesAndPredict(
        event: MotionEvent,
        pointerIndex: Int,
        screenToDocX: (Float) -> Float,
        screenToDocY: (Float) -> Float
    ): Pair<List<StrokePoint>, List<StrokePoint>> {
        predictor?.record(event)

        val realPoints = extractPointsFromEvent(
            event = event,
            pointerIndex = pointerIndex,
            screenToDocX = screenToDocX,
            screenToDocY = screenToDocY
        )

        val predictedPoints = ArrayList<StrokePoint>()
        val predictedEvent = predictor?.predict()
        if (predictedEvent != null) {
            try {
                val predIdx = 0
                if (predictedEvent.pointerCount > 0) {
                    predictedPoints.addAll(
                        extractPointsFromEvent(
                            event = predictedEvent,
                            pointerIndex = predIdx,
                            screenToDocX = screenToDocX,
                            screenToDocY = screenToDocY
                        )
                    )
                }
            } finally {
                predictedEvent.recycle()
            }
        }

        // Jetpack Ink InProgressStroke beslemesi
        enqueueToJetpackInk(
            toolType = mapToolType(event.getToolType(pointerIndex)),
            realPoints = realPoints,
            predictedPoints = predictedPoints
        )

        return realPoints to predictedPoints
    }

    fun finishActiveStroke() {
        hasEnqueuedInputs = false
        runCatching {
            inProgressStroke?.finishInput()
            inProgressStroke?.clear()
        }
    }

    /**
     * Aktif çizilmekte olan çizgiyi Jetpack Ink `CanvasStrokeRenderer` ile çizer.
     * Eğer başarılıysa `true` döner; aksi halde yedek vektör çizici devreye girer.
     */
    fun drawActiveWetStrokeWithJetpackInk(
        canvas: Canvas,
        zoom: Float,
        panX: Float,
        panY: Float
    ): Boolean {
        val renderer = canvasStrokeRenderer ?: return false
        val stroke = inProgressStroke ?: return false
        return runCatching {
            if (!hasEnqueuedInputs) return false
            strokeTransformMatrix.reset()
            strokeTransformMatrix.postScale(zoom, zoom)
            strokeTransformMatrix.postTranslate(panX, panY)
            renderer.draw(canvas, stroke, strokeTransformMatrix)
            true
        }.getOrDefault(false)
    }

    private fun enqueueToJetpackInk(
        toolType: InputToolType,
        realPoints: List<StrokePoint>,
        predictedPoints: List<StrokePoint>
    ) {
        val stroke = inProgressStroke ?: return
        runCatching {
            val realBatch = MutableStrokeInputBatch()
            for (pt in realPoints) {
                val elapsed = (pt.timestampMillis - strokeStartTimeMillis).coerceAtLeast(0L)
                realBatch.addOrIgnore(
                    type = toolType,
                    x = pt.x,
                    y = pt.y,
                    elapsedTimeMillis = elapsed,
                    pressure = pt.pressure.coerceIn(0.05f, 1.0f),
                    tiltRadians = pt.tiltRadians,
                    orientationRadians = pt.orientationRadians
                )
            }
            val predictedBatch = MutableStrokeInputBatch()
            for (pt in predictedPoints) {
                val elapsed = (pt.timestampMillis - strokeStartTimeMillis).coerceAtLeast(0L)
                predictedBatch.addOrIgnore(
                    type = toolType,
                    x = pt.x,
                    y = pt.y,
                    elapsedTimeMillis = elapsed,
                    pressure = pt.pressure.coerceIn(0.05f, 1.0f),
                    tiltRadians = pt.tiltRadians,
                    orientationRadians = pt.orientationRadians
                )
            }
            stroke.enqueueInputsOrThrow(realBatch, predictedBatch)
            val latestElapsed = ((realPoints.lastOrNull()?.timestampMillis ?: strokeStartTimeMillis) - strokeStartTimeMillis)
                .coerceAtLeast(0L)
            stroke.updateShapeOrThrow(latestElapsed)
            hasEnqueuedInputs = true
        }
    }

    private fun extractPointsFromEvent(
        event: MotionEvent,
        pointerIndex: Int,
        screenToDocX: (Float) -> Float,
        screenToDocY: (Float) -> Float
    ): List<StrokePoint> {
        val historySize = event.historySize
        val result = ArrayList<StrokePoint>(historySize + 1)

        // 1. Önce tüm tarihsel (coalesced) donanım örneklerini sırayla oku
        for (h in 0 until historySize) {
            val hx = screenToDocX(event.getHistoricalX(pointerIndex, h))
            val hy = screenToDocY(event.getHistoricalY(pointerIndex, h))
            val hp = event.getHistoricalPressure(pointerIndex, h).coerceIn(0.05f, 1.0f)
            val htilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, h)
            val horient = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, h)
            val htime = event.getHistoricalEventTime(h)
            result.add(
                StrokePoint(
                    x = hx,
                    y = hy,
                    pressure = hp,
                    tiltRadians = htilt,
                    orientationRadians = horient,
                    timestampMillis = htime
                )
            )
        }

        // 2. Güncel (anlık) örneği ekle
        val cx = screenToDocX(event.getX(pointerIndex))
        val cy = screenToDocY(event.getY(pointerIndex))
        val cp = event.getPressure(pointerIndex).coerceIn(0.05f, 1.0f)
        val ctilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
        val corient = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
        result.add(
            StrokePoint(
                x = cx,
                y = cy,
                pressure = cp,
                tiltRadians = ctilt,
                orientationRadians = corient,
                timestampMillis = event.eventTime
            )
        )

        return result
    }

    private fun mapToolType(androidToolType: Int): InputToolType = when (androidToolType) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InputToolType.STYLUS
        MotionEvent.TOOL_TYPE_MOUSE -> InputToolType.MOUSE
        else -> InputToolType.TOUCH
    }
}
