package org.leetboard.ime.model

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
    fun preferenceRoundTripPreservesTextAction() {
        val action = KeyAction.text("|")

        assertEquals(action, keyActionFromPreferenceValue(action.toPreferenceValue()))
    }

    @Test
    fun invalidPreferenceValueReturnsNull() {
        assertNull(keyActionFromPreferenceValue("NOPE"))
    }
}

