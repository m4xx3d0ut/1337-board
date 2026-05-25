package org.leetboard.ime.engine

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.model.KeyActionType
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
    }

    @Test
    fun letterKeysCanExposeSwipeUpAlternates() {
        val layout = engine.layoutFor(KeyboardState(), Configuration.ORIENTATION_PORTRAIT)
        val cKey = layout.rows.flatMap { it.keys }.first { it.id == "key_c" }

        assertEquals("|", cKey.swipeUpAction?.text)
        assertEquals("|", cKey.longPressAction?.text)
    }
}
