package org.leetboard.ime.engine

internal data class RolloverTap<T>(
    val downOrder: Long,
    val releaseTimeMs: Long,
    val value: T,
)

internal class TouchRolloverCoordinator<T>(
    private val reorderWindowMs: Long,
) {
    private val pending = mutableListOf<RolloverTap<T>>()

    fun enqueue(tap: RolloverTap<T>, activeDownOrders: Set<Long>, nowMs: Long): List<RolloverTap<T>> {
        pending += tap
        return flush(activeDownOrders, nowMs)
    }

    fun flush(activeDownOrders: Set<Long>, nowMs: Long): List<RolloverTap<T>> {
        if (pending.isEmpty()) return emptyList()
        pending.sortBy { tap -> tap.downOrder }
        val ready = mutableListOf<RolloverTap<T>>()
        while (pending.isNotEmpty()) {
            val next = pending.first()
            val blockedByLowerActiveTap = activeDownOrders.any { order -> order < next.downOrder }
            val expired = nowMs - next.releaseTimeMs >= reorderWindowMs
            if (blockedByLowerActiveTap && !expired) break
            ready += pending.removeAt(0)
        }
        return ready
    }

    fun clear() {
        pending.clear()
    }

    fun hasPending(): Boolean = pending.isNotEmpty()
}
