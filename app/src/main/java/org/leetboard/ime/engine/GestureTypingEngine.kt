package org.leetboard.ime.engine

import android.graphics.PointF
import android.view.inputmethod.EditorInfo

class GestureTypingEngine(
    private val textContextPolicy: TextContextPolicy,
) {
    fun isEnabledFor(editorInfo: EditorInfo?): Boolean {
        return textContextPolicy.allowsGestureTyping(editorInfo)
    }

    fun decode(points: List<PointF>): String? {
        if (points.size < MIN_POINTS_FOR_GESTURE) return null
        return null
    }

    private companion object {
        const val MIN_POINTS_FOR_GESTURE = 8
    }
}

