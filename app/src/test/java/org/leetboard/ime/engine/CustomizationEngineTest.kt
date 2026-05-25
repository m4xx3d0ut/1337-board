package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.model.CustomizationState
import org.leetboard.ime.model.EscTouchMode
import org.leetboard.ime.model.KeyAction
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyDisplayOverride
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeySpec
import org.leetboard.ime.model.KeyboardLayout

class CustomizationEngineTest {
    private val engine = CustomizationEngine()

    @Test
    fun hiddenOptionalKeyCanPreserveItsLayoutSlot() {
        val layout = KeyboardLayout(
            id = "test",
            name = "Test",
            rows = listOf(
                KeyRow(
                    keys = listOf(
                        actionKey("tab", KeyActionType.TAB, preserveSpaceWhenHidden = true),
                        textKey("q"),
                    ),
                    layoutWeight = 2f,
                ),
            ),
        )

        val customized = engine.apply(layout, CustomizationState(hiddenOptionalKeyIds = setOf("tab")))
        val keys = customized.rows.single().keys

        assertTrue(keys.first().isSpacer)
        assertEquals("key_q", keys.last().id)
        assertEquals(2f, customized.rows.single().layoutWeight ?: 0f, 0.001f)
    }

    @Test
    fun hiddenOptionalKeyWithoutPreservedSpaceIsRemoved() {
        val layout = KeyboardLayout(
            id = "test",
            name = "Test",
            rows = listOf(
                KeyRow(
                    keys = listOf(
                        actionKey("settings", KeyActionType.SETTINGS),
                        textKey("q"),
                    ),
                ),
            ),
        )

        val customized = engine.apply(layout, CustomizationState(hiddenOptionalKeyIds = setOf("settings")))

        assertEquals(listOf("key_q"), customized.rows.single().keys.map { it.id })
    }

    @Test
    fun slotActionRemapUpdatesLabelAndAction() {
        val layout = KeyboardLayout(
            id = "test",
            name = "Test",
            rows = listOf(KeyRow(keys = listOf(actionKey("settings", KeyActionType.SETTINGS)))),
        )

        val customized = engine.apply(
            layout,
            CustomizationState(slotActions = mapOf("settings" to KeyAction(KeyActionType.MICROPHONE))),
        )
        val key = customized.rows.single().keys.single()

        assertEquals(KeyActionType.MICROPHONE, key.action.type)
        assertEquals("Mic", key.label)
    }

    @Test
    fun longTapEscModeMovesEscToLongPress() {
        val layout = KeyboardLayout(
            id = "test",
            name = "Test",
            rows = listOf(KeyRow(keys = listOf(actionKey("esc", KeyActionType.ESCAPE)))),
        )

        val customized = engine.apply(layout, CustomizationState(escTouchMode = EscTouchMode.LONG_TAP))
        val key = customized.rows.single().keys.single()

        assertEquals(KeyActionType.NO_OP, key.action.type)
        assertEquals(KeyActionType.ESCAPE, key.longPressAction?.type)
    }

    @Test
    fun displayOverrideUpdatesLabelAndIcon() {
        val layout = KeyboardLayout(
            id = "test",
            name = "Test",
            rows = listOf(KeyRow(keys = listOf(actionKey("settings", KeyActionType.SETTINGS)))),
        )

        val customized = engine.apply(
            layout,
            CustomizationState(
                keyDisplayOverrides = mapOf("settings" to KeyDisplayOverride(label = "Prefs", icon = KeyIcon.GEAR)),
            ),
        )
        val key = customized.rows.single().keys.single()

        assertEquals("Prefs", key.label)
        assertEquals(KeyIcon.GEAR, key.icon)
    }

    @Test
    fun hiddenBottomRowKeysExpandSpacebarAndKeepArrowColumnsStable() {
        val layout = LayoutEngine().layoutFor(
            state = org.leetboard.ime.model.KeyboardState(),
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE,
        )
        val baselineBottomRow = layout.rows.last()
        val baselineSpaceWeight = baselineBottomRow.keys.first { it.id == "space" }.weight
        val baselineLeftCenter = baselineBottomRow.centerOf("left")
        val baselineDownCenter = baselineBottomRow.centerOf("down")
        val baselineRightCenter = baselineBottomRow.centerOf("right")

        val customized = engine.apply(
            layout,
            CustomizationState(hiddenOptionalKeyIds = setOf("settings", "mic", "num_toggle")),
        )
        val bottomRow = customized.rows.last()

        assertTrue(bottomRow.keys.none { it.id in setOf("settings", "mic", "num_toggle") })
        assertEquals(baselineSpaceWeight + 3f, bottomRow.keys.first { it.id == "space" }.weight, 0.001f)
        assertEquals(baselineLeftCenter, bottomRow.centerOf("left"), 0.001f)
        assertEquals(baselineDownCenter, bottomRow.centerOf("down"), 0.001f)
        assertEquals(baselineRightCenter, bottomRow.centerOf("right"), 0.001f)
    }

    private fun actionKey(
        id: String,
        type: KeyActionType,
        preserveSpaceWhenHidden: Boolean = false,
    ): KeySpec {
        return KeySpec(
            id = id,
            label = id,
            action = KeyAction(type),
            optional = true,
            preserveSpaceWhenHidden = preserveSpaceWhenHidden,
        )
    }

    private fun textKey(label: String): KeySpec {
        return KeySpec(
            id = "key_$label",
            label = label,
            action = KeyAction.text(label),
        )
    }

    private fun KeyRow.centerOf(keyId: String): Float {
        val occupiedWeight = startInsetWeight + endInsetWeight + keys.sumOf { it.weight.toDouble() }.toFloat()
        val effectiveWeight = layoutWeight?.coerceAtLeast(occupiedWeight) ?: occupiedWeight
        var cursor = startInsetWeight
        keys.forEach { key ->
            if (key.id == keyId) {
                return (cursor + key.weight / 2f) / effectiveWeight
            }
            cursor += key.weight
        }
        error("Missing key $keyId")
    }
}
