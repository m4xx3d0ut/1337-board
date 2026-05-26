package org.leetboard.ime.engine

import kotlin.math.hypot
import kotlin.math.roundToInt

object GlideGeometryScorer {
    fun profile(trace: GlideTouchTrace?): GlideTraceProfile? {
        if (trace?.isUsable() != true) return null
        val sampledPoints = sampleTrace(trace.points)
        val cornerPoints = extractCornerPoints(sampledPoints)
        val dwellWeights = trace.keyDwellWeights.ifEmpty {
            keyDwellWeights(trace.points, trace.keyCenters)
        }
        return GlideTraceProfile(
            trace = trace,
            sampledPoints = sampledPoints,
            cornerPoints = cornerPoints,
            cornerKeys = cornerKeys(cornerPoints, trace.keyCenters),
            keyDwellWeights = dwellWeights,
        )
    }

    fun cost(wordSignature: String, pathSignature: String): Int {
        val wordPoints = wordSignature.mapNotNull { keyPositions[it] }
        val pathPoints = pathSignature.mapNotNull { keyPositions[it] }
        if (wordPoints.isEmpty() || pathPoints.isEmpty()) return 0

        return dynamicTimeWarpingCost(wordPoints, pathPoints)
    }

    fun touchCost(wordSignature: String, trace: GlideTouchTrace?): Int {
        return touchCost(wordSignature, profile(trace))
    }

    fun touchCost(wordSignature: String, profile: GlideTraceProfile?): Int {
        profile ?: return 0
        val wordPoints = wordSignature.mapNotNull { profile.trace.keyCenters[it] }
        if (wordPoints.isEmpty()) return 0
        return dynamicTimeWarpingCost(wordPoints, profile.sampledPoints)
    }

    fun dwellCost(wordSignature: String, trace: GlideTouchTrace?): Int {
        return dwellCost(wordSignature, profile(trace))
    }

    fun dwellCost(wordSignature: String, profile: GlideTraceProfile?): Int {
        if (profile == null || profile.keyDwellWeights.isEmpty()) return 0
        val wordLetters = wordSignature.toSet()
        val strongDwellKeys = profile.keyDwellWeights.entries
            .asSequence()
            .filter { (_, weight) -> weight >= STRONG_DWELL_THRESHOLD }
            .sortedByDescending { (_, weight) -> weight }
            .take(MAX_DWELL_KEYS)
            .toList()
        if (strongDwellKeys.isEmpty()) return 0
        val cost = strongDwellKeys.sumOf { (key, weight) ->
            if (key in wordLetters) {
                -(weight * PRESENT_DWELL_BOOST).toDouble()
            } else {
                (weight * MISSING_DWELL_PENALTY).toDouble()
            }
        }
        return cost.roundToInt()
    }

    fun cornerCost(wordSignature: String, profile: GlideTraceProfile?): Int {
        profile ?: return 0
        if (profile.cornerPoints.size < MIN_CORNER_POINTS_FOR_COST) return 0
        val wordPoints = wordSignature.mapNotNull { profile.trace.keyCenters[it] }
        if (wordPoints.isEmpty()) return 0
        return dynamicTimeWarpingCost(wordPoints, profile.cornerPoints)
    }

    fun anchorCost(wordSignature: String, profile: GlideTraceProfile?): Int {
        profile ?: return 0
        val first = wordSignature.firstOrNull()?.let { profile.trace.keyCenters[it] } ?: return 0
        val last = wordSignature.lastOrNull()?.let { profile.trace.keyCenters[it] } ?: return 0
        val start = profile.sampledPoints.firstOrNull() ?: return 0
        val end = profile.sampledPoints.lastOrNull() ?: return 0
        return ((first.distanceTo(start) + last.distanceTo(end)) * ANCHOR_COST_SCALE).roundToInt()
    }

    fun cornerKeyCost(wordSignature: String, profile: GlideTraceProfile?): Int {
        profile ?: return 0
        if (profile.cornerKeys.isEmpty()) return 0
        val wordLetters = wordSignature.toSet()
        val first = wordSignature.firstOrNull()
        val last = wordSignature.lastOrNull()
        val cost = profile.cornerKeys.entries.sumOf { (key, strength) ->
            if (key == first || key == last) {
                0.0
            } else if (key in wordLetters) {
                -(strength * PRESENT_CORNER_KEY_BOOST).toDouble()
            } else {
                (strength * MISSING_CORNER_KEY_PENALTY).toDouble()
            }
        }
        return cost.roundToInt()
    }

