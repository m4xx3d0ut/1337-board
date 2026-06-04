package org.leetboard.ime.remote

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.ModifierState

class RemoteHidKeyMapperTest {
    @Test
    fun textMapsLettersToKeyboardUsages() {
        assertEquals(
            listOf(HidKeyChord(0x04), HidKeyChord(0x05), HidKeyChord(0x06)),
            RemoteHidKeyMapper.textChords("abc", KeyboardState(), HeldModifiers()),
        )
    }

    @Test
    fun shiftMapsNumberToShiftedPunctuation() {
        assertEquals(
            listOf(HidKeyChord(0x1E, RemoteHidKeyMapper.MOD_LEFT_SHIFT)),
            RemoteHidKeyMapper.textChords(
                "1",
                KeyboardState(modifiers = ModifierState(shift = true)),
                HeldModifiers(),
            ),
        )
    }

    @Test
    fun textMapsWhitespaceControlsToKeyboardUsages() {
        assertEquals(
            listOf(HidKeyChord(0x2B), HidKeyChord(0x28)),
            RemoteHidKeyMapper.textChords("\t\n", KeyboardState(), HeldModifiers()),
        )
    }

    @Test
    fun outputTextReflectsShiftButIgnoresShortcutModifiers() {
        assertEquals(
            "A!",
            RemoteHidKeyMapper.outputText(
                "a1",
                KeyboardState(modifiers = ModifierState(shift = true)),
                HeldModifiers(),
            ),
        )
        assertEquals(
            "",
            RemoteHidKeyMapper.outputText(
                "a",
                KeyboardState(modifiers = ModifierState(ctrl = true)),
                HeldModifiers(),
            ),
        )
    }

    @Test
    fun ctrlNumberMapsToNumericUsageWithCtrlModifier() {
        assertEquals(
            listOf(HidKeyChord(0x1E, RemoteHidKeyMapper.MOD_LEFT_CTRL)),
            RemoteHidKeyMapper.textChords(
                "1",
                KeyboardState(modifiers = ModifierState(ctrl = true)),
                HeldModifiers(),
            ),
        )
    }

    @Test
    fun fnArrowMapsToNavigationUsage() {
        assertEquals(
            HidKeyChord(0x4A),
            RemoteHidKeyMapper.arrowChord(
                arrowKeyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                navigationKeyCode = KeyEvent.KEYCODE_MOVE_HOME,
                state = KeyboardState(modifiers = ModifierState(fn = true)),
                heldModifiers = HeldModifiers(),
            ),
        )
    }

    @Test
    fun keyEventMapsFunctionKey() {
        assertEquals(
            HidKeyChord(0x3E),
            RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_F5, KeyboardState(), HeldModifiers()),
        )
    }

    @Test
    fun routerClearsTransientModifiersAfterRemoteKey() {
        val sent = mutableListOf<HidKeyChord>()
        val router = RemoteHidKeyRouter { chord ->
            sent += chord
            true
        }
        val nextState = router.handle(
            KeyAction.text("a"),
            KeyboardState(modifiers = ModifierState(shift = true, ctrl = true)),
            HeldModifiers(),
        )

        assertEquals(listOf(HidKeyChord(0x04, RemoteHidKeyMapper.MOD_LEFT_CTRL or RemoteHidKeyMapper.MOD_LEFT_SHIFT)), sent)
        assertEquals(ModifierState(), nextState.modifiers)
    }

    @Test
    fun routerLeavesBluetoothControlActionsLocal() {
        val router = RemoteHidKeyRouter { error("No report expected") }
        val state = KeyboardState(modifiers = ModifierState(ctrl = true))

        assertEquals(state, router.handle(KeyAction(KeyActionType.TOGGLE_BLUETOOTH_REMOTE), state, HeldModifiers()))
    }
}
