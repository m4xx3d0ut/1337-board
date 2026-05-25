package org.leetboard.ime.prefs

import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyboardGeometry
import org.leetboard.ime.model.KeyboardTheme
import org.leetboard.ime.model.ThemePreset

data class KeyboardPreferences(
    val themePreset: ThemePreset = ThemePreset.LEET_GREEN,
    val portraitGeometry: KeyboardGeometry = KeyboardGeometry(),
    val landscapeGeometry: KeyboardGeometry = KeyboardTheme.leetGreen.landscape,
    val hiddenOptionalKeyIds: Set<String> = emptySet(),
    val slotActions: Map<String, KeyAction> = emptyMap(),
    val numpadToggleEnabled: Boolean = true,
    val gestureTypingEnabled: Boolean = false,
    val speechInputEnabled: Boolean = false,
) {
    fun customizationState(): CustomizationState {
        val hiddenKeys = buildSet {
            addAll(hiddenOptionalKeyIds)
            if (!numpadToggleEnabled) add("num_toggle")
            if (!speechInputEnabled) add("mic")
        }
        return CustomizationState(
            hiddenOptionalKeyIds = hiddenKeys,
            slotActions = slotActions,
        )
    }

    companion object {
        fun defaults() = KeyboardPreferences()
    }
}

enum class GeometryOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class GeometryField {
    KEY_RADIUS,
    BORDER_WIDTH,
    KEY_GAP,
    OUTER_MARGIN,
    ROW_GAP,
}

