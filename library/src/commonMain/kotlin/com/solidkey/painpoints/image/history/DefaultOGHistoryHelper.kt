package com.solidkey.painpoints.image.history

import com.solidkey.painpoints.image.processing.OGImageTransformation
import com.solidkey.painpoints.image.processing.safeCopy

class DefaultOGHistoryHelper(
    private val maxDepth: Int = 20
) : OGTransformationHistoryHelper {

    private val undoStack = ArrayDeque<List<OGImageTransformation>>()
    private val redoStack = ArrayDeque<List<OGImageTransformation>>()

    override fun pushSnapshot(current: List<OGImageTransformation>) {
        if (undoStack.size >= maxDepth) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current.map { it.safeCopy() }) // snapshot copy
        redoStack.clear()
    }

    override fun undo(): List<OGImageTransformation>? {
        if (undoStack.isEmpty()) return null
        val last = undoStack.removeLast()
        redoStack.addLast(last.map { it.safeCopy() })
        return last.map { it.safeCopy() }
    }

    override fun redo(): List<OGImageTransformation>? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(next.map { it.safeCopy() })
        return next.map { it.safeCopy() }
    }

    override fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    override fun peekLatest(): List<OGImageTransformation>? {
        return undoStack.lastOrNull()?.map { it.safeCopy() }
    }

    override fun canUndo(): Boolean = undoStack.size > 1
    override fun canRedo(): Boolean = redoStack.isNotEmpty()
}
