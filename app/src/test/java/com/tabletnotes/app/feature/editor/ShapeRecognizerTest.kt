package com.tabletnotes.app.feature.editor

import com.tabletnotes.app.domain.model.RecognizedShapeType
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.editor.shape.ShapeRecognizer
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeRecognizerTest {

    @Test
    fun `recognizes straight line from slightly noisy linear points`() {
        val pts = (0..20).map { i ->
            val t = i / 20f
            StrokePoint(
                x = 100f + t * 300f,
                y = 200f + sin(t * 6f) * 2.5f
            )
        }
        val result = ShapeRecognizer.recognize(pts)
        assertNotNull(result)
        assertEquals(RecognizedShapeType.LINE, result!!.shapeType)
    }

    @Test
    fun `recognizes circle from rough circular stroke`() {
        val cx = 300f
        val cy = 300f
        val r = 90f
        val pts = (0..36).map { i ->
            val theta = (i / 36.0) * Math.PI * 2.0
            StrokePoint(
                x = cx + (cos(theta) * r).toFloat(),
                y = cy + (sin(theta) * r).toFloat()
            )
        }
        val result = ShapeRecognizer.recognize(pts)
        assertNotNull(result)
        assertEquals(RecognizedShapeType.CIRCLE, result!!.shapeType)
    }

    @Test
    fun `recognizes triangle from 3-edge closed path`() {
        val a = StrokePoint(200f, 100f)
        val b = StrokePoint(340f, 320f)
        val c = StrokePoint(80f, 320f)
        val pts = sampleEdges(listOf(a, b, c, a))
        val result = ShapeRecognizer.recognize(pts)
        assertNotNull(result)
        assertEquals(RecognizedShapeType.TRIANGLE, result!!.shapeType)
    }

    @Test
    fun `recognizes square from equal-sided 4-corner stroke`() {
        val corners = listOf(
            StrokePoint(100f, 100f),
            StrokePoint(260f, 100f),
            StrokePoint(260f, 260f),
            StrokePoint(100f, 260f),
            StrokePoint(100f, 100f)
        )
        val result = ShapeRecognizer.recognize(sampleEdges(corners))
        assertNotNull(result)
        assertEquals(RecognizedShapeType.SQUARE, result!!.shapeType)
    }

    @Test
    fun `recognizes trapezoid when top edge is narrower than bottom edge`() {
        val corners = listOf(
            StrokePoint(160f, 100f),
            StrokePoint(260f, 100f),
            StrokePoint(340f, 260f),
            StrokePoint(80f, 260f),
            StrokePoint(160f, 100f)
        )
        val result = ShapeRecognizer.recognize(sampleEdges(corners))
        assertNotNull(result)
        assertEquals(RecognizedShapeType.TRAPEZOID, result!!.shapeType)
        assertTrue(result.points.size >= 4)
    }

    private fun sampleEdges(vertices: List<StrokePoint>): List<StrokePoint> {
        val out = ArrayList<StrokePoint>()
        for (i in 0 until vertices.size - 1) {
            val p0 = vertices[i]
            val p1 = vertices[i + 1]
            for (step in 0 until 10) {
                val t = step / 10f
                out.add(StrokePoint(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t))
            }
        }
        out.add(vertices.last())
        return out
    }
}

