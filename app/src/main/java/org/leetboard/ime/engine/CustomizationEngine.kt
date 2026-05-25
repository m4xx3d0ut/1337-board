package org.leetboard.ime.engine

import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout
import org.leetboard.ime.model.displayLabel

class CustomizationEngine {
    fun apply(layout: KeyboardLayout, customization: CustomizationState): KeyboardLayout {
        val rows = layout.rows.mapNotNull { row ->
            val keys = row.keys.mapNotNull { key ->
                when {
                    key.optional && key.id in customization.hiddenOptionalKeyIds -> null
                    key.id in customization.slotActions -> remap(key, customization)
                    else -> key
                }
            }
            keys.takeIf { it.isNotEmpty() }?.let(::KeyRow)
        }
        return layout.copy(rows = rows)
    }

    private fun remap(key: KeySpec, customization: CustomizationState): KeySpec {
        val action = customization.slotActions.getValue(key.id)
        return key.copy(label = action.displayLabel(), action = action)
    }
}

