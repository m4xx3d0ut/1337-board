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

    @Test
    fun speechInsertionAddsSpaceAfterWords() {
        assertEquals(
            " hello",
            formatSpeechInsertionText("hello", "test", autoCapAfterSentence = true),
        )
    }

    @Test
    fun speechInsertionCapitalizesAfterSentenceEnd() {
        assertEquals(
            " Hello",
            formatSpeechInsertionText("hello", "test.", autoCapAfterSentence = true),
        )
    }

    @Test
    fun speechInsertionKeepsMidSentenceLowercaseAfterComma() {
        assertEquals(
            "hello",
            formatSpeechInsertionText("Hello", "test, ", autoCapAfterSentence = true),
        )
    }

    @Test
    fun speechInsertionDoesNotSpaceBeforePunctuation() {
        assertEquals(
            ",",
            formatSpeechInsertionText(",", "test", autoCapAfterSentence = true),
        )
    }

    @Test
    fun speechInsertionConvertsSpokenPunctuation() {
        assertEquals(
            "This is a test, this is only a test.",
            formatSpeechInsertionText(
                "this is a test comma this is only a test period",
                "",
                autoCapAfterSentence = true,
            ),
        )
    }

    @Test
    fun speechInsertionCapitalizesAfterSpokenSentenceCommand() {
        assertEquals(
            "This is one. This is two.",
            formatSpeechInsertionText(
                "this is one period this is two period",
                "",
                autoCapAfterSentence = true,
            ),
        )
    }

    @Test
    fun speechInsertionCapitalizesAtLineStart() {
        assertEquals(
            "Line start test.",
            formatSpeechInsertionText(
                "line start test period",
                "Previous text\n",
                autoCapAfterSentence = true,
            ),
        )
    }

    @Test
    fun speechCommandsCanCreateNewLines() {
        assertEquals(
            "First line.\nSecond line.",
            formatSpeechInsertionText(
                "first line period new line second line period",
                "",
                autoCapAfterSentence = true,
            ),
        )
    }
}
