package org.leetboard.ime.prefs

import android.view.KeyEvent
import java.util.Base64
import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.displayLabel

const val MAX_BLUETOOTH_TRACKPAD_MACRO_KEYS_PER_SIDE = 6

enum class BluetoothTrackpadMacroSide {
    LEFT,
    RIGHT,
}

enum class BluetoothTrackpadMacroPlacement {
    OFF,
    LEFT,
    RIGHT,
    BOTH,
}

data class BluetoothTrackpadMacroStep(
    val action: KeyAction,
    val heldModifiers: HeldModifiers = HeldModifiers(),
)

data class BluetoothTrackpadMacroKey(
    val side: BluetoothTrackpadMacroSide,
    val index: Int,
    val label: String,
    val actionText: String,
    val steps: List<BluetoothTrackpadMacroStep> = parseBluetoothTrackpadMacroSteps(actionText),
) {
    val enabled: Boolean
        get() = label.isNotBlank() && steps.isNotEmpty()
}

val BluetoothTrackpadMacroSide.label: String
    get() = when (this) {
        BluetoothTrackpadMacroSide.LEFT -> "Left"
        BluetoothTrackpadMacroSide.RIGHT -> "Right"
    }

val BluetoothTrackpadMacroPlacement.label: String
    get() = when (this) {
        BluetoothTrackpadMacroPlacement.OFF -> "Off"
        BluetoothTrackpadMacroPlacement.LEFT -> "Left"
        BluetoothTrackpadMacroPlacement.RIGHT -> "Right"
        BluetoothTrackpadMacroPlacement.BOTH -> "Both"
    }

fun BluetoothTrackpadMacroPlacement.shows(side: BluetoothTrackpadMacroSide): Boolean {
    return when (this) {
        BluetoothTrackpadMacroPlacement.OFF -> false
        BluetoothTrackpadMacroPlacement.LEFT -> side == BluetoothTrackpadMacroSide.LEFT
        BluetoothTrackpadMacroPlacement.RIGHT -> side == BluetoothTrackpadMacroSide.RIGHT
        BluetoothTrackpadMacroPlacement.BOTH -> true
    }
}

fun List<BluetoothTrackpadMacroKey>.forSide(side: BluetoothTrackpadMacroSide): List<BluetoothTrackpadMacroKey> {
    return filter { macro -> macro.side == side && macro.enabled }
        .sortedBy { macro -> macro.index }
}

fun List<BluetoothTrackpadMacroKey>.macroAt(
    side: BluetoothTrackpadMacroSide,
    index: Int,
): BluetoothTrackpadMacroKey? {
    return firstOrNull { macro -> macro.side == side && macro.index == index }
}

fun parseBluetoothTrackpadMacroSteps(input: String): List<BluetoothTrackpadMacroStep> {
    return input
        .split(Regex("[,\\n]+"))
        .mapNotNull { rawStep -> parseBluetoothTrackpadMacroStep(rawStep.trim()) }
        .take(MAX_MACRO_STEPS)
}

fun BluetoothTrackpadMacroKey.toPreferenceValue(): String {
    return listOf(
        side.name,
        index.toString(),
        label.toBase64(),
        actionText.toBase64(),
    ).joinToString(MACRO_FIELD_SEPARATOR)
}

fun bluetoothTrackpadMacroFromPreferenceValue(value: String): BluetoothTrackpadMacroKey? {
    val parts = value.split(MACRO_FIELD_SEPARATOR, limit = 4)
    val side = parts.getOrNull(0)?.let { encoded ->
        BluetoothTrackpadMacroSide.entries.firstOrNull { side -> side.name == encoded }
    } ?: return null
    val index = parts.getOrNull(1)?.toIntOrNull()
        ?.takeIf { it in 0 until MAX_BLUETOOTH_TRACKPAD_MACRO_KEYS_PER_SIDE }
        ?: return null
    val label = parts.getOrNull(2)?.fromBase64() ?: return null
    val actionText = parts.getOrNull(3)?.fromBase64() ?: return null
    return BluetoothTrackpadMacroKey(
        side = side,
        index = index,
        label = label,
        actionText = actionText,
    )
}

fun BluetoothTrackpadMacroStep.displayLabel(): String {
    val modifiers = listOfNotNull(
        "Ctrl".takeIf { heldModifiers.ctrl },
        "Alt".takeIf { heldModifiers.alt },
        "Shift".takeIf { heldModifiers.shift },
        "Fn".takeIf { heldModifiers.fn },
    )
    return (modifiers + action.displayLabel()).joinToString("+")
}

private fun parseBluetoothTrackpadMacroStep(step: String): BluetoothTrackpadMacroStep? {
    if (step.isBlank()) return null
    if (step.startsWith(TEXT_PREFIX, ignoreCase = true)) {
        val text = step.substringAfter(":")
        return text.takeIf { it.isNotEmpty() }?.let { BluetoothTrackpadMacroStep(KeyAction.text(it)) }
    }
    val parts = step.split("+").map { part -> part.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val keyToken = parts.last()
    val modifiers = parts.dropLast(1)
    val held = HeldModifiers(
        ctrl = modifiers.any { it.equals("ctrl", true) || it.equals("control", true) },
        alt = modifiers.any { it.equals("alt", true) },
        shift = modifiers.any { it.equals("shift", true) },
        fn = modifiers.any { it.equals("fn", true) },
    )
    val action = keyActionForMacroToken(keyToken)
        ?: keyToken.takeIf { it.isNotBlank() }?.let { KeyAction.text(it) }
        ?: return null
    return BluetoothTrackpadMacroStep(action, held)
}

private fun keyActionForMacroToken(token: String): KeyAction? {
    val normalized = token.lowercase()
    val functionKey = normalized.removePrefix("f").toIntOrNull()
    if (normalized.startsWith("f") && functionKey != null && functionKey in 1..12) {
        return KeyAction.keyEvent(KeyEvent.KEYCODE_F1 + functionKey - 1, "F$functionKey")
    }
    return when (normalized) {
        "esc", "escape" -> KeyAction(KeyActionType.ESCAPE)
        "tab" -> KeyAction(KeyActionType.TAB)
        "enter", "return" -> KeyAction(KeyActionType.ENTER)
        "space", "spc" -> KeyAction(KeyActionType.SPACE)
        "backspace", "bksp", "bs" -> KeyAction(KeyActionType.DELETE)
        "del", "delete", "forward_delete" -> KeyAction.keyEvent(KeyEvent.KEYCODE_FORWARD_DEL, "Del")
        "left" -> KeyAction(KeyActionType.ARROW_LEFT)
        "right" -> KeyAction(KeyActionType.ARROW_RIGHT)
        "up" -> KeyAction(KeyActionType.ARROW_UP)
        "down" -> KeyAction(KeyActionType.ARROW_DOWN)
        "home" -> KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_HOME, "Home")
        "end" -> KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_END, "End")
        "pgup", "pageup", "page_up" -> KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_UP, "PgUp")
        "pgdn", "pagedown", "page_down" -> KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn")
        "ins", "insert" -> KeyAction.keyEvent(KeyEvent.KEYCODE_INSERT, "Ins")
        else -> null
    }
}

private fun String.toBase64(): String {
    return Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))
}

private fun String.fromBase64(): String? {
    return runCatching {
        String(Base64.getDecoder().decode(this), Charsets.UTF_8)
    }.getOrNull()
}

private const val TEXT_PREFIX = "text:"
private const val MACRO_FIELD_SEPARATOR = "\t"
private const val MAX_MACRO_STEPS = 12
