package org.leetboard.ime.engine

data class TouchDiagnosticsSnapshot(
    val pointerDowns: Long,
    val exactHits: Long,
    val gapHits: Long,
    val downMisses: Long,
    val recoveredReleases: Long,
    val droppedReleases: Long,
    val dispatchedTaps: Long,
    val lastTapLatencyMs: Long,
    val maxTapLatencyMs: Long,
    val lastPredictionMs: Long,
    val maxPredictionMs: Long,
) {
    fun summary(): String {
        return buildString {
            appendLine("pointerDowns=$pointerDowns exactHits=$exactHits gapHits=$gapHits downMisses=$downMisses")
            appendLine("recoveredReleases=$recoveredReleases droppedReleases=$droppedReleases dispatchedTaps=$dispatchedTaps")
            append("tapLatencyMs(last/max)=$lastTapLatencyMs/$maxTapLatencyMs predictionMs(last/max)=$lastPredictionMs/$maxPredictionMs")
        }
    }
}

object TouchDiagnostics {
    private var pointerDowns = 0L
    private var exactHits = 0L
    private var gapHits = 0L
    private var downMisses = 0L
    private var recoveredReleases = 0L
    private var droppedReleases = 0L
    private var dispatchedTaps = 0L
    private var lastTapLatencyMs = 0L
    private var maxTapLatencyMs = 0L
    private var lastPredictionMs = 0L
    private var maxPredictionMs = 0L

    @Synchronized
    fun recordPointerDown(exactVisualHit: Boolean?) {
        pointerDowns += 1
        when (exactVisualHit) {
            true -> exactHits += 1
            false -> gapHits += 1
            null -> downMisses += 1
        }
    }

    @Synchronized
    fun recordRecoveredRelease() {
        recoveredReleases += 1
    }

    @Synchronized
    fun recordDroppedRelease() {
        droppedReleases += 1
    }

    @Synchronized
    fun recordTapDispatch(latencyMs: Long) {
        dispatchedTaps += 1
        lastTapLatencyMs = latencyMs.coerceAtLeast(0L)
        maxTapLatencyMs = maxOf(maxTapLatencyMs, lastTapLatencyMs)
    }

    @Synchronized
    fun recordPrediction(durationMs: Long) {
        lastPredictionMs = durationMs.coerceAtLeast(0L)
        maxPredictionMs = maxOf(maxPredictionMs, lastPredictionMs)
    }

    @Synchronized
    fun snapshot(): TouchDiagnosticsSnapshot {
        return TouchDiagnosticsSnapshot(
            pointerDowns = pointerDowns,
            exactHits = exactHits,
            gapHits = gapHits,
            downMisses = downMisses,
            recoveredReleases = recoveredReleases,
            droppedReleases = droppedReleases,
            dispatchedTaps = dispatchedTaps,
            lastTapLatencyMs = lastTapLatencyMs,
            maxTapLatencyMs = maxTapLatencyMs,
            lastPredictionMs = lastPredictionMs,
            maxPredictionMs = maxPredictionMs,
        )
    }

    @Synchronized
    fun reset() {
        pointerDowns = 0L
        exactHits = 0L
        gapHits = 0L
        downMisses = 0L
        recoveredReleases = 0L
        droppedReleases = 0L
        dispatchedTaps = 0L
        lastTapLatencyMs = 0L
        maxTapLatencyMs = 0L
        lastPredictionMs = 0L
        maxPredictionMs = 0L
    }
}
