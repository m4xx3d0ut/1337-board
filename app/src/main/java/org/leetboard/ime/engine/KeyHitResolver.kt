package org.leetboard.ime.engine

data class TouchKeyBounds(
    val id: String,
    val row: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }
}

data class TouchKeyResolution(
    val id: String,
    val exactVisualHit: Boolean,
)

/** Assigns visual gaps to the nearest adjacent key without consuming outer margins. */
class KeyHitResolver(targets: List<TouchKeyBounds>) {
    private val visualTargets = targets.toList()
    private val touchTargets = expandedTargets(targets)

    fun resolve(x: Float, y: Float): TouchKeyResolution? {
        visualTargets.firstOrNull { target -> target.contains(x, y) }?.let { target ->
            return TouchKeyResolution(target.id, exactVisualHit = true)
        }
        return touchTargets.firstOrNull { target -> target.contains(x, y) }?.let { target ->
            TouchKeyResolution(target.id, exactVisualHit = false)
        }
    }

    fun contains(id: String, x: Float, y: Float): Boolean {
        return touchTargets.firstOrNull { target -> target.id == id }?.contains(x, y) == true
    }

    private fun expandedTargets(targets: List<TouchKeyBounds>): List<TouchKeyBounds> {
        if (targets.isEmpty()) return emptyList()
        val rows = targets.groupBy { target -> target.row }.toSortedMap()
        val rowBounds = rows.mapValues { (_, rowTargets) ->
            RowBounds(
                top = rowTargets.minOf { target -> target.top },
                bottom = rowTargets.maxOf { target -> target.bottom },
            )
        }
        return rows.flatMap { (rowIndex, rowTargets) ->
            val sorted = rowTargets.sortedBy { target -> target.left }
            val previousRow = rowBounds[rowIndex - 1]
            val nextRow = rowBounds[rowIndex + 1]
            val rowTop = rowBounds.getValue(rowIndex).top
            val rowBottom = rowBounds.getValue(rowIndex).bottom
            val touchTop = previousRow?.let { previous -> (previous.bottom + rowTop) / 2f } ?: rowTop
            val touchBottom = nextRow?.let { next -> (rowBottom + next.top) / 2f } ?: rowBottom
            sorted.mapIndexed { index, target ->
                val previous = sorted.getOrNull(index - 1)
                val next = sorted.getOrNull(index + 1)
                target.copy(
                    left = previous?.let { key -> (key.right + target.left) / 2f } ?: target.left,
                    right = next?.let { key -> (target.right + key.left) / 2f } ?: target.right,
                    top = touchTop,
                    bottom = touchBottom,
                )
            }
        }
    }

    private data class RowBounds(
        val top: Float,
        val bottom: Float,
    )
}
