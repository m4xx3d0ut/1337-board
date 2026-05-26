package org.leetboard.ime.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType

class KeyboardPreferencesTest {
    @Test
    fun customizationStateCombinesHiddenKeysAndNumpadToggle() {
        val preferences = KeyboardPreferences(
            hiddenOptionalKeyIds = setOf("esc"),
            numpadToggleEnabled = false,
            speechInputEnabled = false,
        )

        val customization = preferences.customizationState()

        assertTrue("esc" in customization.hiddenOptionalKeyIds)
        assertTrue("num_toggle" in customization.hiddenOptionalKeyIds)
    }

    @Test
    fun customizationStatePreservesSlotActions() {
        val action = KeyAction(KeyActionType.TAB)
        val preferences = KeyboardPreferences(slotActions = mapOf("esc" to action))

        assertEquals(action, preferences.customizationState().slotActions["esc"])
    }

    @Test
    fun defaultsUseFiveRowLayoutAndKeyPreview() {
        val preferences = KeyboardPreferences.defaults()

        assertEquals("qwerty5", preferences.layoutId)
        assertTrue(preferences.keyPreviewEnabled)
        assertTrue(preferences.stickyModifiersEnabled)
        assertTrue(preferences.shiftCapsLockEnabled)
        assertTrue(preferences.swipeUpActionsEnabled)
        assertTrue(preferences.autoCapAfterPeriodEnabled)
        assertFalse(preferences.glideCorrectionLearningEnabled)
        assertTrue(preferences.glideCorrections.isEmpty())
        assertFalse(preferences.glidePreferShorterWords)
        assertFalse(preferences.glideStrictFirstLastLetter)
        assertTrue(preferences.glidePredictiveRankingEnabled)
        assertEquals(GlidePathTolerance.BALANCED, preferences.glidePathTolerance)
        assertEquals(GlideImportedWordsPriority.NORMAL, preferences.glideImportedWordsPriority)
        assertEquals(GlideRawFallbackMode.OFF, preferences.glideRawFallbackMode)
        assertEquals(EscTouchMode.REGULAR, preferences.escTouchMode)
        assertEquals(0.75f, preferences.edgeKeyWidthScale, 0.001f)
        assertEquals(34f, preferences.portraitGeometry.keyboardHeightPercent, 0.001f)
        assertEquals(16f, preferences.portraitGeometry.horizontalMarginDp, 0.001f)
        assertEquals(12f, preferences.portraitGeometry.bottomMarginDp, 0.001f)
        assertEquals(48f, preferences.landscapeGeometry.keyboardHeightPercent, 0.001f)
        assertEquals(24f, preferences.landscapeGeometry.horizontalMarginDp, 0.001f)
        assertEquals(12f, preferences.landscapeGeometry.bottomMarginDp, 0.001f)
    }

    @Test
    fun glideTypingOptionsReflectPreferences() {
        val preferences = KeyboardPreferences(
            glideImportedWordCount = 8,
            glidePreferShorterWords = true,
            glideStrictFirstLastLetter = false,
            glidePathTolerance = GlidePathTolerance.LOOSE,
            glideImportedWordsPriority = GlideImportedWordsPriority.HIGH,
            glideRawFallbackMode = GlideRawFallbackMode.ALWAYS,
            glidePredictiveRankingEnabled = false,
        )

        val options = preferences.glideTypingOptions()

        assertEquals(8, options.importedWordCount)
        assertTrue(options.preferShorterWords)
        assertFalse(options.strictFirstLastLetter)
        assertEquals(GlidePathTolerance.LOOSE, options.pathTolerance)
        assertEquals(GlideImportedWordsPriority.HIGH, options.importedWordsPriority)
        assertEquals(GlideRawFallbackMode.ALWAYS, options.rawPathFallbackMode)
        assertFalse(options.predictiveRankingEnabled)
    }

    @Test
    fun glideCorrectionPreferenceValueNormalizesAndSkipsInvalidEntries() {
        val encoded = listOf(
            "TTE\tThe",
            "x\ttooShort",
            "valid\tbad-value",
            "wrd\tWard\t4\t2\t99",
        ).joinToString(separator = "\n")

        val corrections = glideCorrectionsFromPreferenceValue(encoded)

        assertEquals("the", corrections["te"]?.word)
        assertEquals("ward", corrections["wrd"]?.word)
        assertEquals(1, corrections["te"]?.acceptedCount)
        assertEquals(4, corrections["wrd"]?.acceptedCount)
        assertEquals(2, corrections["wrd"]?.rejectedCount)
        assertEquals("te\tthe\t1\t0\t0\nwrd\tward\t4\t2\t99", glideCorrectionsToPreferenceValue(corrections))
    }
}