    fun keyDwellWeights(
        tracePoints: List<GlidePoint>,
        keyCenters: Map<Char, GlidePoint>,
    ): Map<Char, Float> {
        if (tracePoints.isEmpty() || keyCenters.isEmpty()) return emptyMap()
        val counts = mutableMapOf<Char, Float>()
        tracePoints.forEachIndexed { index, point ->
            val nearest = keyCenters.minByOrNull { (_, center) ->
                val dx = point.x - center.x
                val dy = point.y - center.y
                dx * dx + dy * dy
            } ?: return@forEachIndexed
            val distance = point.distanceTo(nearest.value)
            if (distance <= DWELL_KEY_RADIUS) {
                val elapsedWeight = point.elapsedWeight(tracePoints, index)
                val proximityWeight = (1f - distance / DWELL_KEY_RADIUS).coerceIn(0.2f, 1f)
                counts[nearest.key] = counts.getOrDefault(nearest.key, 0f) + proximityWeight * elapsedWeight
            }
        }
        val maxCount = counts.values.maxOrNull()?.takeIf { it > 0f } ?: return emptyMap()
        return counts.mapValues { (_, count) -> count / maxCount }
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

    private fun extractCornerPoints(points: List<GlidePoint>): List<GlidePoint> {
        if (points.size <= 2) return points
        val candidates = points
            .drop(1)
            .dropLast(1)
            .mapIndexedNotNull { shiftedIndex, point ->
                val index = shiftedIndex + 1
                val previous = points[index - 1]
                val next = points[index + 1]
                val strength = turnStrength(previous, point, next)
                if (strength >= CORNER_TURN_THRESHOLD) CornerCandidate(index, point, strength) else null
            }
            .filterWithMinimumSpacing()
            .sortedByDescending { candidate -> candidate.strength }
            .take(MAX_INTERIOR_CORNERS)
            .sortedBy { candidate -> candidate.index }
            .map { candidate -> candidate.point }
        return buildList {
            add(points.first())
            addAll(candidates)
            add(points.last())
        }
    }

    private fun List<CornerCandidate>.filterWithMinimumSpacing(): List<CornerCandidate> {
        val selected = mutableListOf<CornerCandidate>()
        sortedByDescending { candidate -> candidate.strength }.forEach { candidate ->
            val farEnough = selected.all { existing ->
                candidate.point.distanceTo(existing.point) >= MIN_CORNER_SPACING
            }
            if (farEnough) selected += candidate
        }
        return selected.sortedBy { candidate -> candidate.index }
    }

    private fun turnStrength(
        previous: GlidePoint,
        current: GlidePoint,
        next: GlidePoint,
    ): Float {
        val ax = current.x - previous.x
        val ay = current.y - previous.y
        val bx = next.x - current.x
        val by = next.y - current.y
        val aLength = hypot(ax, ay)
        val bLength = hypot(bx, by)
        if (aLength < MIN_TURN_SEGMENT || bLength < MIN_TURN_SEGMENT) return 0f
        val cosine = ((ax * bx + ay * by) / (aLength * bLength)).coerceIn(-1f, 1f)
        return 1f - cosine
    }

    private fun cornerKeys(
        cornerPoints: List<GlidePoint>,
        keyCenters: Map<Char, GlidePoint>,
    ): Map<Char, Float> {
        if (cornerPoints.size < MIN_CORNER_POINTS_FOR_COST || keyCenters.isEmpty()) return emptyMap()
        return cornerPoints
            .drop(1)
            .dropLast(1)
            .mapNotNull { point ->
                val nearest = keyCenters.minByOrNull { (_, center) -> point.distanceTo(center) } ?: return@mapNotNull null
                val distance = point.distanceTo(nearest.value)
                if (distance > CORNER_KEY_RADIUS) return@mapNotNull null
                nearest.key to (1f - distance / CORNER_KEY_RADIUS).coerceIn(0.2f, 1f)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, strengths) -> strengths.maxOrNull() ?: 0f }
    }

    private fun GlidePoint.elapsedWeight(points: List<GlidePoint>, index: Int): Float {
        val previous = points.getOrNull(index - 1)?.timeMs
        val next = timeMs
        val elapsedMs = if (previous != null && next != null) {
            (next - previous).coerceIn(MIN_DWELL_DELTA_MS, MAX_DWELL_DELTA_MS).toFloat()
        } else {
            SAMPLE_DWELL_WEIGHT
        }
        return elapsedMs / SAMPLE_DWELL_WEIGHT
    }

    private fun GlidePoint.distanceTo(other: GlidePoint): Float {
        return hypot(x - other.x, y - other.y)
    }

    private data class CornerCandidate(
        val index: Int,
        val point: GlidePoint,
        val strength: Float,
    )

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
    private const val ANCHOR_COST_SCALE = 1000
    private const val MAX_TRACE_POINTS = 32
    private const val MAX_INTERIOR_CORNERS = 6
    private const val MIN_CORNER_POINTS_FOR_COST = 3
    private const val CORNER_TURN_THRESHOLD = 0.34f
    private const val MIN_TURN_SEGMENT = 0.18f
    private const val MIN_CORNER_SPACING = 0.55f
    private const val CORNER_KEY_RADIUS = 0.62f
    private const val PRESENT_CORNER_KEY_BOOST = 500
    private const val MISSING_CORNER_KEY_PENALTY = 1400
    private const val DWELL_KEY_RADIUS = 0.72f
    private const val STRONG_DWELL_THRESHOLD = 0.7f
    private const val MAX_DWELL_KEYS = 6
    private const val PRESENT_DWELL_BOOST = 300
    private const val MISSING_DWELL_PENALTY = 3000
    private const val SAMPLE_DWELL_WEIGHT = 16f
    private const val MIN_DWELL_DELTA_MS = 1L
    private const val MAX_DWELL_DELTA_MS = 120L
}

data class GlideTraceProfile(
    val trace: GlideTouchTrace,
    val sampledPoints: List<GlidePoint>,
    val cornerPoints: List<GlidePoint>,
    val cornerKeys: Map<Char, Float>,
    val keyDwellWeights: Map<Char, Float>,
)
