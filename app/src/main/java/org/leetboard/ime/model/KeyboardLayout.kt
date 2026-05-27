package org.leetboard.ime.model

data class KeySpec(
    val id: String,
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val icon: KeyIcon? = null,
    val secondaryLabel: String? = null,
    val secondaryIcon: KeyIcon? = null,
    val optional: Boolean = false,
    val repeatable: Boolean = false,
    val preserveSpaceWhenHidden: Boolean = false,
    val isSpacer: Boolean = false,
    val swipeUpAction: KeyAction? = null,
    val longPressAction: KeyAction? = null,
)

enum class KeyIcon {
    GEAR,
    SYMBOLS,
    SPACE_BAR,
    BACKSPACE,
    SHIFT,
    FORWARD_DELETE,
    MIC,
    SWIPE,
    SWIPE_OFF,
    NUMPAD,
    QUICK_NAV,
    EMOJI,
    ENTER,
    TAB,
    ESC,
    CTRL,
    ALT,
    FN,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
}

data class KeyDisplayOverride(
    val label: String? = null,
    val icon: KeyIcon? = null,
) {
    fun isEmpty(): Boolean = label.isNullOrBlank() && icon == null
}

enum class EscTouchMode {
    REGULAR,
    LONG_TAP,
}

data class KeyLabelStyle(
    val primaryTextSizeSp: Float = 18.5f,
    val secondaryTextSizeSp: Float = 10.5f,
    val fontWeight: Float = 400f,
    val labelOpacity: Float = 1f,
    val uppercaseOnShift: Boolean = true,
)

enum class RowAlignment {
    START,
    CENTER,
    END,
}

data class KeyRow(
    val keys: List<KeySpec>,
    val startInsetWeight: Float = 0f,
    val endInsetWeight: Float = 0f,
    val alignment: RowAlignment = RowAlignment.START,
    val layoutWeight: Float? = null,
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
    val fn: Boolean = false,
    val shiftLocked: Boolean = false,
)

data class KeyboardState(
    val activeLayoutId: String = "qwerty5",
    val symbols: Boolean = false,
    val fn: Boolean = false,
    val fnHold: Boolean = false,
    val quickNavHold: Boolean = false,
    val emoji: Boolean = false,
    val numpad: Boolean = false,
    val modifiers: ModifierState = ModifierState(),
    val keyPreviewEnabled: Boolean = true,
    val stickyModifiersEnabled: Boolean = true,
    val shiftCapsLockEnabled: Boolean = true,
    val edgeKeyWidthScale: Float = 0.75f,
)

data class HeldModifiers(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val fn: Boolean = false,
) {
    fun isActive(): Boolean = shift || ctrl || alt || fn
}

fun KeyboardState.activeKeyIds(): Set<String> = buildSet {
    if (modifiers.shift || modifiers.shiftLocked) add("shift")
    if (modifiers.shift || modifiers.shiftLocked) add("shift_right")
    if (modifiers.ctrl) add("ctrl")
    if (modifiers.alt) add("alt")
    if (modifiers.fn) add("quick_fn")
    if (symbols) add("symbols")
    if (fn || fnHold) add("fn")
    if (quickNavHold) add("num_toggle")
    if (emoji) add("emoji")
    if (numpad) add("num_toggle")
}

fun KeyboardState.toggleShift(): KeyboardState {
    val next = when {
        !shiftCapsLockEnabled && modifiers.shift -> modifiers.copy(shift = false, shiftLocked = false)
        !shiftCapsLockEnabled -> modifiers.copy(shift = true, shiftLocked = false)
        modifiers.shift && !modifiers.shiftLocked -> modifiers.copy(shift = true, shiftLocked = true)
        modifiers.shiftLocked -> modifiers.copy(shift = false, shiftLocked = false)
        else -> modifiers.copy(shift = true, shiftLocked = false)
    }
    return copy(modifiers = next)
}

fun KeyboardState.clearTransientModifiers(): KeyboardState {
    return copy(modifiers = modifiers.copy(shift = modifiers.shiftLocked, ctrl = false, alt = false, fn = false))
}

data class CustomizationState(
    val hiddenOptionalKeyIds: Set<String> = emptySet(),
    val slotActions: Map<String, KeyAction> = emptyMap(),
    val keyDisplayOverrides: Map<String, KeyDisplayOverride> = emptyMap(),
    val escTouchMode: EscTouchMode = EscTouchMode.REGULAR,
)

fun KeySpec.asSpacer(): KeySpec {
    return copy(
        label = "",
        optional = false,
        repeatable = false,
        icon = null,
        secondaryLabel = null,
        secondaryIcon = null,
        isSpacer = true,
        swipeUpAction = null,
        longPressAction = null,
    )
}
