package org.leetboard.ime.engine

import android.view.KeyEvent
import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.asSpacer
import org.leetboard.ime.model.displayLabel

class CustomizationEngine {
    fun apply(layout: KeyboardLayout, customization: CustomizationState): KeyboardLayout {
        val rows = layout.rows.mapNotNull { row ->
            var spaceWeightBonus = 0f
            val canAbsorbIntoSpace = row.keys.any { it.id == SPACE_KEY_ID }
            val keys = row.keys.mapNotNull { key ->
                when {
                    key.isSpacer -> key
                    key.optional && key.id in customization.hiddenOptionalKeyIds -> {
                        when {
                            canAbsorbIntoSpace -> {
                                spaceWeightBonus += key.weight
                                null
                            }
                            key.preserveSpaceWhenHidden -> key.asSpacer()
                            else -> null
                        }
                    }
                    else -> customizeKey(key, customization)
                }
            }
            val adjustedKeys = keys.map { key ->
                if (key.id == SPACE_KEY_ID && spaceWeightBonus > 0f) {
                    key.copy(weight = key.weight + spaceWeightBonus)
                } else {
                    key
                }
            }
            adjustedKeys.takeIf { nextKeys -> nextKeys.any { !it.isSpacer } }?.let { row.copy(keys = it) }
        }
        return layout.copy(rows = rows)
    }

    private fun customizeKey(key: KeySpec, customization: CustomizationState): KeySpec {
        val remapped = if (key.id in customization.slotActions) remap(key, customization) else key
        val escAdjusted = if (remapped.id == "esc" && customization.escTouchMode == EscTouchMode.LONG_TAP) {
            remapped.copy(
                action = KeyAction(KeyActionType.NO_OP),
                longPressAction = KeyAction(KeyActionType.ESCAPE),
            )
        } else {
            remapped
        }
        return applyDisplayOverride(escAdjusted, customization)
    }

    private fun remap(key: KeySpec, customization: CustomizationState): KeySpec {
        val action = customization.slotActions.getValue(key.id)
        return key.copy(
            label = action.displayLabel(),
            action = action,
            icon = defaultIcon(action),
            secondaryLabel = null,
            secondaryIcon = null,
            swipeUpAction = null,
            longPressAction = null,
        )
    }

    private fun defaultIcon(action: KeyAction): KeyIcon? {
        return when (action.type) {
            KeyActionType.SPACE -> KeyIcon.SPACE_BAR
            KeyActionType.DELETE -> KeyIcon.BACKSPACE
            KeyActionType.SHIFT -> KeyIcon.SHIFT
            KeyActionType.SWITCH_SYMBOLS -> KeyIcon.SYMBOLS
            KeyActionType.SWITCH_EMOJI -> KeyIcon.EMOJI
            KeyActionType.SETTINGS -> KeyIcon.GEAR
            KeyActionType.MICROPHONE,
            KeyActionType.TOGGLE_SPEECH_INPUT -> KeyIcon.MIC
            KeyActionType.TOGGLE_GESTURE_TYPING -> KeyIcon.SWIPE
            KeyActionType.TOGGLE_BLUETOOTH_REMOTE,
            KeyActionType.BLUETOOTH_DEVICE_NEXT -> KeyIcon.BLUETOOTH
            KeyActionType.TOGGLE_BLUETOOTH_TRACKPAD -> KeyIcon.TRACKPAD
            KeyActionType.NUMPAD_TOGGLE -> KeyIcon.NUMPAD
            KeyActionType.ENTER -> KeyIcon.ENTER
            KeyActionType.TAB -> KeyIcon.TAB
            KeyActionType.ESCAPE -> KeyIcon.ESC
            KeyActionType.CTRL -> KeyIcon.CTRL
            KeyActionType.ALT -> KeyIcon.ALT
            KeyActionType.FN_MODIFIER,
            KeyActionType.SWITCH_FN -> KeyIcon.FN
            KeyActionType.ARROW_LEFT -> KeyIcon.ARROW_LEFT
            KeyActionType.ARROW_RIGHT -> KeyIcon.ARROW_RIGHT
            KeyActionType.ARROW_UP -> KeyIcon.ARROW_UP
            KeyActionType.ARROW_DOWN -> KeyIcon.ARROW_DOWN
            KeyActionType.KEY_EVENT -> when (action.keyCode) {
                KeyEvent.KEYCODE_FORWARD_DEL -> KeyIcon.FORWARD_DELETE
                else -> null
            }
            else -> null
        }
    }

    private fun applyDisplayOverride(key: KeySpec, customization: CustomizationState): KeySpec {
        val override = customization.keyDisplayOverrides[key.id] ?: return key
        return key.copy(
            label = override.label?.takeIf { it.isNotBlank() } ?: key.label,
            icon = override.icon,
        )
    }

    private companion object {
        const val SPACE_KEY_ID = "space"
    }
}
