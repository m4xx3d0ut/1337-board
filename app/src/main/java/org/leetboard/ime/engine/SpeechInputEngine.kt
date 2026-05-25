package org.leetboard.ime.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat

class SpeechInputEngine(
    private val context: Context,
    private val textContextPolicy: TextContextPolicy,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(editorInfo: EditorInfo?): Boolean {
        return textContextPolicy.allowsSpeechInput(editorInfo)
    }

    fun start(
        editorInfo: EditorInfo?,
        onText: (String) -> Unit,
        onError: (String) -> Unit,
        onState: (SpeechInputEngineState) -> Unit = {},
    ): SpeechStartResult {
        if (!textContextPolicy.allowsSpeechInput(editorInfo)) {
            return SpeechStartResult.Error("Speech input is disabled for this field")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return SpeechStartResult.Error("Microphone permission is required")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return SpeechStartResult.FeatureNotInstalled
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onState(SpeechInputEngineState.LISTENING)
                }

                override fun onBeginningOfSpeech() {
                    onState(SpeechInputEngineState.LISTENING)
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onState(SpeechInputEngineState.PROCESSING)
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    recognizer?.destroy()
                    recognizer = null
                    onError(speechErrorMessage(error))
                }

                override fun onResults(results: Bundle?) {
                    recognizer?.destroy()
                    recognizer = null
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) {
                        onError("No speech recognized")
                    } else {
                        onText(text)
                    }
                }
            })
            speechRecognizer.startListening(speechIntent())
        }
        return SpeechStartResult.Started
    }

    fun stopListening(): Boolean {
        val activeRecognizer = recognizer ?: return false
        activeRecognizer.stopListening()
        return true
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun speechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private fun speechErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed"
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
            SpeechRecognizer.ERROR_NETWORK -> "Speech recognizer network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognizer network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Speech recognizer service error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            else -> "Speech recognizer error"
        }
    }
}

enum class SpeechInputEngineState {
    LISTENING,
    PROCESSING,
}

sealed interface SpeechStartResult {
    data object FeatureNotInstalled : SpeechStartResult
    data object Started : SpeechStartResult
    data class Error(val message: String) : SpeechStartResult
}
