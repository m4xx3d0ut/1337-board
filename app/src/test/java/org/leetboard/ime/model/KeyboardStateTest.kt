package org.leetboard.ime.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStateTest {
    @Test
    fun shiftCyclesThroughStickyLockedAndOff() {
        val once = KeyboardState().toggleShift()
        val twice = once.toggleShift()
        val third = twice.toggleShift()

        assertTrue(once.modifiers.shift)
        assertFalse(once.modifiers.shiftLocked)
        assertTrue(twice.modifiers.shift)
        assertTrue(twice.modifiers.shiftLocked)
        assertFalse(third.modifiers.shift)
        assertFalse(third.modifiers.shiftLocked)
    }

    @Test
    fun activeKeyIdsReflectModifiersAndLayers() {
        val state = KeyboardState(
            symbols = true,
            numpad = true,
            modifiers = ModifierState(shift = true, ctrl = true, alt = true),
        )

        val active = state.activeKeyIds()

        assertTrue("shift" in active)
        assertTrue("ctrl" in active)
        assertTrue("alt" in active)
        assertTrue("symbols" in active)
        assertTrue("num_toggle" in active)
    }
}

