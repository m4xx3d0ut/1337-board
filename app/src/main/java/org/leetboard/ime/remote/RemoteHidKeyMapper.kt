package org.leetboard.ime.remote

import android.view.KeyEvent
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.ModifierState
import org.leetboard.ime.model.clearTransientModifiers
import org.leetboard.ime.model.toggleShift

data class HidKeyChord(
    val usage: Int,
    val modifiers: Int = 0,
)

class RemoteHidKeyRouter(
    private val sendChord: (HidKeyChord) -> Boolean,
) {
    fun handle(action: KeyAction, state: KeyboardState, heldModifiers: HeldModifiers): KeyboardState {
        return when (action.type) {
            KeyActionType.NO_OP -> state
            KeyActionType.COMMIT_TEXT -> {
                RemoteHidKeyMapper.textChords(action.text.orEmpty(), state, heldModifiers).forEach { chord ->
                    sendChord(chord)
                }
                state.afterRemoteKey()
            }
            KeyActionType.SPACE -> sendSingle(RemoteHidKeyMapper.spaceChord(state, heldModifiers), state)
            KeyActionType.DELETE -> {
                val chord = if (heldModifiers.shift) {
                    RemoteHidKeyMapper.keyCodeChord(
                        KeyEvent.KEYCODE_FORWARD_DEL,
                        state,
                        heldModifiers.copy(shift = false),
                        suppressShift = true,
                    )
                } else {
                    RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_DEL, state, heldModifiers)
                }
                sendSingle(chord, state)
            }
            KeyActionType.ENTER -> sendSingle(
                RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_ENTER, state, heldModifiers),
                state,
            )
            KeyActionType.SHIFT -> state.toggleShift()
            KeyActionType.CTRL -> state.copy(modifiers = state.modifiers.copy(ctrl = !state.modifiers.ctrl))
            KeyActionType.ALT -> state.copy(modifiers = state.modifiers.copy(alt = !state.modifiers.alt))
            KeyActionType.FN_MODIFIER -> state.copy(modifiers = state.modifiers.copy(fn = !state.modifiers.fn))
            KeyActionType.TAB -> sendSingle(RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_TAB, state, heldModifiers), state)
            KeyActionType.ESCAPE -> sendSingle(RemoteHidKeyMapper.keyCodeChord(KeyEvent.KEYCODE_ESCAPE, state, heldModifiers), state)
            KeyActionType.ARROW_LEFT -> sendSingle(
                RemoteHidKeyMapper.arrowChord(
                    arrowKeyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                    navigationKeyCode = KeyEvent.KEYCODE_MOVE_HOME,
                    state = state,
                    heldModifiers = heldModifiers,
                ),
                state,
            )
            KeyActionType.ARROW_RIGHT -> sendSingle(
                RemoteHidKeyMapper.arrowChord(
                    arrowKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                    navigationKeyCode = KeyEvent.KEYCODE_MOVE_END,
                    state = state,
                    heldModifiers = heldModifiers,
                ),
                state,
            )
            KeyActionType.ARROW_UP -> sendSingle(
                RemoteHidKeyMapper.arrowChord(
                    arrowKeyCode = KeyEvent.KEYCODE_DPAD_UP,
                    navigationKeyCode = KeyEvent.KEYCODE_PAGE_UP,
                    state = state,
                    heldModifiers = heldModifiers,
                ),
                state,
            )
            KeyActionType.ARROW_DOWN -> sendSingle(
                RemoteHidKeyMapper.arrowChord(
                    arrowKeyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                    navigationKeyCode = KeyEvent.KEYCODE_PAGE_DOWN,
                    state = state,
                    heldModifiers = heldModifiers,
                ),
                state,
            )
            KeyActionType.SWITCH_SYMBOLS -> state.copy(symbols = !state.symbols, fn = false, fnHold = false, emoji = false, numpad = false)
            KeyActionType.SWITCH_FN -> state.copy(fn = !state.fn, fnHold = false, symbols = false, emoji = false, numpad = false)
            KeyActionType.SWITCH_EMOJI -> state.copy(emoji = !state.emoji, symbols = false, fn = false, fnHold = false, numpad = false)
            KeyActionType.NUMPAD_TOGGLE -> state.copy(numpad = !state.numpad, symbols = false, fn = false, fnHold = false, emoji = false)
            KeyActionType.KEY_EVENT -> sendSingle(
                action.keyCode?.let { RemoteHidKeyMapper.keyCodeChord(it, state, heldModifiers) },
                state,
            )
            KeyActionType.SETTINGS,
            KeyActionType.LANGUAGE_SWITCH,
            KeyActionType.MICROPHONE,
            KeyActionType.TOGGLE_SPEECH_INPUT,
            KeyActionType.TOGGLE_GESTURE_TYPING,
            KeyActionType.TOGGLE_BLUETOOTH_REMOTE,
            KeyActionType.BLUETOOTH_DEVICE_NEXT,
            KeyActionType.BLUETOOTH_LOCAL_INPUT,
            KeyActionType.BLUETOOTH_DEVICE_1,
            KeyActionType.BLUETOOTH_DEVICE_2,
            KeyActionType.BLUETOOTH_DEVICE_3,
            KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD -> state
        }
    }

    private fun sendSingle(chord: HidKeyChord?, state: KeyboardState): KeyboardState {
        if (chord != null) sendChord(chord)
        return state.afterRemoteKey()
    }

    private fun KeyboardState.afterRemoteKey(): KeyboardState {
        return if (modifiers.shiftLocked) {
            copy(modifiers = modifiers.copy(ctrl = false, alt = false, fn = false))
        } else {
            clearTransientModifiers()
        }
    }
}

