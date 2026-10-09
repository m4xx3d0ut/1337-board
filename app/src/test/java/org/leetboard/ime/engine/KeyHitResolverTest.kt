package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHitResolverTest {
    private val resolver = KeyHitResolver(
        listOf(
            TouchKeyBounds("q", 0, 10f, 10f, 50f, 50f),
            TouchKeyBounds("w", 0, 56f, 10f, 96f, 50f),
            TouchKeyBounds("a", 1, 14f, 56f, 54f, 96f),
            TouchKeyBounds("s", 1, 60f, 56f, 100f, 96f),
        ),
    )

    @Test
    fun reportsExactVisualHits() {
        assertEquals(TouchKeyResolution("q", exactVisualHit = true), resolver.resolve(30f, 30f))
    }

    @Test
    fun dividesHorizontalGapBetweenAdjacentKeys() {
        assertEquals("q", resolver.resolve(52f, 30f)?.id)
        assertEquals("w", resolver.resolve(54f, 30f)?.id)
        assertFalse(resolver.resolve(52f, 30f)?.exactVisualHit ?: true)
    }

    @Test
    fun dividesRowGapWithoutChangingOuterMargins() {
        assertEquals("q", resolver.resolve(30f, 52f)?.id)
        assertEquals("a", resolver.resolve(30f, 54f)?.id)
        assertNull(resolver.resolve(5f, 30f))
        assertNull(resolver.resolve(30f, 5f))
    }

    @Test
    fun capturedKeyOwnsItsExpandedGapRegion() {
        assertTrue(resolver.contains("q", 52f, 30f))
        assertFalse(resolver.contains("w", 52f, 30f))
    }
}
