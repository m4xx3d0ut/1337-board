package org.leetboard.ime.engine

import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.ModifierState

fun String.applyKeyboardCapitalization(
    modifiers: ModifierState,
    heldModifiers: HeldModifiers,
    autoCapAfterPeriod: Boolean,
    textBeforeCursor: CharSequence?,
    allCapsOnShift: Boolean = true,
): String {
    if (isEmpty()) return this
    return when {
        modifiers.shiftLocked -> uppercase()
        modifiers.shift || heldModifiers.shift -> if (allCapsOnShift) uppercase() else capitalizeFirstChar()
        autoCapAfterPeriod && shouldAutoCapAfterPeriod(textBeforeCursor) -> replaceFirstChar { it.uppercase() }
        else -> this
    }
}

fun shouldAutoCapAfterPeriod(textBeforeCursor: CharSequence?): Boolean {
    val trimmed = textBeforeCursor?.toString()?.trimEnd().orEmpty()
    return trimmed.lastOrNull() == '.'
}

private fun String.capitalizeFirstChar(): String {
    return replaceFirstChar { it.uppercase() }
}
