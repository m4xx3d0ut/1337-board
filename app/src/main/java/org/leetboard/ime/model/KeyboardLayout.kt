package org.leetboard.ime.model

data class KeySpec(
    val id: String,
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val optional: Boolean = false,
    val repeatable: Boolean = false,
    val swipeUpAction: KeyAction? = null,
    val longPressAction: KeyAction? = null,
)

data class KeyRow(
    val keys: List<KeySpec>,
)

data class KeyboardLayout(
    val id: String,
    val name: String,
    val rows: List<KeyRow>,
)

data class ModifierState(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shiftLocked: Boolean = false,
)

data class KeyboardState(
    val activeLayoutId: String = "qwerty5",
    val symbols: Boolean = false,
    val numpad: Boolean = false,
    val modifiers: ModifierState = ModifierState(),
    val keyPreviewEnabled: Boolean = true,
)

fun KeyboardState.activeKeyIds(): Set<String> = buildSet {
    if (modifiers.shift || modifiers.shiftLocked) add("shift")
    if (modifiers.ctrl) add("ctrl")
    if (modifiers.alt) add("alt")
    if (symbols) add("symbols")
    if (numpad) add("num_toggle")
}

fun KeyboardState.toggleShift(): KeyboardState {
    val next = when {
        modifiers.shift && !modifiers.shiftLocked -> modifiers.copy(shift = true, shiftLocked = true)
        modifiers.shiftLocked -> modifiers.copy(shift = false, shiftLocked = false)
        else -> modifiers.copy(shift = true, shiftLocked = false)
    }
    return copy(modifiers = next)
}

fun KeyboardState.clearTransientModifiers(): KeyboardState {
    return copy(modifiers = modifiers.copy(shift = modifiers.shiftLocked, ctrl = false, alt = false))
}

data class CustomizationState(
    val hiddenOptionalKeyIds: Set<String> = emptySet(),
    val slotActions: Map<String, KeyAction> = emptyMap(),
)