object RemoteHidKeyMapper {
    fun textChords(text: String, state: KeyboardState, heldModifiers: HeldModifiers): List<HidKeyChord> {
        if (text.isEmpty()) return emptyList()
        val modifiers = state.modifiers.effectiveWith(heldModifiers)
        if (text.length == 1 && (modifiers.ctrl || modifiers.alt)) {
            return usageForControlText(text.first())?.let { usage ->
                listOf(HidKeyChord(usage = usage, modifiers = modifiers.toHidModifierByte()))
            }.orEmpty()
        }
        return text.mapNotNull { char ->
            val outputChar = shiftedTextChar(char, modifiers) ?: char
            chordForChar(outputChar)?.let { chord ->
                chord.copy(
                    modifiers = chord.modifiers or modifiers.toHidModifierByte(includeShift = false),
                )
            }
        }
    }

    fun spaceChord(state: KeyboardState, heldModifiers: HeldModifiers): HidKeyChord {
        return HidKeyChord(HID_SPACE, state.modifiers.effectiveWith(heldModifiers).toHidModifierByte())
    }

    fun arrowChord(
        arrowKeyCode: Int,
        navigationKeyCode: Int,
        state: KeyboardState,
        heldModifiers: HeldModifiers,
    ): HidKeyChord? {
        val modifiers = state.modifiers.effectiveWith(heldModifiers)
        return if (modifiers.fn) {
            keyCodeChord(navigationKeyCode, state, heldModifiers.copy(shift = false), suppressShift = true)
        } else {
            keyCodeChord(arrowKeyCode, state, heldModifiers)
        }
    }

    fun keyCodeChord(
        keyCode: Int,
        state: KeyboardState,
        heldModifiers: HeldModifiers,
        suppressShift: Boolean = false,
    ): HidKeyChord? {
        val usage = usageForKeyCode(keyCode) ?: return null
        val modifiers = state.modifiers.effectiveWith(heldModifiers).toHidModifierByte(includeShift = !suppressShift)
        return HidKeyChord(usage = usage, modifiers = modifiers)
    }

    fun chordForChar(char: Char): HidKeyChord? {
        val lower = char.lowercaseChar()
        return when {
            lower in 'a'..'z' -> HidKeyChord(HID_A + (lower - 'a'), if (char.isUpperCase()) MOD_LEFT_SHIFT else 0)
            char in '1'..'9' -> HidKeyChord(HID_1 + (char - '1'))
            char == '0' -> HidKeyChord(HID_0)
            else -> punctuationChords[char]
        }
    }

    private fun shiftedTextChar(char: Char, modifiers: ModifierState): Char? {
        val shiftActive = modifiers.shift || modifiers.shiftLocked
        if (!shiftActive) return null
        if (char.isLetter()) return char.uppercaseChar()
        if (modifiers.shiftLocked) return null
        return shiftAlternates[char]
    }

