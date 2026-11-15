package com.tabletnotes.app.feature.editor.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin

/**
 * Samsung Notes tarzı çoklu kalem ucu (İnce Dolma Kalem, Kalın Dolma/Kaligrafi Kalemi,
 * Tükenmez Kalem, Kurşun Kalem, Marker Kalem, Sulu Boya Fırçası, Fosforlu Kalem) destekli
 * basınç ve eğim duyarlı vektör çizgi çizici.
 */
object StrokeRenderer {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val reusablePath = Path()

    fun drawStroke(canvas: Canvas, stroke: NoteStroke) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        configurePaintForTool(stroke.tool, stroke.colorArgb, stroke.thickness)

        if (pts.size == 1) {
            val p = pts[0]
            val radius = computePointWidth(
                tool = stroke.tool,
                baseThickness = stroke.thickness,
                pressure = p.pressure,
                tiltRadians = p.tiltRadians,
                directionAngle = 0f
            ) * 0.5f
            strokePaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, radius.coerceAtLeast(1f), strokePaint)
            strokePaint.style = Paint.Style.STROKE
            return
        }

        // Sulu boya fırçası: yumuşak dış katman + iç pigment katmanı
        if (stroke.tool == DrawingToolType.WATERCOLOR) {
            buildSmoothPath(pts, reusablePath)
            haloPaint.color = withAlpha(stroke.colorArgb, 0x28)
            haloPaint.strokeWidth = stroke.thickness * 2.9f
            canvas.drawPath(reusablePath, haloPaint)

            strokePaint.color = withAlpha(stroke.colorArgb, 0x4C)
            strokePaint.strokeWidth = stroke.thickness * 1.9f
            canvas.drawPath(reusablePath, strokePaint)
            return
        }

        // Fosforlu kalem veya Marker kalem için pürüzsüz tek Bezier Path
        if (
            stroke.tool == DrawingToolType.HIGHLIGHTER ||
            stroke.tool == DrawingToolType.MARKER ||
            (stroke.tool == DrawingToolType.PEN && !hasSignificantPressureVariation(pts))
        ) {
            val avgPressure = pts.sumOf { it.pressure.toDouble() }.toFloat() / pts.size
            val avgTilt = pts.sumOf { it.tiltRadians.toDouble() }.toFloat() / pts.size
            strokePaint.strokeWidth = computePointWidth(
                tool = stroke.tool,
                baseThickness = stroke.thickness,
                pressure = avgPressure,
                tiltRadians = avgTilt,
                directionAngle = 0f
            )
            buildSmoothPath(pts, reusablePath)
            canvas.drawPath(reusablePath, strokePaint)
            return
        }

        // Dolma Kalem, Kaligrafi, Kurşun Kalem ve değişken basınçlı çizgiler için segment çizimi
        var prevX = pts[0].x
        var prevY = pts[0].y
        for (i in 1 until pts.size) {
            val curr = pts[i]
            val midX = (prevX + curr.x) * 0.5f
            val midY = (prevY + curr.y) * 0.5f
            val dirAngle = atan2(curr.y - prevY, curr.x - prevX)

            if (stroke.tool == DrawingToolType.PENCIL) {
                val pNorm = curr.pressure.coerceIn(0.15f, 1.0f)
                val alpha = (110 + (pNorm * 115)).toInt().coerceIn(80, 230)
                strokePaint.color = withAlpha(stroke.colorArgb, alpha)
            }

            strokePaint.strokeWidth = computePointWidth(
                tool = stroke.tool,
                baseThickness = stroke.thickness,
                pressure = curr.pressure,
                tiltRadians = curr.tiltRadians,
                directionAngle = dirAngle
            )
            canvas.drawLine(prevX, prevY, midX, midY, strokePaint)
            prevX = midX
            prevY = midY
        }
        val last = pts.last()
        canvas.drawLine(prevX, prevY, last.x, last.y, strokePaint)
    }

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
                strokePaint.color = withAlpha(colorArgb, 0x66)
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.strokeWidth = baseThickness * 2.4f
            }
            DrawingToolType.CALLIGRAPHY -> {
                strokePaint.color = colorArgb
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.strokeWidth = baseThickness * 1.35f
            }
            DrawingToolType.MARKER -> {
                strokePaint.color = withAlpha(colorArgb, 0xEC)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = baseThickness * 1.55f
            }
            DrawingToolType.PENCIL -> {
                strokePaint.color = withAlpha(colorArgb, 0xC8)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = baseThickness * 0.95f
            }
            DrawingToolType.WATERCOLOR -> {
                strokePaint.color = withAlpha(colorArgb, 0x50)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeWidth = baseThickness * 2.0f
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
        tiltRadians: Float,
        directionAngle: Float
    ): Float {
        val p = pressure.coerceIn(0.05f, 1.0f)
        val tiltFactor = 1f + (abs(tiltRadians) * 0.25f).coerceAtMost(0.45f)
        return when (tool) {
            DrawingToolType.HIGHLIGHTER -> baseThickness * 2.4f * tiltFactor
            DrawingToolType.FINE_FOUNTAIN -> {
                // İnce dolma kalem: keskin ince uçlar + güçlü basınç duyarlılığı
                val factor = 0.28f + (p * p * 1.45f)
                max(0.8f, baseThickness * factor)
            }
            DrawingToolType.CALLIGRAPHY -> {
                // Kalın dolma / kaligrafi kalemi: 45 derece kesik uç açısı + basınç etkisi
                val nibAngleEffect = 0.45f + 0.95f * abs(sin(directionAngle - 0.785f))
                val pressureEffect = 0.55f + (p * 0.95f)
                max(1.2f, baseThickness * 1.35f * nibAngleEffect * pressureEffect)
            }
            DrawingToolType.PENCIL -> {
                // Kurşun kalem: eğimle genişleyen gölgeleme
                val pencilTilt = 1f + (abs(tiltRadians) * 0.6f).coerceAtMost(0.9f)
                max(1.0f, baseThickness * (0.65f + p * 0.55f) * pencilTilt)
            }
            DrawingToolType.MARKER -> {
                max(2.0f, baseThickness * 1.55f * (0.88f + p * 0.24f))
            }
            DrawingToolType.WATERCOLOR -> {
                max(2.5f, baseThickness * 2.1f * (0.75f + p * 0.65f))
            }
            else -> {
                val factor = 0.45f + (p * 1.10f)
                max(1f, baseThickness * factor * tiltFactor)
            }
        }
    }

    private fun withAlpha(colorArgb: Int, alpha: Int): Int {
        return (colorArgb and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
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
