package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GlidePredictionEngineTest {
    @Test
    fun frequencyRankingCanLiftCommonWordsOverCloseGeometricMatches() {
        val engine = FrequencyContextGlidePredictionEngine()
        val candidates = listOf(
            GlideCandidate("obscure", score = 420, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 9000),
            GlideCandidate("test", score = 620, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 20),
        )

        val ranked = engine.rank(
            candidates = candidates,
            pathSignature = "test",
            context = GlidePredictionContext(),
            options = GlideTypingOptions(),
        )

        assertEquals("test", ranked.first().word)
    }

    @Test
    fun acceptedWordsGetSessionBoost() {
        val engine = FrequencyContextGlidePredictionEngine()
        val context = GlidePredictionContext(textBeforeCursor = "run ")
        repeat(3) {
            engine.recordAcceptedWord("tests", context)
        }
        val candidates = listOf(
            GlideCandidate("tents", score = 0, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 25),
            GlideCandidate("tests", score = 460, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 7000),
        )

        val ranked = engine.rank(
            candidates = candidates,
            pathSignature = "tests",
            context = context,
            options = GlideTypingOptions(),
        )

        assertEquals("tests", ranked.first().word)
    }

    @Test
    fun predictiveRankingCanBeDisabled() {
        val engine = FrequencyContextGlidePredictionEngine()
        val candidates = listOf(
            GlideCandidate("obscure", score = 420, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 9000),
            GlideCandidate("test", score = 620, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 20),
        )

        val ranked = engine.rank(
            candidates = candidates,
            pathSignature = "test",
            context = GlidePredictionContext(),
            options = GlideTypingOptions(predictiveRankingEnabled = false),
        )

        assertEquals("obscure", ranked.first().word)
    }

    @Test
    fun previousWordKeepsInternalApostrophes() {
        assertEquals(
            "don't",
            GlidePredictionContext(textBeforeCursor = "I don't, ").previousWord(),
        )
        assertEquals(
            "bar",
            GlidePredictionContext(textBeforeCursor = "foo 'bar").previousWord(),
        )
    }

    @Test
    fun commonShortWordBoostCanBeatLongerExactCrossedPath() {
        val engine = FrequencyContextGlidePredictionEngine()
        val candidates = listOf(
            GlideCandidate("three", score = 90, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 207),
            GlideCandidate("the", score = 1420, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 1),
        )

        val ranked = engine.rank(
            candidates = candidates,
            pathSignature = "thre",
            context = GlidePredictionContext(),
            options = GlideTypingOptions(),
        )

        assertEquals("the", ranked.first().word)
    }

    @Test
    fun commonShortWordBoostCoversPad3CrossedTheTrace() {
        val engine = FrequencyContextGlidePredictionEngine()
        val candidates = listOf(
            GlideCandidate("three", score = 2749, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 251),
            GlideCandidate("there", score = 4034, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 80),
            GlideCandidate("they're", score = 6548, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 27),
            GlideCandidate("the", score = 9240, source = GlideCandidateSource.BUNDLED_WORDLIST, priority = 45),
        )

        val ranked = engine.rank(
            candidates = candidates,
            pathSignature = "tyhtre",
            context = GlidePredictionContext(),
            options = GlideTypingOptions(),
        )

        assertEquals("the", ranked.first().word)
    }
}