    private fun usageForControlText(char: Char): Int? {
        val lower = char.lowercaseChar()
        return when {
            lower in 'a'..'z' -> HID_A + (lower - 'a')
            lower in '1'..'9' -> HID_1 + (lower - '1')
            lower == '0' -> HID_0
            else -> null
        }
    }

    private fun usageForKeyCode(keyCode: Int): Int? {
        return when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> HID_A + (keyCode - KeyEvent.KEYCODE_A)
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> HID_1 + (keyCode - KeyEvent.KEYCODE_1)
            KeyEvent.KEYCODE_0 -> HID_0
            KeyEvent.KEYCODE_ENTER -> HID_ENTER
            KeyEvent.KEYCODE_ESCAPE -> HID_ESCAPE
            KeyEvent.KEYCODE_DEL -> HID_BACKSPACE
            KeyEvent.KEYCODE_TAB -> HID_TAB
            KeyEvent.KEYCODE_SPACE -> HID_SPACE
            KeyEvent.KEYCODE_MINUS -> HID_MINUS
            KeyEvent.KEYCODE_EQUALS -> HID_EQUALS
            KeyEvent.KEYCODE_LEFT_BRACKET -> HID_LEFT_BRACKET
            KeyEvent.KEYCODE_RIGHT_BRACKET -> HID_RIGHT_BRACKET
            KeyEvent.KEYCODE_BACKSLASH -> HID_BACKSLASH
            KeyEvent.KEYCODE_SEMICOLON -> HID_SEMICOLON
            KeyEvent.KEYCODE_APOSTROPHE -> HID_APOSTROPHE
            KeyEvent.KEYCODE_GRAVE -> HID_GRAVE
            KeyEvent.KEYCODE_COMMA -> HID_COMMA
            KeyEvent.KEYCODE_PERIOD -> HID_PERIOD
            KeyEvent.KEYCODE_SLASH -> HID_SLASH
            KeyEvent.KEYCODE_INSERT -> HID_INSERT
            KeyEvent.KEYCODE_MOVE_HOME -> HID_HOME
            KeyEvent.KEYCODE_PAGE_UP -> HID_PAGE_UP
            KeyEvent.KEYCODE_FORWARD_DEL -> HID_DELETE
            KeyEvent.KEYCODE_MOVE_END -> HID_END
            KeyEvent.KEYCODE_PAGE_DOWN -> HID_PAGE_DOWN
            KeyEvent.KEYCODE_DPAD_RIGHT -> HID_RIGHT
            KeyEvent.KEYCODE_DPAD_LEFT -> HID_LEFT
            KeyEvent.KEYCODE_DPAD_DOWN -> HID_DOWN
            KeyEvent.KEYCODE_DPAD_UP -> HID_UP
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> HID_F1 + (keyCode - KeyEvent.KEYCODE_F1)
            else -> null
        }
    }

    private fun ModifierState.effectiveWith(heldModifiers: HeldModifiers): ModifierState {
        return copy(
            shift = shift || heldModifiers.shift,
            ctrl = ctrl || heldModifiers.ctrl,
            alt = alt || heldModifiers.alt,
            fn = fn || heldModifiers.fn,
        )
    }

    private fun ModifierState.toHidModifierByte(includeShift: Boolean = true): Int {
        var modifiers = 0
        if (includeShift && (shift || shiftLocked)) modifiers = modifiers or MOD_LEFT_SHIFT
        if (ctrl) modifiers = modifiers or MOD_LEFT_CTRL
        if (alt) modifiers = modifiers or MOD_LEFT_ALT
        return modifiers
    }

    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04

    private const val HID_A = 0x04
    private const val HID_1 = 0x1E
    private const val HID_0 = 0x27
    private const val HID_ENTER = 0x28
    private const val HID_ESCAPE = 0x29
    private const val HID_BACKSPACE = 0x2A
    private const val HID_TAB = 0x2B
    private const val HID_SPACE = 0x2C
    private const val HID_MINUS = 0x2D
    private const val HID_EQUALS = 0x2E
    private const val HID_LEFT_BRACKET = 0x2F
    private const val HID_RIGHT_BRACKET = 0x30
    private const val HID_BACKSLASH = 0x31
    private const val HID_SEMICOLON = 0x33
    private const val HID_APOSTROPHE = 0x34
    private const val HID_GRAVE = 0x35
    private const val HID_COMMA = 0x36
    private const val HID_PERIOD = 0x37
    private const val HID_SLASH = 0x38
    private const val HID_F1 = 0x3A
    private const val HID_INSERT = 0x49
    private const val HID_HOME = 0x4A
    private const val HID_PAGE_UP = 0x4B
    private const val HID_DELETE = 0x4C
    private const val HID_END = 0x4D
    private const val HID_PAGE_DOWN = 0x4E
    private const val HID_RIGHT = 0x4F
    private const val HID_LEFT = 0x50
    private const val HID_DOWN = 0x51
    private const val HID_UP = 0x52

    private val shiftAlternates = mapOf(
        '1' to '!',
        '2' to '@',
        '3' to '#',
        '4' to '$',
        '5' to '%',
        '6' to '^',
        '7' to '&',
        '8' to '*',
        '9' to '(',
        '0' to ')',
        '-' to '_',
        '=' to '+',
        '`' to '~',
        '[' to '{',
        ']' to '}',
        '\\' to '|',
        ';' to ':',
        '\'' to '"',
        ',' to '<',
        '.' to '>',
        '/' to '?',
    )

    private val punctuationChords = mapOf(
        ' ' to HidKeyChord(HID_SPACE),
        '-' to HidKeyChord(HID_MINUS),
        '_' to HidKeyChord(HID_MINUS, MOD_LEFT_SHIFT),
        '=' to HidKeyChord(HID_EQUALS),
        '+' to HidKeyChord(HID_EQUALS, MOD_LEFT_SHIFT),
        '[' to HidKeyChord(HID_LEFT_BRACKET),
        '{' to HidKeyChord(HID_LEFT_BRACKET, MOD_LEFT_SHIFT),
        ']' to HidKeyChord(HID_RIGHT_BRACKET),
        '}' to HidKeyChord(HID_RIGHT_BRACKET, MOD_LEFT_SHIFT),
        '\\' to HidKeyChord(HID_BACKSLASH),
        '|' to HidKeyChord(HID_BACKSLASH, MOD_LEFT_SHIFT),
        ';' to HidKeyChord(HID_SEMICOLON),
        ':' to HidKeyChord(HID_SEMICOLON, MOD_LEFT_SHIFT),
        '\'' to HidKeyChord(HID_APOSTROPHE),
        '"' to HidKeyChord(HID_APOSTROPHE, MOD_LEFT_SHIFT),
        '`' to HidKeyChord(HID_GRAVE),
        '~' to HidKeyChord(HID_GRAVE, MOD_LEFT_SHIFT),
        ',' to HidKeyChord(HID_COMMA),
        '<' to HidKeyChord(HID_COMMA, MOD_LEFT_SHIFT),
        '.' to HidKeyChord(HID_PERIOD),
        '>' to HidKeyChord(HID_PERIOD, MOD_LEFT_SHIFT),
        '/' to HidKeyChord(HID_SLASH),
        '?' to HidKeyChord(HID_SLASH, MOD_LEFT_SHIFT),
        '!' to HidKeyChord(HID_1, MOD_LEFT_SHIFT),
        '@' to HidKeyChord(HID_1 + 1, MOD_LEFT_SHIFT),
        '#' to HidKeyChord(HID_1 + 2, MOD_LEFT_SHIFT),
        '$' to HidKeyChord(HID_1 + 3, MOD_LEFT_SHIFT),
        '%' to HidKeyChord(HID_1 + 4, MOD_LEFT_SHIFT),
        '^' to HidKeyChord(HID_1 + 5, MOD_LEFT_SHIFT),
        '&' to HidKeyChord(HID_1 + 6, MOD_LEFT_SHIFT),
        '*' to HidKeyChord(HID_1 + 7, MOD_LEFT_SHIFT),
        '(' to HidKeyChord(HID_1 + 8, MOD_LEFT_SHIFT),
        ')' to HidKeyChord(HID_0, MOD_LEFT_SHIFT),
    )
}
