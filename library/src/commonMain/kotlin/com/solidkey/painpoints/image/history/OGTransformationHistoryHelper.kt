package com.solidkey.painpoints.image.history

import com.solidkey.painpoints.image.processing.OGImageTransformation

interface OGTransformationHistoryHelper {
    fun pushSnapshot(current: List<OGImageTransformation>)
    fun undo(): List<OGImageTransformation>?
    fun redo(): List<OGImageTransformation>?
    fun clear()
    fun peekLatest(): List<OGImageTransformation>?

    fun canUndo(): Boolean
    fun canRedo(): Boolean
}
