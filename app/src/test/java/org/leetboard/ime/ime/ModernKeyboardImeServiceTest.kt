package org.leetboard.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.leetboard.ime.model.KeyAction

class ModernKeyboardImeServiceTest {
    @Test
    fun postPredictionPunctuationAddsTrailingSpaceAfterTrim() {
        assertEquals(
            KeyAction.text(". "),
            postPredictionPunctuationAction(KeyAction.text("."), ".", trimmedPendingSpace = true),
        )
    }

    @Test
    fun postPredictionPunctuationLeavesNormalPunctuationUnchanged() {
        val action = KeyAction.text(".")

        assertEquals(
            action,
            postPredictionPunctuationAction(action, ".", trimmedPendingSpace = false),
        )
    }
}
