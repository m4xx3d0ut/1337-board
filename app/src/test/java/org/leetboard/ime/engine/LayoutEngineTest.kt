package org.leetboard.ime.engine

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}

