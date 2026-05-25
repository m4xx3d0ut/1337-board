package org.leetboard.ime.engine

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextContextPolicyTest {
    private val policy = TextContextPolicy()

    @Test
    fun passwordFieldsDisableGestureAndSpeech() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        assertTrue(policy.isSensitive(editorInfo))
        assertFalse(policy.allowsGestureTyping(editorInfo))
        assertFalse(policy.allowsSpeechInput(editorInfo))
    }

    @Test
    fun noPersonalizedLearningDisablesGestureOnly() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        assertFalse(policy.allowsLearning(editorInfo))
        assertFalse(policy.allowsGestureTyping(editorInfo))
        assertTrue(policy.allowsSpeechInput(editorInfo))
    }
}
