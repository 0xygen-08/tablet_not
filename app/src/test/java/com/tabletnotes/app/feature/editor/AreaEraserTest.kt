package com.tabletnotes.app.feature.editor

import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.editor.ui.NoteEditorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaEraserTest {

    @Test
    fun `area eraser splits stroke into two fragments leaving middle gap`() {
        val controller = NoteEditorController()
        val points = (0..10).map { i ->
            StrokePoint(x = 100f + i * 20f, y = 200f)
        }
        val stroke = NoteStroke(
            id = "s-line-1",
            pageIndex = 0,
            tool = DrawingToolType.PEN,
            colorArgb = 0xFF000000.toInt(),
            thickness = 4f,
            points = points
        )
        controller.commitStroke(stroke)
        assertEquals(1, controller.strokeCount)

        val beforeMap = LinkedHashMap<String, NoteStroke>()
        val createdIds = HashSet<String>()
        val changed = controller.eraseAreaAlongSegment(
            x0 = 200f,
            y0 = 190f,
            x1 = 200f,
            y1 = 210f,
            radius = 22f,
            beforeSnapshotMap = beforeMap,
            createdStrokeIds = createdIds
        )

        assertTrue(changed)
        assertEquals(2, controller.strokeCount)
        controller.recordAreaEraseBatch(beforeMap.values.toList(), createdIds)

        // Geri Al (Undo) orijinal tek parça çizgiyi geri getirmelidir
        controller.undo()
        assertEquals(1, controller.strokeCount)
        assertEquals("s-line-1", controller.spatialIndex.getAllStrokes().first().id)
    }
}

