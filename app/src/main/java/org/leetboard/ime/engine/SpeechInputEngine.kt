package org.leetboard.ime.engine

import android.view.inputmethod.EditorInfo

class SpeechInputEngine(
    private val textContextPolicy: TextContextPolicy,
) {
    fun isAvailable(editorInfo: EditorInfo?): Boolean {
        return textContextPolicy.allowsSpeechInput(editorInfo)
    }

    fun start(): SpeechStartResult {
        return SpeechStartResult.FeatureNotInstalled
    }
}

sealed interface SpeechStartResult {
    data object FeatureNotInstalled : SpeechStartResult
    data object Started : SpeechStartResult
    data class Error(val message: String) : SpeechStartResult
}

