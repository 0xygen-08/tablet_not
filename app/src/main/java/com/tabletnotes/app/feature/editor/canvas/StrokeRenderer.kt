package com.tabletnotes.app.feature.editor.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.max

/**
 * Basınç (pressure) ve eğim (tilt) duyarlı vektör çizgi çizici.
 * Hem Karo Önbelleği (BitmapTileCache) içine sabitlenmiş çizgileri ("dry ink")
 * hem de aktif çizilmekte olan çizginin ("wet ink" + tahmin edilen uç) yedek çizimini yapar.
 */
object StrokeRenderer {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val reusablePath = Path()

    /**
     * Tamamlanmış bir [NoteStroke] nesnesini verilen Android [Canvas] üzerine çizer.
     * Canvas'ın halihazırda belge koordinatlarına (veya ilgili karo ofsetine) dönüştürülmüş olduğu varsayılır.
     */
    fun drawStroke(canvas: Canvas, stroke: NoteStroke) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        configurePaintForTool(stroke.tool, stroke.colorArgb, stroke.thickness)

        if (pts.size == 1) {
            val p = pts[0]
            val radius = computePointWidth(stroke.tool, stroke.thickness, p.pressure, p.tiltRadians) * 0.5f
            strokePaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, radius.coerceAtLeast(1f), strokePaint)
            strokePaint.style = Paint.Style.STROKE
            return
        }

        // Fosforlu kalem veya sabit basınçlı kısa çizgiler için tek yumuşak Quadratic Bezier Path
        if (stroke.tool == DrawingToolType.HIGHLIGHTER || !hasSignificantPressureVariation(pts)) {
            val avgPressure = pts.sumOf { it.pressure.toDouble() }.toFloat() / pts.size
            val avgTilt = pts.sumOf { it.tiltRadians.toDouble() }.toFloat() / pts.size
            strokePaint.strokeWidth = computePointWidth(stroke.tool, stroke.thickness, avgPressure, avgTilt)
            buildSmoothPath(pts, reusablePath)
            canvas.drawPath(reusablePath, strokePaint)
            return
        }

        // Basınç ve eğim değişimi belirgin olan Kalem (PEN) çizgileri için değişken kalınlıklı segment çizimi
        var prevX = pts[0].x
        var prevY = pts[0].y
        for (i in 1 until pts.size) {
            val curr = pts[i]
            val midX = (prevX + curr.x) * 0.5f
            val midY = (prevY + curr.y) * 0.5f
            strokePaint.strokeWidth = computePointWidth(
                tool = stroke.tool,
                baseThickness = stroke.thickness,
                pressure = curr.pressure,
                tiltRadians = curr.tiltRadians
            )
            canvas.drawLine(prevX, prevY, midX, midY, strokePaint)
            prevX = midX
            prevY = midY
        }
        val last = pts.last()
        canvas.drawLine(prevX, prevY, last.x, last.y, strokePaint)
    }

    /**
     * Aktif çizim sırasında gerçek noktaları + `MotionEventPredictor` tarafından tahmin edilen
     * (predicted) noktaları düşük gecikmeyle çizer.
     */
    fun drawWetStrokePoints(
        canvas: Canvas,
        tool: DrawingToolType,
        colorArgb: Int,
        thickness: Float,
        realPoints: List<StrokePoint>,
        predictedPoints: List<StrokePoint>
    ) {
        if (realPoints.isEmpty()) return
        val combined = if (predictedPoints.isEmpty()) {
            realPoints
        } else {
            ArrayList<StrokePoint>(realPoints.size + predictedPoints.size).apply {
                addAll(realPoints)
                addAll(predictedPoints)
            }
        }
        val tempStroke = NoteStroke(
            id = "wet",
            tool = tool,
            colorArgb = colorArgb,
            thickness = thickness,
            points = combined
        )
        drawStroke(canvas, tempStroke)
    }

    private fun configurePaintForTool(
        tool: DrawingToolType,
        colorArgb: Int,
        baseThickness: Float
    ) {
        when (tool) {
            DrawingToolType.HIGHLIGHTER -> {
                // Fosforlu kalem: yarı saydam (%40 alfa) ve kesik/geniş uç
                strokePaint.color = (colorArgb and 0x00FFFFFF) or (0x66 shl 24)
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.strokeWidth = baseThickness * 2.4f
            }
            else -> {
                strokePaint.color = colorArgb
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = baseThickness
            }
        }
    }

    private fun computePointWidth(
        tool: DrawingToolType,
        baseThickness: Float,
        pressure: Float,
        tiltRadians: Float
    ): Float {
        if (tool == DrawingToolType.HIGHLIGHTER) {
            val tiltFactor = 1f + (abs(tiltRadians) * 0.35f).coerceAtMost(0.5f)
            return baseThickness * 2.4f * tiltFactor
        }
        // Kalem: basınç (0.05..1.0) kalınlığı 0.45x ile 1.55x arasında doğal şekilde modüle eder
        val normalizedPressure = pressure.coerceIn(0.05f, 1.0f)
        val pressureFactor = 0.45f + (normalizedPressure * 1.10f)
        val tiltFactor = 1f + (abs(tiltRadians) * 0.2f).coerceAtMost(0.35f)
        return max(1f, baseThickness * pressureFactor * tiltFactor)
    }

    private fun hasSignificantPressureVariation(points: List<StrokePoint>): Boolean {
        if (points.size < 3) return false
        var minP = 1f
        var maxP = 0f
        for (i in points.indices) {
            val p = points[i].pressure
            if (p < minP) minP = p
            if (p > maxP) maxP = p
            if (maxP - minP > 0.08f) return true
        }
        return false
    }

    private fun buildSmoothPath(points: List<StrokePoint>, outPath: Path) {
        outPath.reset()
        outPath.moveTo(points[0].x, points[0].y)
        if (points.size == 2) {
            outPath.lineTo(points[1].x, points[1].y)
            return
        }
        for (i in 1 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) * 0.5f
            val midY = (p0.y + p1.y) * 0.5f
            outPath.quadTo(p0.x, p0.y, midX, midY)
        }
        val last = points.last()
        outPath.lineTo(last.x, last.y)
    }
}
