package org.leetboard.ime.engine

data class GlidePoint(
    val x: Float,
    val y: Float,
    val timeMs: Long? = null,
)

data class GlideTouchTrace(
    val points: List<GlidePoint>,
    val keyCenters: Map<Char, GlidePoint>,
    val keyDwellWeights: Map<Char, Float> = emptyMap(),
) {
    fun isUsable(): Boolean = points.size >= MIN_TRACE_POINTS && keyCenters.isNotEmpty()

    companion object {
        private const val MIN_TRACE_POINTS = 2
    }
}
