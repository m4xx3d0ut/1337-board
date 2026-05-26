package org.leetboard.ime.engine

data class GlidePoint(
    val x: Float,
    val y: Float,
)

data class GlideTouchTrace(
    val points: List<GlidePoint>,
    val keyCenters: Map<Char, GlidePoint>,
) {
    fun isUsable(): Boolean = points.size >= MIN_TRACE_POINTS && keyCenters.isNotEmpty()

    companion object {
        private const val MIN_TRACE_POINTS = 2
    }
}
