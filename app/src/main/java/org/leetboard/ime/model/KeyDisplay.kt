package org.leetboard.ime.model

data class ResolvedKeyDisplay(
    val label: String,
    val icon: KeyIcon?,
    val secondaryLabel: String?,
    val secondaryIcon: KeyIcon?,
)

fun KeySpec.resolvedDisplay(shiftActive: Boolean, labelStyle: KeyLabelStyle): ResolvedKeyDisplay {
    val primaryLabel = if (
        labelStyle.uppercaseOnShift &&
        shiftActive &&
        action.type == KeyActionType.COMMIT_TEXT &&
        label.length == 1 &&
        label.first().isLetter()
    ) {
        label.uppercase()
    } else {
        label
    }
    return ResolvedKeyDisplay(
        label = primaryLabel,
        icon = icon,
        secondaryLabel = secondaryLabel,
        secondaryIcon = secondaryIcon,
    )
}

fun KeySpec.isModifierKey(): Boolean {
    return action.type == KeyActionType.SHIFT ||
        action.type == KeyActionType.CTRL ||
        action.type == KeyActionType.ALT ||
        action.type == KeyActionType.FN_MODIFIER
}

fun KeyIcon.fallbackLabel(): String {
    return when (this) {
        KeyIcon.GEAR -> "Settings"
        KeyIcon.SYMBOLS -> "Symbols"
        KeyIcon.SPACE_BAR -> "Space"
        KeyIcon.BACKSPACE -> "Backspace"
        KeyIcon.SHIFT -> "Shift"
        KeyIcon.FORWARD_DELETE -> "Forward delete"
        KeyIcon.MIC -> "Mic"
        KeyIcon.SWIPE -> "Swipe"
        KeyIcon.SWIPE_OFF -> "Swipe off"
        KeyIcon.NUMPAD -> "Num"
        KeyIcon.QUICK_NAV -> "Quick nav"
        KeyIcon.EMOJI -> "Emoji"
        KeyIcon.ENTER -> "Enter"
        KeyIcon.TAB -> "Tab"
        KeyIcon.ESC -> "Esc"
        KeyIcon.CTRL -> "Ctrl"
        KeyIcon.ALT -> "Alt"
        KeyIcon.FN -> "Fn"
        KeyIcon.BLUETOOTH -> "Bluetooth"
        KeyIcon.TRACKPAD -> "Trackpad"
        KeyIcon.ARROW_LEFT -> "Left"
        KeyIcon.ARROW_RIGHT -> "Right"
        KeyIcon.ARROW_UP -> "Up"
        KeyIcon.ARROW_DOWN -> "Down"
    }
}
