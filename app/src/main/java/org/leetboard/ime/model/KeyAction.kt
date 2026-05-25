package org.leetboard.ime.model

enum class KeyActionType {
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
    NUMPAD_TOGGLE,
    SETTINGS,
    LANGUAGE_SWITCH,
    MICROPHONE,
}

data class KeyAction(
    val type: KeyActionType,
    val text: String? = null,
) {
    companion object {
        fun text(value: String) = KeyAction(KeyActionType.COMMIT_TEXT, value)
    }
}

fun KeyAction.displayLabel(): String {
    return when (type) {
        KeyActionType.COMMIT_TEXT -> text.orEmpty()
        KeyActionType.SPACE -> "Space"
        KeyActionType.DELETE -> "Del"
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
        KeyActionType.NUMPAD_TOGGLE -> "Num"
        KeyActionType.SETTINGS -> "Settings"
        KeyActionType.LANGUAGE_SWITCH -> "Lang"
        KeyActionType.MICROPHONE -> "Mic"
    }
}

fun KeyAction.toPreferenceValue(): String {
    return when (type) {
        KeyActionType.COMMIT_TEXT -> "${type.name}:${text.orEmpty()}"
        else -> type.name
    }
}

fun keyActionFromPreferenceValue(value: String?): KeyAction? {
    if (value.isNullOrBlank()) return null
    val typeName = value.substringBefore(":")
    val type = KeyActionType.entries.firstOrNull { it.name == typeName } ?: return null
    return if (type == KeyActionType.COMMIT_TEXT) {
        KeyAction.text(value.substringAfter(":", ""))
    } else {
        KeyAction(type)
    }
}
