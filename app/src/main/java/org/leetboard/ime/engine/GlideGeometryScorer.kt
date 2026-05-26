package org.leetboard.ime.engine

import kotlin.math.hypot
import kotlin.math.roundToInt

object GlideGeometryScorer {
    fun cost(wordSignature: String, pathSignature: String): Int {
        val wordPoints = wordSignature.mapNotNull { keyPositions[it] }
        val pathPoints = pathSignature.mapNotNull { keyPositions[it] }
        if (wordPoints.isEmpty() || pathPoints.isEmpty()) return 0

        return dynamicTimeWarpingCost(wordPoints, pathPoints)
    }

    fun touchCost(wordSignature: String, trace: GlideTouchTrace?): Int {
        if (trace?.isUsable() != true) return 0
        val wordPoints = wordSignature.mapNotNull { trace.keyCenters[it] }
        if (wordPoints.isEmpty()) return 0
        return dynamicTimeWarpingCost(wordPoints, sampleTrace(trace.points))
    }

    private fun dynamicTimeWarpingCost(
        wordPoints: List<GlidePoint>,
        pathPoints: List<GlidePoint>,
    ): Int {
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

    private fun sampleTrace(points: List<GlidePoint>): List<GlidePoint> {
        if (points.size <= MAX_TRACE_POINTS) return points
        val step = (points.lastIndex).toFloat() / (MAX_TRACE_POINTS - 1)
        return List(MAX_TRACE_POINTS) { index ->
            points[(index * step).roundToInt().coerceIn(points.indices)]
        }
    }

    private fun GlidePoint.distanceTo(other: GlidePoint): Float {
        return hypot(x - other.x, y - other.y)
    }

    private val keyPositions: Map<Char, GlidePoint> = buildMap {
        "qwertyuiop".forEachIndexed { index, char ->
            put(char, GlidePoint(index.toFloat(), 0f))
        }
        "asdfghjkl".forEachIndexed { index, char ->
            put(char, GlidePoint(index + 0.5f, 1f))
        }
        "zxcvbnm".forEachIndexed { index, char ->
            put(char, GlidePoint(index + 1.25f, 2f))
        }
    }

    private const val GEOMETRY_COST_SCALE = 1000
    private const val MAX_TRACE_POINTS = 32
}
