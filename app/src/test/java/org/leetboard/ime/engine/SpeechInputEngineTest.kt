package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechInputEngineTest {
    @Test
    fun bestSpeechRecognitionTextSkipsBlankFinalCandidate() {
        val text = bestSpeechRecognitionText(
            candidates = listOf("", "testing", "test"),
            partialFallback = "",
        )

        assertEquals("testing", text)
    }

    @Test
    fun bestSpeechRecognitionTextUsesPartialFallback() {
        val text = bestSpeechRecognitionText(
            candidates = listOf("", " "),
            partialFallback = "test word",
        )

        assertEquals("test word", text)
    }
}
