package org.leetboard.ime.engine

import android.view.KeyEvent
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState
import org.leetboard.ime.model.RowAlignment

class LayoutEngine {
    fun layoutFor(state: KeyboardState, @Suppress("UNUSED_PARAMETER") orientation: Int): KeyboardLayout {
        return when {
            state.numpad -> numpad()
            state.fn -> functionLayer()
            state.symbols -> symbols()
            state.activeLayoutId == "qwerty4" -> qwertyFourRow()
            state.activeLayoutId == "compact5" -> compactFiveRow()
            else -> qwertyFiveRow(state.edgeKeyWidthScale)
        }
    }

    private fun qwertyFiveRow(edgeKeyWidthScale: Float): KeyboardLayout {
        val edgeScale = edgeKeyWidthScale.coerceIn(MIN_EDGE_KEY_SCALE, MAX_EDGE_KEY_SCALE)
        return KeyboardLayout(
            id = "qwerty5",
            name = "HK-style QWERTY five-row",
            rows = listOf(
                KeyRow(
                    listOf(
                        text("`", weight = 0.8f, swipe = "~"),
                        text("1", swipe = "!"),
                        text("2", swipe = "@"),
                        text("3", swipe = "#"),
                        text("4", swipe = "$"),
                        text("5", swipe = "%"),
                        text("6", swipe = "^"),
                        text("7", swipe = "&"),
                        text("8", swipe = "*"),
                        text("9", swipe = "("),
                        text("0", swipe = ")"),
                        text("-", swipe = "_"),
                        text("=", swipe = "+"),
                        action("delete", "Backspace", KeyActionType.DELETE, 2.2f, repeatable = true),
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
                KeyRow(
                    balancedEdgeRow(
                        action("tab", "Tab", KeyActionType.TAB, 1.5f, optional = true, preserveSpaceWhenHidden = true),
                        text("q"),
                        text("w"),
                        text("e"),
                        text("r"),
                        text("t"),
                        text("y"),
                        text("u"),
                        text("i"),
                        text("o"),
                        text("p"),
                        text("[", swipe = "{"),
                        text("]", swipe = "}"),
                        text("\\", weight = 1.5f, swipe = "|"),
                        edgeScale = edgeScale,
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
                KeyRow(
                    balancedEdgeRow(
                        action("ctrl", "Ctrl", KeyActionType.CTRL, 1.7f, optional = true, preserveSpaceWhenHidden = true),
                        text("a"),
                        text("s"),
                        text("d"),
                        text("f"),
                        text("g"),
                        text("h"),
                        text("j"),
                        text("k"),
                        text("l"),
                        text(";", swipe = ":"),
                        text("'", swipe = "\""),
                        action("enter", "Enter", KeyActionType.ENTER, 2.3f),
                        edgeScale = edgeScale,
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
                KeyRow(
                    balancedEdgeRow(
                        action("shift", "Shift", KeyActionType.SHIFT, 2.2f, preserveSpaceWhenHidden = true),
                        text("z", swipe = "~"),
                        text("x", swipe = "`"),
                        text("c", swipe = "|"),
                        text("v", swipe = "\\"),
                        text("b", swipe = "{"),
                        text("n", swipe = "}"),
                        text("m", swipe = "$"),
                        text(",", swipe = "<"),
                        text(".", swipe = ">"),
                        text("/", swipe = "?"),
                        action("up", "▲", KeyActionType.ARROW_UP, optional = true, repeatable = true),
                        action("shift_right", "Shift", KeyActionType.SHIFT, 1.8f, preserveSpaceWhenHidden = true),
                        edgeScale = edgeScale,
                        rightEdgeScale = edgeScale.coerceAtMost(UP_ARROW_RIGHT_EDGE_SCALE),
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        action("esc", "Esc", KeyActionType.ESCAPE, 1.2f, optional = true, preserveSpaceWhenHidden = true),
                        action("alt", "Alt", KeyActionType.ALT, optional = true),
                        action("fn", "Fn", KeyActionType.SWITCH_FN, optional = true),
                        action("symbols", "Symbols", KeyActionType.SWITCH_SYMBOLS, longPress = KeyAction(KeyActionType.SETTINGS)),
                        action("space", "Space", KeyActionType.SPACE, 5f),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        micKey(),
                        action("num_toggle", "Num", KeyActionType.NUMPAD_TOGGLE, optional = true),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true, repeatable = true),
                        action("down", "▼", KeyActionType.ARROW_DOWN, optional = true, repeatable = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true, repeatable = true),
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
            ),
        )
    }

    private fun functionLayer(): KeyboardLayout {
        return KeyboardLayout(
            id = "function",
            name = "Function and navigation",
            rows = listOf(
                KeyRow(
                    (1..12).map { index ->
                        keyEvent("f$index", "F$index", KeyEvent.KEYCODE_F1 + index - 1)
                    },
                    layoutWeight = 12f,
                ),
                KeyRow(
                    listOf(
                        action("esc", "Esc", KeyActionType.ESCAPE),
                        action("tab", "Tab", KeyActionType.TAB),
                        keyEvent("insert", "Ins", KeyEvent.KEYCODE_INSERT),
                        keyEvent("forward_delete", "Forward delete", KeyEvent.KEYCODE_FORWARD_DEL, repeatable = true),
                        keyEvent("home", "Home", KeyEvent.KEYCODE_MOVE_HOME, repeatable = true),
                        keyEvent("end", "End", KeyEvent.KEYCODE_MOVE_END, repeatable = true),
                        keyEvent("page_up", "PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
                        keyEvent("page_down", "PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true),
                        keyEvent("dpad_center", "Center", KeyEvent.KEYCODE_DPAD_CENTER),
                    ),
                    layoutWeight = 9f,
                ),
                KeyRow(
                    listOf(
                        action("ctrl", "Ctrl", KeyActionType.CTRL, optional = true),
                        action("alt", "Alt", KeyActionType.ALT, optional = true),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        micKey(),
                        action("delete", "Backspace", KeyActionType.DELETE, 1.5f, repeatable = true),
                        action("enter", "Enter", KeyActionType.ENTER, 1.5f),
                    ),
                    layoutWeight = 7f,
                    alignment = RowAlignment.CENTER,
                ),
                KeyRow(
                    listOf(
                        spacer(6f),
                        action("up", "▲", KeyActionType.ARROW_UP, optional = true, repeatable = true),
                        spacer(6f),
                    ),
                    layoutWeight = FULL_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        action("fn", "ABC", KeyActionType.SWITCH_FN, 1.2f),
                        action("num_toggle", "Num", KeyActionType.NUMPAD_TOGGLE, optional = true),
                        action("symbols", "Symbols", KeyActionType.SWITCH_SYMBOLS),
                        action("space", "Space", KeyActionType.SPACE, 5f),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true, repeatable = true),
                        action("down", "▼", KeyActionType.ARROW_DOWN, optional = true, repeatable = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true, repeatable = true),
                    ),
                    layoutWeight = 12.2f,
                    alignment = RowAlignment.CENTER,
                ),
            ),
        )
    }

    private fun symbols(): KeyboardLayout {
        return KeyboardLayout(
            id = "symbols",
            name = "Symbols",
            rows = listOf(
                row("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
                row("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"),
                row("`", "~", ";", ":", "'", "\"", ",", ".", "/", "?"),
                KeyRow(
                    listOf(
                        action("symbols", "ABC", KeyActionType.SWITCH_SYMBOLS, 1.5f, longPress = KeyAction(KeyActionType.SETTINGS)),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true, repeatable = true),
                        action("up", "▲", KeyActionType.ARROW_UP, optional = true, repeatable = true),
                        action("down", "▼", KeyActionType.ARROW_DOWN, optional = true, repeatable = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true, repeatable = true),
                        action("delete", "Backspace", KeyActionType.DELETE, 1.5f, repeatable = true),
                    ),
                ),
                KeyRow(
                    listOf(
                        action("num_toggle", "Num", KeyActionType.NUMPAD_TOGGLE, optional = true),
                        action("space", "Space", KeyActionType.SPACE, 5f),
                        action("enter", "Enter", KeyActionType.ENTER, 1.5f),
                    ),
                ),
            ),
        )
    }

    private fun qwertyFourRow(): KeyboardLayout {
        return KeyboardLayout(
            id = "qwerty4",
            name = "HK-style QWERTY four-row",
            rows = listOf(
                row("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", layoutWeight = PHONE_GRID_WEIGHT),
                row("a", "s", "d", "f", "g", "h", "j", "k", "l", startInsetWeight = 0.5f, endInsetWeight = 0.5f, layoutWeight = PHONE_GRID_WEIGHT),
                KeyRow(
                    listOf(
                        action("shift", "Shift", KeyActionType.SHIFT, 1.4f, preserveSpaceWhenHidden = true),
                        text("z", swipe = "~"),
                        text("x", swipe = "`"),
                        text("c", swipe = "|"),
                        text("v", swipe = "\\"),
                        text("b", swipe = "{"),
                        text("n", swipe = "}"),
                        text("m", swipe = "$"),
                        action("delete", "Backspace", KeyActionType.DELETE, 1.4f, repeatable = true),
                    ),
                    layoutWeight = PHONE_GRID_WEIGHT,
                    alignment = RowAlignment.CENTER,
                ),
                KeyRow(
                    listOf(
                        action("esc", "Esc", KeyActionType.ESCAPE, optional = true),
                        action("symbols", "Symbols", KeyActionType.SWITCH_SYMBOLS, longPress = KeyAction(KeyActionType.SETTINGS)),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        action("space", "Space", KeyActionType.SPACE, 2.2f),
                        micKey(),
                        action("num_toggle", "Num", KeyActionType.NUMPAD_TOGGLE, optional = true),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true, repeatable = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true, repeatable = true),
                        action("enter", "Enter", KeyActionType.ENTER, 1.4f),
                    ),
                    layoutWeight = PHONE_GRID_WEIGHT,
                ),
            ),
        )
    }

    private fun compactFiveRow(): KeyboardLayout {
        return KeyboardLayout(
            id = "compact5",
            name = "HK-style compact five-row",
            rows = listOf(
                KeyRow(
                    listOf(
                        text("1", swipe = "!"),
                        text("2", swipe = "@"),
                        text("3", swipe = "#"),
                        text("4", swipe = "$"),
                        text("5", swipe = "%"),
                        text("6", swipe = "^"),
                        text("7", swipe = "&"),
                        text("8", swipe = "*"),
                        text("9", swipe = "("),
                        text("0", swipe = ")"),
                        action("delete", "Backspace", KeyActionType.DELETE, repeatable = true),
                    ),
                    layoutWeight = COMPACT_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        text("q"),
                        text("w"),
                        text("e"),
                        text("r"),
                        text("t"),
                        text("y"),
                        text("u"),
                        text("i"),
                        text("o"),
                        text("p"),
                        text("'", swipe = "\""),
                    ),
                    layoutWeight = COMPACT_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        text("a"),
                        text("s"),
                        text("d"),
                        text("f"),
                        text("g"),
                        text("h"),
                        text("j"),
                        text("k"),
                        text("l"),
                        action("enter", "Enter", KeyActionType.ENTER, 1.5f),
                    ),
                    startInsetWeight = 0.5f,
                    layoutWeight = COMPACT_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        action("shift", "Shift", KeyActionType.SHIFT, 1.2f, preserveSpaceWhenHidden = true),
                        text("z", swipe = "~"),
                        text("x", swipe = "`"),
                        text("c", swipe = "|"),
                        text("v", swipe = "\\"),
                        text("b", swipe = "{"),
                        text("n", swipe = "}"),
                        text("m", swipe = "$"),
                        text(",", swipe = "<"),
                        text(".", swipe = ">"),
                        text("/", swipe = "?"),
                    ),
                    layoutWeight = COMPACT_GRID_WEIGHT,
                ),
                KeyRow(
                    listOf(
                        action("esc", "Esc", KeyActionType.ESCAPE, optional = true),
                        action("symbols", "Symbols", KeyActionType.SWITCH_SYMBOLS, longPress = KeyAction(KeyActionType.SETTINGS)),
                        action("space", "Space", KeyActionType.SPACE, 3f),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        micKey(),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true, repeatable = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true, repeatable = true),
                        action("num_toggle", "Num", KeyActionType.NUMPAD_TOGGLE, optional = true),
                    ),
                    layoutWeight = COMPACT_GRID_WEIGHT,
                ),
            ),
        )
    }

    private fun numpad(): KeyboardLayout {
        return KeyboardLayout(
            id = "landscape_numpad",
            name = "Numpad",
            rows = listOf(
                KeyRow(
                    listOf(
                        keyEvent("home", "Home", KeyEvent.KEYCODE_MOVE_HOME, repeatable = true),
                        keyEvent("end", "End", KeyEvent.KEYCODE_MOVE_END, repeatable = true),
                        keyEvent("page_up", "PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
                        keyEvent("page_down", "PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true),
                        action("delete", "Backspace", KeyActionType.DELETE, 1.4f, repeatable = true),
                    ),
                    layoutWeight = 5.4f,
                ),
                KeyRow(
                    listOf(
                        text("7"),
                        text("8"),
                        text("9"),
                        text("/"),
                        keyEvent("insert", "Ins", KeyEvent.KEYCODE_INSERT),
                    ),
                    layoutWeight = 5f,
                ),
                KeyRow(
                    listOf(
                        text("4"),
                        text("5"),
                        text("6"),
                        text("*"),
                        keyEvent("forward_delete", "Forward delete", KeyEvent.KEYCODE_FORWARD_DEL, repeatable = true),
                    ),
                    layoutWeight = 5f,
                ),
                KeyRow(
                    listOf(
                        text("1"),
                        text("2"),
                        text("3"),
                        text("-"),
                        action("tab", "Tab", KeyActionType.TAB),
                    ),
                    layoutWeight = 5f,
                ),
                KeyRow(
                    listOf(
                        text("0", weight = 2f),
                        text("."),
                        text("="),
                        text("+"),
                    ),
                    layoutWeight = 5f,
                ),
                KeyRow(
                    listOf(
                        action("num_toggle", "ABC", KeyActionType.NUMPAD_TOGGLE),
                        action("fn", "Fn", KeyActionType.SWITCH_FN),
                        action("settings", "Settings", KeyActionType.SETTINGS, optional = true),
                        action("enter", "Enter", KeyActionType.ENTER, 1.6f),
                    ),
                    layoutWeight = 4.6f,
                ),
            ),
        )
    }

    private fun row(
        vararg labels: String,
        startInsetWeight: Float = 0f,
        endInsetWeight: Float = 0f,
        alignment: RowAlignment = RowAlignment.START,
        layoutWeight: Float? = null,
    ): KeyRow {
        return KeyRow(
            keys = labels.map { label -> text(label) },
            startInsetWeight = startInsetWeight,
            endInsetWeight = endInsetWeight,
            alignment = alignment,
            layoutWeight = layoutWeight,
        )
    }

    private fun balancedEdgeRow(
        vararg keys: KeySpec,
        edgeScale: Float,
        rightEdgeScale: Float = edgeScale,
    ): List<KeySpec> {
        if (keys.size < 3) return keys.toList()
        val left = keys.first()
        val right = keys.last()
        val middle = keys.drop(1).dropLast(1)
        val leftWeight = left.weight * edgeScale
        val rightWeight = right.weight * rightEdgeScale
        val freedWeight = (left.weight - leftWeight) + (right.weight - rightWeight)
        val middleBonus = freedWeight / middle.size
        return listOf(left.copy(weight = leftWeight)) +
            middle.map { key -> key.copy(weight = key.weight + middleBonus) } +
            right.copy(weight = rightWeight)
    }

    private fun text(
        label: String,
        weight: Float = 1f,
        swipe: String? = defaultSwipeAlternates[label],
    ): KeySpec {
        return KeySpec(
            id = "key_$label",
            label = label,
            action = KeyAction.text(label),
            weight = weight,
            secondaryLabel = swipe,
            swipeUpAction = swipe?.let(KeyAction::text),
            longPressAction = swipe?.let(KeyAction::text),
        )
    }

    private fun action(
        id: String,
        label: String,
        type: KeyActionType,
        weight: Float = 1f,
        optional: Boolean = false,
        repeatable: Boolean = false,
        preserveSpaceWhenHidden: Boolean = false,
        longPress: KeyAction? = null,
    ): KeySpec {
        val isBackspace = type == KeyActionType.DELETE
        val navigationSecondary = navigationSecondary(type)
        return KeySpec(
            id = id,
            label = label,
            action = KeyAction(type),
            weight = weight,
            icon = defaultIcon(type, id),
            secondaryIcon = if (isBackspace) KeyIcon.FORWARD_DELETE else null,
            secondaryLabel = navigationSecondary?.label,
            optional = optional,
            repeatable = repeatable,
            preserveSpaceWhenHidden = preserveSpaceWhenHidden,
            swipeUpAction = navigationSecondary?.action,
            longPressAction = longPress ?: navigationSecondary?.action?.takeUnless { repeatable },
        )
    }

    private fun micKey(weight: Float = 1f): KeySpec {
        return action(
            id = "mic",
            label = "Mic",
            type = KeyActionType.MICROPHONE,
            weight = weight,
            optional = true,
        )
    }

    private fun keyEvent(
        id: String,
        label: String,
        keyCode: Int,
        weight: Float = 1f,
        optional: Boolean = false,
        repeatable: Boolean = false,
    ): KeySpec {
        return KeySpec(
            id = id,
            label = label,
            action = KeyAction.keyEvent(keyCode, label),
            weight = weight,
            icon = defaultKeyEventIcon(keyCode),
            optional = optional,
            repeatable = repeatable,
        )
    }

    private fun defaultIcon(type: KeyActionType, id: String): KeyIcon? {
        return when (type) {
            KeyActionType.SPACE -> KeyIcon.SPACE_BAR
            KeyActionType.DELETE -> KeyIcon.BACKSPACE
            KeyActionType.SHIFT -> KeyIcon.SHIFT
            KeyActionType.SWITCH_SYMBOLS -> KeyIcon.SYMBOLS
            KeyActionType.SETTINGS -> KeyIcon.GEAR
            KeyActionType.MICROPHONE,
            KeyActionType.TOGGLE_SPEECH_INPUT -> KeyIcon.MIC
            KeyActionType.TOGGLE_GESTURE_TYPING -> KeyIcon.SWIPE
            KeyActionType.NUMPAD_TOGGLE -> KeyIcon.NUMPAD
            KeyActionType.ENTER -> KeyIcon.ENTER
            KeyActionType.TAB -> KeyIcon.TAB
            KeyActionType.ESCAPE -> KeyIcon.ESC
            KeyActionType.CTRL -> KeyIcon.CTRL
            KeyActionType.ALT -> KeyIcon.ALT
            KeyActionType.SWITCH_FN -> if (id == "fn") KeyIcon.FN else null
            KeyActionType.ARROW_LEFT -> KeyIcon.ARROW_LEFT
            KeyActionType.ARROW_RIGHT -> KeyIcon.ARROW_RIGHT
            KeyActionType.ARROW_UP -> KeyIcon.ARROW_UP
            KeyActionType.ARROW_DOWN -> KeyIcon.ARROW_DOWN
            else -> null
        }
    }

    private fun defaultKeyEventIcon(keyCode: Int): KeyIcon? {
        return when (keyCode) {
            KeyEvent.KEYCODE_FORWARD_DEL -> KeyIcon.FORWARD_DELETE
            else -> null
        }
    }

    private fun navigationSecondary(type: KeyActionType): SecondaryAction? {
        return when (type) {
            KeyActionType.ARROW_LEFT -> SecondaryAction("Home", KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_HOME, "Home"))
            KeyActionType.ARROW_RIGHT -> SecondaryAction("End", KeyAction.keyEvent(KeyEvent.KEYCODE_MOVE_END, "End"))
            KeyActionType.ARROW_UP -> SecondaryAction("PgUp", KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_UP, "PgUp"))
            KeyActionType.ARROW_DOWN -> SecondaryAction("PgDn", KeyAction.keyEvent(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn"))
            else -> null
        }
    }

    private data class SecondaryAction(
        val label: String,
        val action: KeyAction,
    )

    private fun spacer(weight: Float): KeySpec {
        return KeySpec(
            id = "spacer_$weight",
            label = "",
            action = KeyAction.text(""),
            weight = weight,
            isSpacer = true,
        )
    }

    companion object {
        private const val FULL_GRID_WEIGHT = 15f
        private const val COMPACT_GRID_WEIGHT = 11f
        private const val PHONE_GRID_WEIGHT = 10f
        private const val MIN_EDGE_KEY_SCALE = 0.6f
        private const val MAX_EDGE_KEY_SCALE = 1.1f
        private const val UP_ARROW_RIGHT_EDGE_SCALE = 0.56f

        private val defaultSwipeAlternates = mapOf(
            "1" to "!",
            "2" to "@",
            "3" to "#",
            "4" to "$",
            "5" to "%",
            "6" to "^",
            "7" to "&",
            "8" to "*",
            "9" to "(",
            "0" to ")",
            "-" to "_",
            "=" to "+",
            "`" to "~",
            "[" to "{",
            "]" to "}",
            "\\" to "|",
            ";" to ":",
            "'" to "\"",
            "," to "<",
            "." to ">",
            "/" to "?",
        )
    }
}
