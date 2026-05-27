package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.ModifierState

class TextCapitalizationTest {
    @Test
    fun detectsAutoCapOnlyAfterPeriod() {
        assertTrue(shouldAutoCapAfterPeriod("hello. "))
        assertFalse(shouldAutoCapAfterPeriod("hello "))
        assertFalse(shouldAutoCapAfterPeriod(null))
    }

    @Test
    fun appliesAutoCapAfterPeriodWhenEnabled() {
        val output = "test".applyKeyboardCapitalization(
            modifiers = ModifierState(),
            heldModifiers = HeldModifiers(),
            autoCapAfterPeriod = true,
            textBeforeCursor = "hello. ",
            allCapsOnShift = false,
        )

        assertEquals("Test", output)
    }

    @Test
    fun canDisableAutoCapAfterPeriod() {
        val output = "test".applyKeyboardCapitalization(
            modifiers = ModifierState(),
            heldModifiers = HeldModifiers(),
            autoCapAfterPeriod = false,
            textBeforeCursor = "hello. ",
            allCapsOnShift = false,
        )

        assertEquals("test", output)
    }

    @Test
    fun heldShiftCapitalizesFirstGlideLetterOnly() {
        val output = "test".applyKeyboardCapitalization(
            modifiers = ModifierState(),
            heldModifiers = HeldModifiers(shift = true),
            autoCapAfterPeriod = false,
            textBeforeCursor = null,
            allCapsOnShift = false,
        )

        assertEquals("Test", output)
    }

    @Test
    fun heldShiftUsesSymbolAlternateForNumberKeys() {
        assertEquals("!", shiftAlternateText("1", ModifierState(), HeldModifiers(shift = true)))
        assertEquals("?", shiftAlternateText("/", ModifierState(shift = true), HeldModifiers()))
    }

    @Test
    fun shiftLockDoesNotUseSymbolAlternatesForNumberKeys() {
        assertEquals(null, shiftAlternateText("1", ModifierState(shift = true, shiftLocked = true), HeldModifiers()))
    }
}
