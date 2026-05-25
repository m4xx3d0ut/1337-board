package org.leetboard.ime.engine

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.ModifierState

class KeyActionEngine(
    private val service: InputMethodService,
) {
    fun handle(action: KeyAction, state: KeyboardState): KeyboardState {
        return when (action.type) {
            KeyActionType.COMMIT_TEXT -> commitText(action.text.orEmpty(), state)
            KeyActionType.SPACE -> commitText(" ", state)
            KeyActionType.DELETE -> {
                service.currentInputConnection?.deleteSurroundingText(1, 0)
                state
            }
            KeyActionType.ENTER -> {
                performEnter()
                state.clearTransientModifiers()
            }
            KeyActionType.SHIFT -> state.toggleShift()
            KeyActionType.CTRL -> state.copy(modifiers = state.modifiers.copy(ctrl = !state.modifiers.ctrl))
            KeyActionType.ALT -> state.copy(modifiers = state.modifiers.copy(alt = !state.modifiers.alt))
            KeyActionType.TAB -> sendKey(KeyEvent.KEYCODE_TAB, state)
            KeyActionType.ESCAPE -> sendKey(KeyEvent.KEYCODE_ESCAPE, state)
            KeyActionType.ARROW_LEFT -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, state)
            KeyActionType.ARROW_RIGHT -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, state)
            KeyActionType.ARROW_UP -> sendKey(KeyEvent.KEYCODE_DPAD_UP, state)
            KeyActionType.ARROW_DOWN -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, state)
            KeyActionType.SWITCH_SYMBOLS -> state.copy(symbols = !state.symbols, numpad = false)
            KeyActionType.NUMPAD_TOGGLE -> state.copy(numpad = !state.numpad, symbols = false)
            KeyActionType.SETTINGS,
            KeyActionType.LANGUAGE_SWITCH,
            KeyActionType.MICROPHONE -> state
        }
    }

    private fun commitText(text: String, state: KeyboardState): KeyboardState {
        if (text.length == 1 && (state.modifiers.ctrl || state.modifiers.alt)) {
            val keyCode = letterKeyCode(text.first())
            if (keyCode != null) return sendKey(keyCode, state)
        }

        val output = if (state.modifiers.shift || state.modifiers.shiftLocked) text.uppercase() else text
        service.currentInputConnection?.commitText(output, 1)
        return if (state.modifiers.shiftLocked) {
            state.copy(modifiers = state.modifiers.copy(ctrl = false, alt = false))
        } else {
            state.clearTransientModifiers()
        }
    }

    private fun performEnter() {
        val editorInfo = service.currentInputEditorInfo
        val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val consumed = actionId != EditorInfo.IME_ACTION_NONE &&
            actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            service.currentInputConnection?.performEditorAction(actionId) == true
        if (!consumed) {
            service.currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun sendKey(keyCode: Int, state: KeyboardState): KeyboardState {
        val eventTime = System.currentTimeMillis()
        val metaState = state.modifiers.toMetaState()
        service.currentInputConnection?.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState),
        )
        service.currentInputConnection?.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, metaState),
        )
        return state.clearTransientModifiers()
    }

    private fun KeyboardState.toggleShift(): KeyboardState {
        val next = when {
            modifiers.shift && !modifiers.shiftLocked -> modifiers.copy(shift = true, shiftLocked = true)
            modifiers.shiftLocked -> modifiers.copy(shift = false, shiftLocked = false)
            else -> modifiers.copy(shift = true, shiftLocked = false)
        }
        return copy(modifiers = next)
    }

    private fun KeyboardState.clearTransientModifiers(): KeyboardState {
        return copy(modifiers = modifiers.copy(shift = modifiers.shiftLocked, ctrl = false, alt = false))
    }

    private fun ModifierState.toMetaState(): Int {
        var meta = 0
        if (shift || shiftLocked) meta = meta or KeyEvent.META_SHIFT_ON
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON
        if (alt) meta = meta or KeyEvent.META_ALT_ON
        return meta
    }

    private fun letterKeyCode(char: Char): Int? {
        val lower = char.lowercaseChar()
        if (lower !in 'a'..'z') return null
        return KeyEvent.KEYCODE_A + (lower - 'a')
    }
}

