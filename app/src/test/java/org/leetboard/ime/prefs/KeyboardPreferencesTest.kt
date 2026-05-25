package org.leetboard.ime.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun defaultsUseFiveRowLayoutAndKeyPreview() {
        val preferences = KeyboardPreferences.defaults()

        assertEquals("qwerty5", preferences.layoutId)
        assertTrue(preferences.keyPreviewEnabled)
    }
}
