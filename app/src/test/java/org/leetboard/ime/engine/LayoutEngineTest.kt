package org.leetboard.ime.engine

import android.content.res.Configuration
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.model.KeyIcon
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.model.KeyRow
import org.leetboard.ime.model.KeyboardState

class LayoutEngineTest {
    private val engine = LayoutEngine()

    @Test
    fun qwertyFiveRowLayoutIncludesTerminalKeys() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val keyIds = layout.rows.flatMap { row -> row.keys.map { it.id } }.toSet()

        assertEquals("qwerty5", layout.id)
        assertTrue("esc" in keyIds)
        assertTrue("ctrl" in keyIds)
        assertTrue("alt" in keyIds)
        assertTrue("tab" in keyIds)
        assertTrue("fn" in keyIds)
        assertTrue("settings" in keyIds)
        assertTrue("num_toggle" in keyIds)
        assertTrue("mic" in keyIds)
        assertTrue("legacy swipe toggle key is absent", "swipe_toggle" !in keyIds)
    }

    @Test
    fun qwertyFiveRowUsesPhysicalKeyboardStylePositions() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val rows = layout.rows

        assertEquals(5, rows.size)
        assertEquals("key_`", rows[0].keys.first().id)
        assertEquals("delete", rows[0].keys.last().id)
        assertEquals(15f, rows[0].layoutWeight ?: 0f, 0.001f)
        assertEquals("tab", rows[1].keys.first().id)
        assertEquals("ctrl", rows[2].keys.first().id)
        assertEquals("enter", rows[2].keys.last().id)
        assertEquals("shift", rows[3].keys.first().id)
        assertEquals("shift_right", rows[3].keys.last().id)
        assertTrue(rows[3].keys.any { it.id == "up" })

        val bottomIds = rows[4].keys.map { it.id }
        assertTrue(bottomIds.indexOf("fn") < bottomIds.indexOf("space"))
        assertTrue(bottomIds.indexOf("settings") > bottomIds.indexOf("space"))
        assertTrue(bottomIds.indexOf("left") > bottomIds.indexOf("settings"))
        assertTrue(bottomIds.indexOf("down") > bottomIds.indexOf("left"))
        assertTrue(bottomIds.indexOf("right") > bottomIds.indexOf("down"))
    }

    @Test
    fun qwertyFiveRowShrinksAdjustableEdgeColumnsAndRedistributesMiddleKeys() {
        val layout = engine.layoutFor(KeyboardState(edgeKeyWidthScale = 0.75f), Configuration.ORIENTATION_PORTRAIT)
        val tabRow = layout.rows[1].keys
        val ctrlRow = layout.rows[2].keys
        val shiftRow = layout.rows[3].keys

        assertEquals(1.125f, tabRow.first().weight, 0.001f)
        assertEquals(1.125f, tabRow.last().weight, 0.001f)
        assertEquals(1.0625f, tabRow[1].weight, 0.001f)
        assertEquals(1.275f, ctrlRow.first().weight, 0.001f)
        assertEquals(1.725f, ctrlRow.last().weight, 0.001f)
        assertEquals(1.65f, shiftRow.first().weight, 0.001f)
        assertEquals(1.008f, shiftRow.last().weight, 0.001f)
        assertEquals(15f, tabRow.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
        assertEquals(15f, ctrlRow.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
        assertEquals(15f, shiftRow.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun upArrowIsAlignedNearDownArrowColumn() {
        val layout = engine.layoutFor(KeyboardState(edgeKeyWidthScale = 0.75f), Configuration.ORIENTATION_PORTRAIT)
        val upCenter = layout.rows[3].centerOf("up")
        val downCenter = layout.rows[4].centerOf("down")

        assertEquals(downCenter, upCenter, 0.015f)
    }

    @Test
    fun symbolKeyLongPressOpensSettings() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val symbolKey = layout.rows.flatMap { it.keys }.first { it.id == "symbols" }

        assertEquals(KeyActionType.SETTINGS, symbolKey.longPressAction?.type)
    }

    @Test
    fun landscapeNumpadStateSelectsNumpadLayout() {
        val layout = engine.layoutFor(
            state = KeyboardState(numpad = true),
            orientation = Configuration.ORIENTATION_LANDSCAPE,
        )

        assertEquals("landscape_numpad", layout.id)
    }

    @Test
    fun portraitNumpadStateSelectsNumpadLayout() {
        val layout = engine.layoutFor(
            state = KeyboardState(numpad = true),
            orientation = Configuration.ORIENTATION_PORTRAIT,
        )

        assertEquals("landscape_numpad", layout.id)
        assertTrue(layout.rows.flattenKeys().any { it.id == "home" })
        assertTrue(layout.rows.flattenKeys().any { it.id == "page_down" })
    }

    @Test
    fun fnStateSelectsFunctionLayer() {
        val layout = engine.layoutFor(KeyboardState(fn = true), Configuration.ORIENTATION_PORTRAIT)
        val keys = layout.rows.flattenKeys()

        assertEquals("function", layout.id)
        assertEquals("f1", keys.first().id)
        assertTrue(keys.any { it.id == "f12" })
        assertTrue(keys.any { it.id == "home" })
        assertTrue(keys.any { it.id == "num_toggle" })
        assertEquals(null, keys.first { it.id == "mic" }.swipeUpAction)
        assertEquals(KeyActionType.KEY_EVENT, keys.first { it.id == "f5" }.action.type)
        assertEquals(KeyEvent.KEYCODE_F5, keys.first { it.id == "f5" }.action.keyCode)
    }

    @Test
    fun fnLayerUpArrowAlignsOverDownArrow() {
        val layout = engine.layoutFor(KeyboardState(fn = true), Configuration.ORIENTATION_PORTRAIT)
        val upCenter = layout.rows[3].centerOf("up")
        val downCenter = layout.rows[4].centerOf("down")

        assertEquals(downCenter, upCenter, 0.001f)
    }

    @Test
    fun actionKeysExposeDefaultIconsAndSecondaryLegends() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val keys = layout.rows.flattenKeys()

        assertEquals(KeyIcon.GEAR, keys.first { it.id == "settings" }.icon)
        assertEquals(KeyIcon.SWIPE, keys.first { it.id == "settings" }.secondaryIcon)
        assertEquals(KeyActionType.TOGGLE_GESTURE_TYPING, keys.first { it.id == "settings" }.swipeUpAction?.type)
        assertEquals(KeyActionType.TOGGLE_GESTURE_TYPING, keys.first { it.id == "settings" }.longPressAction?.type)
        assertEquals(KeyIcon.SYMBOLS, keys.first { it.id == "symbols" }.icon)
        assertEquals(KeyIcon.SPACE_BAR, keys.first { it.id == "space" }.icon)
        assertEquals(KeyIcon.BACKSPACE, keys.first { it.id == "delete" }.icon)
        assertEquals(KeyIcon.FORWARD_DELETE, keys.first { it.id == "delete" }.secondaryIcon)
        assertEquals(KeyIcon.MIC, keys.first { it.id == "mic" }.icon)
        assertEquals(KeyActionType.MICROPHONE, keys.first { it.id == "mic" }.action.type)
        assertEquals(null, keys.first { it.id == "mic" }.secondaryIcon)
        assertEquals(null, keys.first { it.id == "mic" }.longPressAction)
        assertEquals(null, keys.first { it.id == "mic" }.swipeUpAction)
        assertEquals(null, keys.first { it.id == "delete" }.longPressAction)
        assertEquals(null, keys.first { it.id == "delete" }.swipeUpAction)
        assertEquals("!", keys.first { it.id == "key_1" }.secondaryLabel)
    }

    @Test
    fun arrowKeysExposeNavigationSecondaryActions() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val keys = layout.rows.flattenKeys()

        assertEquals("Home", keys.first { it.id == "left" }.secondaryLabel)
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, keys.first { it.id == "left" }.swipeUpAction?.keyCode)
        assertEquals("End", keys.first { it.id == "right" }.secondaryLabel)
        assertEquals(KeyEvent.KEYCODE_MOVE_END, keys.first { it.id == "right" }.swipeUpAction?.keyCode)
        assertEquals("PgUp", keys.first { it.id == "up" }.secondaryLabel)
        assertEquals(KeyEvent.KEYCODE_PAGE_UP, keys.first { it.id == "up" }.swipeUpAction?.keyCode)
        assertEquals("PgDn", keys.first { it.id == "down" }.secondaryLabel)
        assertEquals(KeyEvent.KEYCODE_PAGE_DOWN, keys.first { it.id == "down" }.swipeUpAction?.keyCode)
    }

    @Test
    fun layoutIdSelectsFourRowAndCompactLayouts() {
        val fourRow = engine.layoutFor(KeyboardState(activeLayoutId = "qwerty4"), Configuration.ORIENTATION_PORTRAIT)
        val compact = engine.layoutFor(KeyboardState(activeLayoutId = "compact5"), Configuration.ORIENTATION_PORTRAIT)

        assertEquals("qwerty4", fourRow.id)
        assertEquals("compact5", compact.id)
    }

    @Test
    fun deleteAndArrowKeysAreRepeatable() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val repeatableActions = layout.rows
            .flatMap { it.keys }
            .filter { it.repeatable }
            .map { it.action.type }
            .toSet()

        assertTrue(KeyActionType.DELETE in repeatableActions)
        assertTrue(KeyActionType.ARROW_LEFT in repeatableActions)
        assertTrue(KeyActionType.ARROW_RIGHT in repeatableActions)
        assertTrue(KeyActionType.ARROW_UP in repeatableActions)
        assertTrue(KeyActionType.ARROW_DOWN in repeatableActions)
    }

    @Test
    fun letterKeysCanExposeSwipeUpAlternates() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val cKey = layout.rows.flatMap { it.keys }.first { it.id == "key_c" }

        assertEquals("|", cKey.swipeUpAction?.text)
        assertEquals("|", cKey.longPressAction?.text)
    }

    @Test
    fun compactAndFourRowLayoutsKeepHomeRowOffset() {
        val fourRow = engine.layoutFor(KeyboardState(activeLayoutId = "qwerty4"), Configuration.ORIENTATION_PORTRAIT)
        val compact = engine.layoutFor(KeyboardState(activeLayoutId = "compact5"), Configuration.ORIENTATION_PORTRAIT)

        assertEquals(0.5f, fourRow.rows[1].startInsetWeight, 0.001f)
        assertEquals(0.5f, compact.rows[2].startInsetWeight, 0.001f)
    }

    private fun List<KeyRow>.flattenKeys() = flatMap { row -> row.keys }

    private fun KeyRow.centerOf(keyId: String): Float {
        val occupiedWeight = startInsetWeight + endInsetWeight + keys.sumOf { it.weight.toDouble() }.toFloat()
        val effectiveWeight = layoutWeight?.coerceAtLeast(occupiedWeight) ?: occupiedWeight
        val slackWeight = effectiveWeight - occupiedWeight
        val alignmentInsetWeight = when (alignment) {
            org.leetboard.ime.model.RowAlignment.START -> 0f
            org.leetboard.ime.model.RowAlignment.CENTER -> slackWeight / 2f
            org.leetboard.ime.model.RowAlignment.END -> slackWeight
        }
        var cursor = alignmentInsetWeight + startInsetWeight
        keys.forEach { key ->
            if (key.id == keyId) {
                return (cursor + key.weight / 2f) / effectiveWeight
            }
            cursor += key.weight
        }
        error("Missing key $keyId")
    }
}
