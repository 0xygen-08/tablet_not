package com.tabletnotes.app.feature.editor

import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.editor.history.EditorCommand
import com.tabletnotes.app.feature.editor.history.UndoRedoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoRedoManagerTest {

    @Test
    fun `undo and redo stacks transition accurately and clear redo on new record`() {
        val manager = UndoRedoManager()
        val stroke1 = NoteStroke(
            id = "1",
            tool = DrawingToolType.PEN,
            colorArgb = 0xFFFFFFFF.toInt(),
            thickness = 3f,
            points = listOf(StrokePoint(10f, 10f), StrokePoint(20f, 20f))
        )
        val stroke2 = stroke1.copy(id = "2")

        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)

        manager.record(EditorCommand.AddStroke(stroke1))
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)

        val popped = manager.popUndo()
        assertEquals(EditorCommand.AddStroke(stroke1), popped)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)

        // Yeni bir eylem kaydedildiğinde redo yığını sıfırlanmalıdır
        manager.record(EditorCommand.AddStroke(stroke2))
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
    }
}
