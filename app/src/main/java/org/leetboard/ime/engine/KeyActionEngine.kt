package org.leetboard.ime.engine

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.SettingsActivity
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.ModifierState
import org.leetboard.ime.model.clearTransientModifiers
import org.leetboard.ime.model.toggleShift

class KeyActionEngine(
    private val service: InputMethodService,
) {
    fun handle(
        action: KeyAction,
        state: KeyboardState,
        heldModifiers: HeldModifiers = HeldModifiers(),
        autoCapAfterPeriod: Boolean = true,
    ): KeyboardState {
        return when (action.type) {
            KeyActionType.NO_OP -> state
            KeyActionType.COMMIT_TEXT -> commitText(action.text.orEmpty(), state, heldModifiers, autoCapAfterPeriod)
            KeyActionType.SPACE -> commitText(" ", state, heldModifiers, autoCapAfterPeriod)
            KeyActionType.DELETE -> {
                val modifiers = state.modifiers.effectiveWith(heldModifiers)
                when {
                    heldModifiers.shift -> sendKey(
                        KeyEvent.KEYCODE_FORWARD_DEL,
                        state,
                        heldModifiers,
                        suppressShift = true,
                    )
                    modifiers.ctrl || modifiers.alt -> sendKey(KeyEvent.KEYCODE_DEL, state, heldModifiers)
                    else -> deleteBackwards(state)
                }
            }
            KeyActionType.ENTER -> {
                if (state.modifiers.effectiveWith(heldModifiers).hasMeta()) {
                    sendKey(KeyEvent.KEYCODE_ENTER, state, heldModifiers)
                } else {
                    performEnter()
                    state.clearTransientModifiers()
                }
            }
            KeyActionType.SHIFT -> state.toggleShift()
            KeyActionType.CTRL -> state.copy(modifiers = state.modifiers.copy(ctrl = !state.modifiers.ctrl))
            KeyActionType.ALT -> state.copy(modifiers = state.modifiers.copy(alt = !state.modifiers.alt))
            KeyActionType.TAB -> sendKey(KeyEvent.KEYCODE_TAB, state, heldModifiers)
            KeyActionType.ESCAPE -> sendKey(KeyEvent.KEYCODE_ESCAPE, state, heldModifiers)
            KeyActionType.ARROW_LEFT -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, state, heldModifiers)
            KeyActionType.ARROW_RIGHT -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, state, heldModifiers)
            KeyActionType.ARROW_UP -> sendKey(KeyEvent.KEYCODE_DPAD_UP, state, heldModifiers)
            KeyActionType.ARROW_DOWN -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, state, heldModifiers)
            KeyActionType.SWITCH_SYMBOLS -> state.copy(symbols = !state.symbols, fn = false, numpad = false)
            KeyActionType.SWITCH_FN -> state.copy(fn = !state.fn, symbols = false, numpad = false)
            KeyActionType.NUMPAD_TOGGLE -> state.copy(numpad = !state.numpad, symbols = false, fn = false)
            KeyActionType.KEY_EVENT -> {
                action.keyCode?.let { keyCode -> sendKey(keyCode, state, heldModifiers) } ?: state
            }
            KeyActionType.SETTINGS -> {
                service.startActivity(
                    Intent(service, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                state
            }
            KeyActionType.LANGUAGE_SWITCH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.switchToNextInputMethod(false)
                }
                state
            }
            KeyActionType.MICROPHONE -> state
            KeyActionType.TOGGLE_SPEECH_INPUT -> state
            KeyActionType.TOGGLE_GESTURE_TYPING -> state
        }
    }

    private fun commitText(
        text: String,
        state: KeyboardState,
        heldModifiers: HeldModifiers,
        autoCapAfterPeriod: Boolean,
    ): KeyboardState {
        val modifiers = state.modifiers.effectiveWith(heldModifiers)
        if (text.length == 1 && (modifiers.ctrl || modifiers.alt)) {
            val keyCode = letterKeyCode(text.first())
            if (keyCode != null) return sendKey(keyCode, state, heldModifiers)
        }

        val output = text.applyKeyboardCapitalization(
            modifiers = state.modifiers,
            heldModifiers = heldModifiers,
            autoCapAfterPeriod = autoCapAfterPeriod && text.length == 1 && text.first().isLetter(),
            textBeforeCursor = service.currentInputConnection?.getTextBeforeCursor(AUTO_CAP_CONTEXT_CHARS, 0),
        )
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

    private fun deleteBackwards(state: KeyboardState): KeyboardState {
        val inputConnection = service.currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            inputConnection?.deleteSurroundingText(1, 0)
        }
        return state.clearTransientModifiers()
    }

    private fun sendKey(
        keyCode: Int,
        state: KeyboardState,
        heldModifiers: HeldModifiers,
        suppressShift: Boolean = false,
    ): KeyboardState {
        val eventTime = System.currentTimeMillis()
        val modifiers = state.modifiers.effectiveWith(heldModifiers)
        val metaState = if (suppressShift) {
            modifiers.copy(shift = false, shiftLocked = false).toMetaState()
        } else {
            modifiers.toMetaState()
        }
        service.currentInputConnection?.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState),
        )
        service.currentInputConnection?.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, metaState),
        )
        return state.clearTransientModifiers()
    }

    private fun ModifierState.effectiveWith(heldModifiers: HeldModifiers): ModifierState {
        return copy(
            shift = shift || heldModifiers.shift,
            ctrl = ctrl || heldModifiers.ctrl,
            alt = alt || heldModifiers.alt,
        )
    }

    private fun ModifierState.hasMeta(): Boolean {
        return shift || shiftLocked || ctrl || alt
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

    private companion object {
        const val AUTO_CAP_CONTEXT_CHARS = 8
    }
}
