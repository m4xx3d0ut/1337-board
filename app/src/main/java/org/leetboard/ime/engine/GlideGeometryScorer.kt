package org.leetboard.ime.engine

import kotlin.math.hypot
import kotlin.math.roundToInt

object GlideGeometryScorer {
    fun cost(wordSignature: String, pathSignature: String): Int {
        val wordPoints = wordSignature.mapNotNull { keyPositions[it] }
        val pathPoints = pathSignature.mapNotNull { keyPositions[it] }
        if (wordPoints.isEmpty() || pathPoints.isEmpty()) return 0

        val distances = Array(wordPoints.size) { FloatArray(pathPoints.size) { Float.POSITIVE_INFINITY } }
        wordPoints.forEachIndexed { wordIndex, wordPoint ->
            pathPoints.forEachIndexed { pathIndex, pathPoint ->
                val distance = wordPoint.distanceTo(pathPoint)
                val previous = if (wordIndex == 0 && pathIndex == 0) {
                    0f
                } else {
                    minOf(
                        distances.getOrNull(wordIndex - 1)?.getOrNull(pathIndex) ?: Float.POSITIVE_INFINITY,
                        distances.getOrNull(wordIndex)?.getOrNull(pathIndex - 1) ?: Float.POSITIVE_INFINITY,
                        distances.getOrNull(wordIndex - 1)?.getOrNull(pathIndex - 1) ?: Float.POSITIVE_INFINITY,
                    )
                }
                distances[wordIndex][pathIndex] = distance + previous
            }
        }

        val normalizedCost = distances.last().last() / maxOf(wordPoints.size, pathPoints.size)
        return (normalizedCost * GEOMETRY_COST_SCALE).roundToInt()
    }

    private fun KeyPoint.distanceTo(other: KeyPoint): Float {
        return hypot(x - other.x, y - other.y)
    }

    private data class KeyPoint(
        val x: Float,
        val y: Float,
    )

    private val keyPositions: Map<Char, KeyPoint> = buildMap {
        "qwertyuiop".forEachIndexed { index, char ->
            put(char, KeyPoint(index.toFloat(), 0f))
        }
        "asdfghjkl".forEachIndexed { index, char ->
            put(char, KeyPoint(index + 0.5f, 1f))
        }
        "zxcvbnm".forEachIndexed { index, char ->
            put(char, KeyPoint(index + 1.25f, 2f))
        }
    }

    private const val GEOMETRY_COST_SCALE = 1000
}
