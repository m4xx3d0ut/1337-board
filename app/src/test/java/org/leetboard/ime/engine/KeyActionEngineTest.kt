package org.leetboard.ime.engine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyActionEngineTest {
    @Test
    fun metaTextKeyCodeMapsLettersAndDigits() {
        assertEquals(KeyEvent.KEYCODE_A, metaTextKeyCode('a'))
        assertEquals(KeyEvent.KEYCODE_Z, metaTextKeyCode('Z'))
        assertEquals(KeyEvent.KEYCODE_0, metaTextKeyCode('0'))
        assertEquals(KeyEvent.KEYCODE_1, metaTextKeyCode('1'))
        assertEquals(KeyEvent.KEYCODE_9, metaTextKeyCode('9'))
    }

    @Test
    fun metaTextKeyCodeIgnoresUnmappedPunctuation() {
        assertNull(metaTextKeyCode('-'))
    }
}
