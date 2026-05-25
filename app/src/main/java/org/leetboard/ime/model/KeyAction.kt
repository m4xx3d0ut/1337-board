package org.leetboard.ime.model

enum class KeyActionType {
    NO_OP,
    COMMIT_TEXT,
    SPACE,
    DELETE,
    ENTER,
    SHIFT,
    CTRL,
    ALT,
    TAB,
    ESCAPE,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    SWITCH_SYMBOLS,
    SWITCH_FN,
    NUMPAD_TOGGLE,
    KEY_EVENT,
    SETTINGS,
    LANGUAGE_SWITCH,
    MICROPHONE,
    TOGGLE_SPEECH_INPUT,
    TOGGLE_GESTURE_TYPING,
}

data class KeyAction(
    val type: KeyActionType,
    val text: String? = null,
    val keyCode: Int? = null,
    val label: String? = null,
) {
    companion object {
        fun text(value: String) = KeyAction(KeyActionType.COMMIT_TEXT, value)
        fun keyEvent(keyCode: Int, label: String) = KeyAction(
            type = KeyActionType.KEY_EVENT,
            keyCode = keyCode,
            label = label,
        )
    }
}

fun KeyAction.displayLabel(): String {
    return when (type) {
        KeyActionType.NO_OP -> ""
        KeyActionType.COMMIT_TEXT -> text.orEmpty()
        KeyActionType.SPACE -> "Space"
        KeyActionType.DELETE -> "Backspace"
        KeyActionType.ENTER -> "Enter"
        KeyActionType.SHIFT -> "Shift"
        KeyActionType.CTRL -> "Ctrl"
        KeyActionType.ALT -> "Alt"
        KeyActionType.TAB -> "Tab"
        KeyActionType.ESCAPE -> "Esc"
        KeyActionType.ARROW_LEFT -> "Left"
        KeyActionType.ARROW_RIGHT -> "Right"
        KeyActionType.ARROW_UP -> "Up"
        KeyActionType.ARROW_DOWN -> "Down"
        KeyActionType.SWITCH_SYMBOLS -> "Sym"
        KeyActionType.SWITCH_FN -> "Fn"
        KeyActionType.NUMPAD_TOGGLE -> "Num"
        KeyActionType.KEY_EVENT -> label ?: keyCode?.toString().orEmpty()
        KeyActionType.SETTINGS -> "Settings"
        KeyActionType.LANGUAGE_SWITCH -> "Lang"
        KeyActionType.MICROPHONE -> "Mic"
        KeyActionType.TOGGLE_SPEECH_INPUT -> "Mic toggle"
        KeyActionType.TOGGLE_GESTURE_TYPING -> "Glide toggle"
    }
}

fun KeyAction.toPreferenceValue(): String {
    return when (type) {
        KeyActionType.COMMIT_TEXT -> "${type.name}:${text.orEmpty()}"
        KeyActionType.KEY_EVENT -> "${type.name}:${keyCode ?: 0}:${label.orEmpty()}"
        else -> type.name
    }
}

fun keyActionFromPreferenceValue(value: String?): KeyAction? {
    if (value.isNullOrBlank()) return null
    val typeName = value.substringBefore(":")
    val type = KeyActionType.entries.firstOrNull { it.name == typeName } ?: return null
    return when (type) {
        KeyActionType.COMMIT_TEXT -> KeyAction.text(value.substringAfter(":", ""))
        KeyActionType.KEY_EVENT -> {
            val parts = value.split(":", limit = 3)
            val keyCode = parts.getOrNull(1)?.toIntOrNull() ?: return null
            KeyAction.keyEvent(keyCode, parts.getOrNull(2).orEmpty())
        }
        else -> KeyAction(type)
    }
}
