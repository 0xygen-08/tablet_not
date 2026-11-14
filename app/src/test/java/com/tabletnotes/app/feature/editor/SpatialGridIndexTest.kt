package com.tabletnotes.app.feature.editor

import com.tabletnotes.app.domain.model.BoundingBox
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.editor.spatial.SpatialGridIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialGridIndexTest {

    @Test
    fun `viewport culling returns only strokes inside visible viewport among 10000 strokes`() {
        val index = SpatialGridIndex(cellSize = 128f)
        for (i in 0 until 10_000) {
            val x = (i % 100) * 50f
            val y = (i / 100) * 50f
            val stroke = NoteStroke(
                id = "s_$i",
                tool = DrawingToolType.PEN,
                colorArgb = 0xFFEF5350.toInt(),
                thickness = 3f,
                points = listOf(
                    StrokePoint(x, y),
                    StrokePoint(x + 10f, y + 10f)
                )
            )
            index.insert(stroke)
        }

        assertEquals(10_000, index.size)

        val viewport = BoundingBox(minX = 0f, minY = 0f, maxX = 120f, maxY = 120f)
        val visible = index.queryViewport(viewport)

        // (0,0), (50,0), (100,0), (0,50), (50,50), (100,50), (0,100), (50,100), (100,100) -> 9 çizgi
        assertEquals(9, visible.size)
        assertTrue(visible.all { it.bounds.intersects(viewport) })
    }

    @Test
    fun `stroke eraser segment hit test detects crossed strokes accurately`() {
        val index = SpatialGridIndex(cellSize = 128f)
        val targetStroke = NoteStroke(
            id = "target",
            tool = DrawingToolType.PEN,
            colorArgb = 0xFF60A5FA.toInt(),
            thickness = 4f,
            points = listOf(
                StrokePoint(100f, 100f),
                StrokePoint(200f, 100f)
            )
        )
        val distantStroke = NoteStroke(
            id = "distant",
            tool = DrawingToolType.PEN,
            colorArgb = 0xFF60A5FA.toInt(),
            thickness = 4f,
            points = listOf(
                StrokePoint(500f, 500f),
                StrokePoint(600f, 500f)
            )
        )
        index.insert(targetStroke)
        index.insert(distantStroke)

        val hits = index.findStrokesHitBySegment(
            x0 = 150f,
            y0 = 80f,
            x1 = 150f,
            y1 = 120f,
            eraserRadius = 10f
        )

        assertEquals(1, hits.size)
        assertEquals("target", hits.first().id)
    }
}
