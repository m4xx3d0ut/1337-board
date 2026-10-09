package org.leetboard.ime.engine

import android.view.KeyEvent
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.prefs.HardwareCompanionPreset

class HardwareCompanionLayoutEngine {
    fun layout(preset: HardwareCompanionPreset): KeyboardLayout {
        val specialKeys = specialKeys(preset)
        val functionKeys = functionKeys()
        return KeyboardLayout(
            id = "hardware_companion_${preset.name.lowercase()}",
            name = "Hardware keyboard companion",
            rows = listOf(
                KeyRow(
                    specialKeys,
                    layoutWeight = specialKeys.sumOf { key -> key.weight.toDouble() }.toFloat(),
                ),
                KeyRow(
                    functionKeys,
                    layoutWeight = functionKeys.sumOf { key -> key.weight.toDouble() }.toFloat(),
                ),
            ),
        )
    }

    private fun specialKeys(preset: HardwareCompanionPreset): List<KeySpec> {
        val commonNavigation = listOf(
            keyEvent("companion_home", "Home", KeyEvent.KEYCODE_MOVE_HOME),
            keyEvent("companion_pgup", "PgUp", KeyEvent.KEYCODE_PAGE_UP),
            keyEvent("companion_pgdn", "PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
            keyEvent("companion_end", "End", KeyEvent.KEYCODE_MOVE_END),
            action("companion_left", "Left", KeyActionType.ARROW_LEFT, KeyIcon.ARROW_LEFT),
            action("companion_up", "Up", KeyActionType.ARROW_UP, KeyIcon.ARROW_UP),
            action("companion_down", "Down", KeyActionType.ARROW_DOWN, KeyIcon.ARROW_DOWN),
            action("companion_right", "Right", KeyActionType.ARROW_RIGHT, KeyIcon.ARROW_RIGHT),
        )
        val prefix = when (preset) {
            HardwareCompanionPreset.AGENT_PBX,
            HardwareCompanionPreset.TERMINAL -> listOf(
                action("companion_esc", "Esc", KeyActionType.ESCAPE, KeyIcon.ESC),
                action("companion_tab", "Tab", KeyActionType.TAB, KeyIcon.TAB),
                action("companion_shift", "Shift", KeyActionType.SHIFT, KeyIcon.SHIFT),
                action("companion_ctrl", "Ctrl", KeyActionType.CTRL, KeyIcon.CTRL),
                action("companion_alt", "Alt", KeyActionType.ALT, KeyIcon.ALT),
            )
            HardwareCompanionPreset.MINIMAL -> listOf(
                action("companion_esc", "Esc", KeyActionType.ESCAPE, KeyIcon.ESC),
                action("companion_tab", "Tab", KeyActionType.TAB, KeyIcon.TAB),
            )
        }
        val extras = when (preset) {
            HardwareCompanionPreset.TERMINAL -> listOf(
                keyEvent("companion_insert", "Ins", KeyEvent.KEYCODE_INSERT),
                keyEvent("companion_forward_delete", "Del", KeyEvent.KEYCODE_FORWARD_DEL),
            )
            HardwareCompanionPreset.AGENT_PBX,
            HardwareCompanionPreset.MINIMAL -> emptyList()
        }
        return prefix + extras + commonNavigation
    }

    private fun functionKeys(): List<KeySpec> {
        return (1..12).map { index ->
            keyEvent("companion_f$index", "F$index", KeyEvent.KEYCODE_F1 + index - 1)
        }
    }

    private fun action(id: String, label: String, type: KeyActionType, icon: KeyIcon): KeySpec {
        return KeySpec(id = id, label = label, action = KeyAction(type), icon = icon)
    }

    private fun keyEvent(id: String, label: String, keyCode: Int): KeySpec {
        return KeySpec(id = id, label = label, action = KeyAction.keyEvent(keyCode, label))
    }
}
