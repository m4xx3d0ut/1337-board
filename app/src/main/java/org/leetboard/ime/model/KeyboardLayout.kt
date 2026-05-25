package org.leetboard.ime.model

data class KeySpec(
    val id: String,
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val optional: Boolean = false,
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
)

data class CustomizationState(
    val hiddenOptionalKeyIds: Set<String> = emptySet(),
    val slotActions: Map<String, KeyAction> = emptyMap(),
)

