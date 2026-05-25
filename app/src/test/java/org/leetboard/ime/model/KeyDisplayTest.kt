package org.leetboard.ime.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDisplayTest {
    @Test
    fun resolvedDisplayUppercasesAlphaLabelsWhenShiftIsActive() {
        val key = KeySpec(
            id = "key_a",
            label = "a",
            action = KeyAction.text("a"),
        )

        val display = key.resolvedDisplay(shiftActive = true, labelStyle = KeyLabelStyle())

        assertEquals("A", display.label)
    }

    @Test
    fun resolvedDisplayKeepsLabelsLowercaseWhenCapsDisplayDisabled() {
        val key = KeySpec(
            id = "key_a",
            label = "a",
            action = KeyAction.text("a"),
        )

        val display = key.resolvedDisplay(
            shiftActive = true,
            labelStyle = KeyLabelStyle(uppercaseOnShift = false),
        )

        assertEquals("a", display.label)
    }
}
