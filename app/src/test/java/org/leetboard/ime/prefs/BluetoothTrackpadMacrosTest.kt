package org.leetboard.ime.prefs

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.leetboard.ime.model.KeyActionType
import org.junit.Test

class BluetoothTrackpadMacrosTest {
    @Test
    fun parsesTextNamedKeysAndModifierCombos() {
        val steps = parseBluetoothTrackpadMacroSteps("ctrl+c, shift+tab, f5, text:git status")

        assertEquals(4, steps.size)
        assertEquals(KeyActionType.COMMIT_TEXT, steps[0].action.type)
        assertEquals("c", steps[0].action.text)
        assertTrue(steps[0].heldModifiers.ctrl)
        assertEquals(KeyActionType.TAB, steps[1].action.type)
        assertTrue(steps[1].heldModifiers.shift)
        assertEquals(KeyActionType.KEY_EVENT, steps[2].action.type)
        assertEquals(KeyEvent.KEYCODE_F5, steps[2].action.keyCode)
        assertEquals("git status", steps[3].action.text)
    }

    @Test
    fun macroPreferenceValueRoundTripsRawInput() {
        val macro = BluetoothTrackpadMacroKey(
            side = BluetoothTrackpadMacroSide.RIGHT,
            index = 2,
            label = "Copy",
            actionText = "ctrl+c",
        )

        val decoded = bluetoothTrackpadMacroFromPreferenceValue(macro.toPreferenceValue())

        requireNotNull(decoded)
        assertEquals(BluetoothTrackpadMacroSide.RIGHT, decoded.side)
        assertEquals(2, decoded.index)
        assertEquals("Copy", decoded.label)
        assertEquals("ctrl+c", decoded.actionText)
        assertTrue(decoded.enabled)
    }

    @Test
    fun incompleteMacrosAreStoredButNotEnabled() {
        val noLabel = BluetoothTrackpadMacroKey(
            side = BluetoothTrackpadMacroSide.LEFT,
            index = 0,
            label = "",
            actionText = "enter",
        )
        val noAction = BluetoothTrackpadMacroKey(
            side = BluetoothTrackpadMacroSide.LEFT,
            index = 1,
            label = "Run",
            actionText = "",
        )

        assertFalse(noLabel.enabled)
        assertFalse(noAction.enabled)
    }
}
