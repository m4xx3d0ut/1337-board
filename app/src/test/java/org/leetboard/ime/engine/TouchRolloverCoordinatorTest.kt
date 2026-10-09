package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchRolloverCoordinatorTest {
    @Test
    fun waitsForLowerActiveTapBeforeDispatchingHigherRelease() {
        val coordinator = TouchRolloverCoordinator<String>(reorderWindowMs = 45)

        val firstFlush = coordinator.enqueue(
            RolloverTap(downOrder = 2, releaseTimeMs = 100, value = "b"),
            activeDownOrders = setOf(1),
            nowMs = 110,
        )
        val secondFlush = coordinator.enqueue(
            RolloverTap(downOrder = 1, releaseTimeMs = 115, value = "a"),
            activeDownOrders = emptySet(),
            nowMs = 115,
        )

        assertTrue(firstFlush.isEmpty())
        assertEquals(listOf("a", "b"), secondFlush.map { it.value })
    }

    @Test
    fun releasesBlockedTapAfterReorderWindow() {
        val coordinator = TouchRolloverCoordinator<String>(reorderWindowMs = 45)

        coordinator.enqueue(
            RolloverTap(downOrder = 2, releaseTimeMs = 100, value = "b"),
            activeDownOrders = setOf(1),
            nowMs = 110,
        )
        val expired = coordinator.flush(activeDownOrders = setOf(1), nowMs = 146)

        assertEquals(listOf("b"), expired.map { it.value })
    }

    @Test
    fun preservesTenPointerDownOrderWhenReleasedInReverse() {
        val coordinator = TouchRolloverCoordinator<Int>(reorderWindowMs = 45)
        val dispatched = mutableListOf<Int>()

        (10 downTo 1).forEach { order ->
            dispatched += coordinator.enqueue(
                RolloverTap(downOrder = order.toLong(), releaseTimeMs = 100L + order, value = order),
                activeDownOrders = (1 until order).map(Int::toLong).toSet(),
                nowMs = 110L,
            ).map { tap -> tap.value }
        }
        dispatched += coordinator.flush(emptySet(), nowMs = 120L).map { tap -> tap.value }

        assertEquals((1..10).toList(), dispatched)
    }

    @Test
    fun clearDropsPendingTapsAfterCanceledGesture() {
        val coordinator = TouchRolloverCoordinator<String>(reorderWindowMs = 45)
        coordinator.enqueue(
            RolloverTap(downOrder = 2, releaseTimeMs = 100, value = "b"),
            activeDownOrders = setOf(1),
            nowMs = 110,
        )

        coordinator.clear()

        assertTrue(coordinator.flush(emptySet(), nowMs = 200).isEmpty())
    }
}
