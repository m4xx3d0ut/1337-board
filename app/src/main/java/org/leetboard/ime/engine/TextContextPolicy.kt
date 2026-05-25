package org.leetboard.ime.engine

import android.text.InputType
import android.view.inputmethod.EditorInfo

class TextContextPolicy {
    fun isSensitive(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false
        val inputType = editorInfo.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        return when {
            inputClass == InputType.TYPE_CLASS_TEXT && variation in passwordTextVariations -> true
            inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            else -> false
        }
    }

    fun allowsLearning(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return true
        return !isSensitive(editorInfo) &&
            editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    }

    fun allowsGestureTyping(editorInfo: EditorInfo?): Boolean = allowsLearning(editorInfo)

    fun allowsSpeechInput(editorInfo: EditorInfo?): Boolean = !isSensitive(editorInfo)

    private companion object {
        val passwordTextVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }
}

