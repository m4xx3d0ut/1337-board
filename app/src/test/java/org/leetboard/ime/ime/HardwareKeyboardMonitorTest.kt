package org.leetboard.ime.ime

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyboardMonitorTest {
    @Test
    fun `no configured keyboard is inactive even when a device remains registered`() {
        assertFalse(
            isHardwareKeyboardConfigurationActive(
                Configuration.KEYBOARD_NOKEYS,
                Configuration.HARDKEYBOARDHIDDEN_NO,
            ),
        )
    }

    @Test
    fun `hidden hardware keyboard is inactive`() {
        assertFalse(
            isHardwareKeyboardConfigurationActive(
                Configuration.KEYBOARD_QWERTY,
                Configuration.HARDKEYBOARDHIDDEN_YES,
            ),
        )
    }

    @Test
    fun `visible qwerty hardware keyboard is active`() {
        assertTrue(
            isHardwareKeyboardConfigurationActive(
                Configuration.KEYBOARD_QWERTY,
                Configuration.HARDKEYBOARDHIDDEN_NO,
            ),
        )
    }
}
