package org.leetboard.ime.engine

import android.content.res.Configuration
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.KeyboardState

class LayoutEngine {
    fun layoutFor(state: KeyboardState, orientation: Int): KeyboardLayout {
        return when {
            state.numpad && orientation == Configuration.ORIENTATION_LANDSCAPE -> numpad()
            state.symbols -> symbols()
            else -> qwertyFiveRow()
        }
    }

    private fun qwertyFiveRow(): KeyboardLayout {
        return KeyboardLayout(
            id = "qwerty5",
            name = "English QWERTY five-row",
            rows = listOf(
                row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                row("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                row("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                KeyRow(
                    listOf(
                        action("shift", "Shift", KeyActionType.SHIFT, 1.3f),
                        text("z"),
                        text("x"),
                        text("c"),
                        text("v"),
                        text("b"),
                        text("n"),
                        text("m"),
                        action("delete", "Del", KeyActionType.DELETE, 1.3f),
                    ),
                ),
                KeyRow(
                    listOf(
                        action("esc", "Esc", KeyActionType.ESCAPE, optional = true),
                        action("ctrl", "Ctrl", KeyActionType.CTRL, optional = true),
                        action("alt", "Alt", KeyActionType.ALT, optional = true),
                        action("symbols", "Sym", KeyActionType.SWITCH_SYMBOLS),
                        action("space", "Space", KeyActionType.SPACE, 4f),
                        action("tab", "Tab", KeyActionType.TAB, optional = true),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true),
                        action("enter", "Enter", KeyActionType.ENTER, 1.4f),
                    ),
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
                        action("symbols", "ABC", KeyActionType.SWITCH_SYMBOLS, 1.5f),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true),
                        action("up", "▲", KeyActionType.ARROW_UP, optional = true),
                        action("down", "▼", KeyActionType.ARROW_DOWN, optional = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true),
                        action("delete", "Del", KeyActionType.DELETE, 1.5f),
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

    private fun numpad(): KeyboardLayout {
        return KeyboardLayout(
            id = "landscape_numpad",
            name = "Landscape numpad",
            rows = listOf(
                row("7", "8", "9", "/"),
                row("4", "5", "6", "*"),
                row("1", "2", "3", "-"),
                row("0", ".", "=", "+"),
                KeyRow(
                    listOf(
                        action("num_toggle", "ABC", KeyActionType.NUMPAD_TOGGLE, 1.4f),
                        action("left", "◀", KeyActionType.ARROW_LEFT, optional = true),
                        action("right", "▶", KeyActionType.ARROW_RIGHT, optional = true),
                        action("enter", "Enter", KeyActionType.ENTER, 1.6f),
                    ),
                ),
            ),
        )
    }

    private fun row(vararg labels: String): KeyRow = KeyRow(labels.map(::text))

    private fun text(label: String): KeySpec {
        return KeySpec(
            id = "key_$label",
            label = label,
            action = KeyAction.text(label),
        )
    }

    private fun action(
        id: String,
        label: String,
        type: KeyActionType,
        weight: Float = 1f,
        optional: Boolean = false,
    ): KeySpec {
        return KeySpec(
            id = id,
            label = label,
            action = KeyAction(type),
            weight = weight,
            optional = optional,
        )
    }
}

