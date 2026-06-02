package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedPredictionEngineTest {
    @Test
    fun suggestionsPreferPrefixMatchesAndUserContext() {
        val model = GlideUserLanguageModel()
        val engine = TypedPredictionEngine(
            wordsProvider = { listOf("testing", "terminal", "tomorrow") },
            userLanguageModel = model,
        )
        repeat(3) {
            model.recordAcceptedWord("terminal", "open")
        }

        val suggestions = engine.suggestions(
            token = "te",
            context = GlidePredictionContext(textBeforeCursor = "open "),
            limit = 3,
        )

        assertEquals("terminal", suggestions.first())
    }

    @Test
    fun autocorrectRequiresClearCandidate() {
        val engine = TypedPredictionEngine(
            wordsProvider = { listOf("the", "then", "there") },
            userLanguageModel = GlideUserLanguageModel(),
        )

        assertEquals("the", engine.autocorrect("teh", GlidePredictionContext()))
        assertNull(engine.autocorrect("th", GlidePredictionContext()))
    }

    @Test
    fun autocorrectDoesNotReplaceKnownWords() {
        val engine = TypedPredictionEngine(
            wordsProvider = { listOf("test", "text") },
            userLanguageModel = GlideUserLanguageModel(),
        )

        assertNull(engine.autocorrect("test", GlidePredictionContext()))
    }

    @Test
    fun suggestionsSupportApostropheWords() {
        val engine = TypedPredictionEngine(
            wordsProvider = { listOf("don't", "done") },
            userLanguageModel = GlideUserLanguageModel(),
        )

        assertTrue("don't" in engine.suggestions("don", GlidePredictionContext(), limit = 2))
        assertEquals("don't", engine.suggestions("don'", GlidePredictionContext(), limit = 2).first())
    }

    @Test
    fun autocorrectCanInsertMissingApostrophe() {
        val engine = TypedPredictionEngine(
            wordsProvider = { listOf("don't") },
            userLanguageModel = GlideUserLanguageModel(),
        )

        assertEquals("don't", engine.autocorrect("dont", GlidePredictionContext()))
    }
}
