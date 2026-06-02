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
    fun speechInsertionTreatsDigitAfterDigitAsContinuation() {
        assertEquals(
            "45",
            formatSpeechInsertionText("45", "123", autoCapAfterSentence = true),
        )
        assertEquals(
            " test",
            formatSpeechInsertionText("test", "123", autoCapAfterSentence = true),
        )
    }

    @Test
    fun leadingSpaceHelperSpacesGlideWordsAfterDigitsAndPunctuation() {
        assertTrue(shouldInsertLeadingSpaceBeforeText("123", "test"))
        assertTrue(shouldInsertLeadingSpaceBeforeText("hello.", "Test"))
        assertFalse(shouldInsertLeadingSpaceBeforeText("123", "45", treatDigitAfterDigitAsContinuation = true))
        assertFalse(shouldInsertLeadingSpaceBeforeText("hello ", "test"))
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
    fun speechInsertionDeletesCursorSpaceBeforeSpokenPunctuation() {
        val insertion = formatSpeechInsertion(
            recognizedText = "comma",
            textBeforeCursor = "hello ",
            options = SpeechTextAutomationOptions(),
        )

        assertEquals(",", insertion.text)
        assertEquals(1, insertion.deleteBeforeChars)
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
    fun speechInsertionCanDisableSmartCleanup() {
        val insertion = formatSpeechInsertion(
            recognizedText = "hello period michael",
            textBeforeCursor = "test.",
            options = SpeechTextAutomationOptions(smartCleanupEnabled = false),
        )

        assertEquals("hello period michael", insertion.text)
        assertEquals(0, insertion.deleteBeforeChars)
    }

    @Test
    fun speechInsertionCanDisableSpokenPunctuationCommands() {
        assertEquals(
            "Hello period Michael",
            formatSpeechInsertion(
                recognizedText = "hello period michael",
                textBeforeCursor = "",
                options = SpeechTextAutomationOptions(spokenPunctuationCommandsEnabled = false),
            ).text,
        )
    }

    @Test
    fun speechInsertionCapitalizesBuiltInAndCustomNames() {
        assertEquals(
            " Michael met OnePlus and NASA",
            formatSpeechInsertion(
                recognizedText = "michael met oneplus and nasa",
                textBeforeCursor = "today,",
                options = SpeechTextAutomationOptions(customNames = setOf("OnePlus", "NASA")),
            ).text,
        )
    }

    @Test
    fun speechInsertionCapitalizesStandaloneI() {
        assertEquals(
            " I think I'm ready.",
            formatSpeechInsertion(
                recognizedText = "i think i'm ready period",
                textBeforeCursor = "okay.",
                options = SpeechTextAutomationOptions(),
            ).text,
        )
    }

    @Test
    fun speechInsertionCanDisablePronounAndNameCapitalization() {
        assertEquals(
            " i saw michael",
            formatSpeechInsertion(
                recognizedText = "i saw michael",
                textBeforeCursor = "today,",
                options = SpeechTextAutomationOptions(
                    autoCapSentencesEnabled = false,
                    autoCapNamesEnabled = false,
                ),
            ).text,
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
