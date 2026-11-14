package com.tabletnotes.app.feature.editor.history

import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.TextBlock

/**
 * Editörde geri alınabilir (Undo) ve ileri alınabilir (Redo) kullanıcı eylemleri.
 */
sealed interface EditorCommand {
    data class AddStroke(val stroke: NoteStroke) : EditorCommand
    data class BatchAddStrokes(val strokes: List<NoteStroke>) : EditorCommand
    data class EraseStrokes(val erasedStrokes: List<NoteStroke>) : EditorCommand
    data class UpsertTextBlock(val previous: TextBlock?, val updated: TextBlock?) : EditorCommand
}

/**
 * Undo / Redo yığınlarını (stack) yöneten sınıf.
 */
class UndoRedoManager(
    private val maxHistorySize: Int = 250
) {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()

    val canUndo: Boolean
        @Synchronized get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        @Synchronized get() = redoStack.isNotEmpty()

    @Synchronized
    fun record(command: EditorCommand) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(command)
        redoStack.clear()
    }

    @Synchronized
    fun popUndo(): EditorCommand? {
        if (undoStack.isEmpty()) return null
        val cmd = undoStack.removeLast()
        redoStack.addLast(cmd)
        return cmd
    }

    @Synchronized
    fun popRedo(): EditorCommand? {
        if (redoStack.isEmpty()) return null
        val cmd = redoStack.removeLast()
        undoStack.addLast(cmd)
        return cmd
    }

    @Synchronized
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
