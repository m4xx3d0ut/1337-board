package org.leetboard.ime.prefs

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.engine.GlideDwellSensitivity
import org.leetboard.ime.engine.GlideImportedWordsPriority
import org.leetboard.ime.engine.GlidePathTolerance
import org.leetboard.ime.engine.GlideRawFallbackMode
import org.leetboard.ime.engine.GlideSpatialPrecision
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
        assertTrue("mic" in customization.hiddenOptionalKeyIds)
    }

    @Test
    fun customizationStatePreservesSlotActions() {
        val action = KeyAction(KeyActionType.TAB)
        val preferences = KeyboardPreferences(slotActions = mapOf("esc" to action))

        assertEquals(action, preferences.customizationState().slotActions["esc"])
    }

    @Test
    fun customizationStateShowsMicOnlyAfterSpeechOptIn() {
        val disabled = KeyboardPreferences(speechInputEnabled = false)
        val enabled = KeyboardPreferences(speechInputEnabled = true)

        assertTrue("mic" in disabled.customizationState().hiddenOptionalKeyIds)
        assertFalse("mic" in enabled.customizationState().hiddenOptionalKeyIds)
    }

    @Test
    fun customizationStateShowsOnlyAssignedBluetoothHotkeySlots() {
        val unassigned = KeyboardPreferences()
        val oneSlot = KeyboardPreferences(bluetoothDeviceSlotAddresses = listOf("AA:BB", null, null))
        val twoSlots = KeyboardPreferences(bluetoothDeviceSlotAddresses = listOf("AA:BB", "CC:DD", null))

        assertTrue("bt_local" in unassigned.customizationState().hiddenOptionalKeyIds)
        assertTrue("bt_device_1" in unassigned.customizationState().hiddenOptionalKeyIds)
        assertTrue("bt_trackpad" in unassigned.customizationState().hiddenOptionalKeyIds)

        assertFalse("bt_local" in oneSlot.customizationState().hiddenOptionalKeyIds)
        assertFalse("bt_device_1" in oneSlot.customizationState().hiddenOptionalKeyIds)
        assertTrue("bt_device_2" in oneSlot.customizationState().hiddenOptionalKeyIds)
        assertFalse("bt_device_next" in oneSlot.customizationState().hiddenOptionalKeyIds)
        assertFalse("bt_trackpad" in oneSlot.customizationState().hiddenOptionalKeyIds)

        assertTrue("bt_device_3" in twoSlots.customizationState().hiddenOptionalKeyIds)
    }

    @Test
    fun defaultsUseFiveRowLayoutAndKeyPreview() {
        val preferences = KeyboardPreferences.defaults()

        assertEquals("qwerty5", preferences.layoutId)
        assertEquals("qwerty5", preferences.portraitLayoutId)
        assertEquals("qwerty5", preferences.landscapeLayoutId)
        assertTrue(preferences.keyPreviewEnabled)
        assertTrue(preferences.keyHapticsEnabled)
        assertTrue(preferences.stickyModifiersEnabled)
        assertTrue(preferences.shiftCapsLockEnabled)
        assertEquals(DEFAULT_KEY_LONG_PRESS_DELAY_MS, preferences.keyLongPressDelayMs)
        assertEquals(DEFAULT_SPECIAL_LONG_PRESS_DELAY_MS, preferences.specialLongPressDelayMs)
        assertTrue(preferences.swipeUpActionsEnabled)
        assertFalse(preferences.speechInputEnabled)
        assertFalse(preferences.speechPushToTalkEnabled)
        assertEquals(DEFAULT_SPEECH_COMPLETE_SILENCE_MS, preferences.speechCompleteSilenceMs)
        assertEquals(DEFAULT_SPEECH_POSSIBLE_SILENCE_MS, preferences.speechPossibleSilenceMs)
        assertTrue(preferences.speechSmartCleanupEnabled)
        assertTrue(preferences.speechAutoSpacingEnabled)
        assertTrue(preferences.speechAutoCapAfterPunctuationEnabled)
        assertTrue(preferences.speechAutoCapNamesEnabled)
        assertTrue(preferences.speechSpokenPunctuationEnabled)
        assertTrue(preferences.speechCustomNames.isEmpty())
        assertTrue(preferences.typedSuggestionsEnabled)
        assertFalse(preferences.typedAutocorrectEnabled)
        assertTrue(preferences.autoCapAfterPeriodEnabled)
        assertFalse(preferences.glideCorrectionLearningEnabled)
        assertTrue(preferences.glideCorrections.isEmpty())
        assertTrue(preferences.glidePreferShorterWords)
        assertFalse(preferences.glideStrictFirstLastLetter)
        assertTrue(preferences.glidePredictiveRankingEnabled)
        assertEquals(GlidePathTolerance.LOOSE, preferences.glidePathTolerance)
        assertEquals(GlideSpatialPrecision.STANDARD, preferences.glideSpatialPrecision)
        assertEquals(GlideDwellSensitivity.HIGH, preferences.glideDwellSensitivity)
        assertEquals(0.30214944f, preferences.glideDwellActivationThreshold, 0.001f)
        assertEquals(GlideImportedWordsPriority.HIGH, preferences.glideImportedWordsPriority)
        assertEquals(GlideRawFallbackMode.OFF, preferences.glideRawFallbackMode)
        assertEquals(EscTouchMode.REGULAR, preferences.escTouchMode)
        assertEquals(0.75f, preferences.edgeKeyWidthScale, 0.001f)
        assertTrue(preferences.compactBottomControlsRightHandEnabled)
        assertEquals(19.5f, preferences.keyLabelStyle.primaryTextSizeSp, 0.001f)
        assertEquals(12.5f, preferences.keyLabelStyle.secondaryTextSizeSp, 0.001f)
        assertEquals(34f, preferences.portraitGeometry.keyboardHeightPercent, 0.001f)
        assertEquals(16f, preferences.portraitGeometry.horizontalMarginDp, 0.001f)
        assertEquals(12f, preferences.portraitGeometry.bottomMarginDp, 0.001f)
        assertEquals(48f, preferences.landscapeGeometry.keyboardHeightPercent, 0.001f)
        assertEquals(24f, preferences.landscapeGeometry.horizontalMarginDp, 0.001f)
        assertEquals(12f, preferences.landscapeGeometry.bottomMarginDp, 0.001f)
        assertEquals(DEFAULT_BLUETOOTH_TRACKPAD_KEEP_SCREEN_ON_MODE, preferences.bluetoothTrackpadKeepScreenOnMode)
        assertTrue(preferences.bluetoothTrackpadDimWhenInactiveEnabled)
    }

    @Test
    fun layoutIdForOrientationUsesPerOrientationSettings() {
        val preferences = KeyboardPreferences(
            layoutId = "qwerty5",
            portraitLayoutId = "qwerty4",
            landscapeLayoutId = "compact5",
        )

        assertEquals("qwerty4", preferences.layoutIdForOrientation(Configuration.ORIENTATION_PORTRAIT))
        assertEquals("compact5", preferences.layoutIdForOrientation(Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun glideTypingOptionsReflectPreferences() {
        val preferences = KeyboardPreferences(
            glideImportedWordCount = 8,
            glidePreferShorterWords = true,
            glideStrictFirstLastLetter = false,
            glidePathTolerance = GlidePathTolerance.LOOSE,
            glideSpatialPrecision = GlideSpatialPrecision.PRECISE,
            glideDwellSensitivity = GlideDwellSensitivity.HIGH,
            glideDwellActivationThreshold = 0.5f,
            glideImportedWordsPriority = GlideImportedWordsPriority.HIGH,
            glideRawFallbackMode = GlideRawFallbackMode.ALWAYS,
            glidePredictiveRankingEnabled = false,
        )

        val options = preferences.glideTypingOptions()

        assertEquals(8, options.importedWordCount)
        assertTrue(options.preferShorterWords)
        assertFalse(options.strictFirstLastLetter)
        assertEquals(GlidePathTolerance.LOOSE, options.pathTolerance)
        assertEquals(GlideSpatialPrecision.PRECISE, options.spatialPrecision)
        assertEquals(GlideDwellSensitivity.HIGH, options.dwellSensitivity)
        assertEquals(0.5f, options.dwellActivationThreshold, 0.001f)
        assertEquals(GlideImportedWordsPriority.HIGH, options.importedWordsPriority)
        assertEquals(GlideRawFallbackMode.ALWAYS, options.rawPathFallbackMode)
        assertFalse(options.predictiveRankingEnabled)
    }

    @Test
    fun speechCustomNamesNormalizeTextInput() {
        val names = normalizeSpeechCustomNamesText(" OnePlus, NASA\nOnePlus\nbad name\nA ")

        assertEquals(setOf("OnePlus", "NASA"), names)
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
