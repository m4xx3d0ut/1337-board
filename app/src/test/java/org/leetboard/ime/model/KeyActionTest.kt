package org.leetboard.ime.model

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyActionTest {
    @Test
    fun preferenceRoundTripPreservesActionType() {
        val action = KeyAction(KeyActionType.NUMPAD_TOGGLE)

        assertEquals(action, keyActionFromPreferenceValue(action.toPreferenceValue()))
    }

    @Test
    fun preferenceRoundTripPreservesNoOpAndFeatureToggles() {
        listOf(
            KeyAction(KeyActionType.NO_OP),
            KeyAction(KeyActionType.TOGGLE_SPEECH_INPUT),
            KeyAction(KeyActionType.TOGGLE_GESTURE_TYPING),
            KeyAction(KeyActionType.SWITCH_EMOJI),
            KeyAction(KeyActionType.FN_MODIFIER),
        ).forEach { action ->
            assertEquals(action, keyActionFromPreferenceValue(action.toPreferenceValue()))
        }
    }

    @Test
    fun preferenceRoundTripPreservesTextAction() {
        val action = KeyAction.text("|")

        assertEquals(action, keyActionFromPreferenceValue(action.toPreferenceValue()))
    }

    @Test
    fun preferenceRoundTripPreservesKeyEventAction() {
        val action = KeyAction.keyEvent(KeyEvent.KEYCODE_F5, "F5")

        assertEquals(action, keyActionFromPreferenceValue(action.toPreferenceValue()))
        assertEquals("F5", action.displayLabel())
        assertEquals("Fn", KeyAction(KeyActionType.FN_MODIFIER).displayLabel())
    }

    @Test
    fun invalidPreferenceValueReturnsNull() {
        assertNull(keyActionFromPreferenceValue("NOPE"))
    }

    @Test
    fun invalidKeyEventPreferenceValueReturnsNull() {
        assertNull(keyActionFromPreferenceValue("KEY_EVENT:nope:F5"))
    }
}
