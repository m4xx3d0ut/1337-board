package org.leetboard.ime.engine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.leetboard.ime.model.KeyActionType
import org.leetboard.ime.prefs.HardwareCompanionPreset

class HardwareCompanionLayoutEngineTest {
    private val engine = HardwareCompanionLayoutEngine()

    @Test
    fun agentPbxLayoutExposesSpecialNavigationAndFunctionRows() {
        val rows = engine.layout(HardwareCompanionPreset.AGENT_PBX).rows
        val specialKeys = rows[0].keys
        val functionKeys = rows[1].keys

        assertEquals(2, rows.size)
        assertTrue(specialKeys.any { key -> key.action.type == KeyActionType.ARROW_LEFT })
        assertTrue(specialKeys.any { key -> key.action.type == KeyActionType.CTRL })
        assertTrue(specialKeys.none { key -> key.action.type == KeyActionType.SWITCH_FN })
        assertEquals(12, functionKeys.size)
    }

    @Test
    fun functionRowContainsF1ThroughF12InOrder() {
        val keys = engine.layout(HardwareCompanionPreset.AGENT_PBX).rows[1].keys
        val functionCodes = keys.mapNotNull { key -> key.action.keyCode }
            .filter { keyCode -> keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 }

        assertEquals((KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).toList(), functionCodes)
    }

    @Test
    fun minimalPresetOmitsInsertDeleteAndQuickFunctionKeys() {
        val keys = engine.layout(HardwareCompanionPreset.MINIMAL).rows[0].keys

        assertTrue(keys.none { key -> key.action.keyCode in setOf(KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_FORWARD_DEL) })
        assertTrue(keys.none { key -> key.action.type in setOf(KeyActionType.CTRL, KeyActionType.ALT, KeyActionType.SHIFT) })
    }

    private fun org.leetboard.ime.model.KeySpec.keyCode(): Int? = action.keyCode
}
