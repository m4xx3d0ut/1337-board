package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTypingEngineTest {
    private val words = listOf(
        "test",
        "the",
        "hello",
        "word",
        "world",
        "keyboard",
        "custom",
    )

    @Test
    fun decodesCollapsedLetterPathToBestWord() {
        val engine = GestureTypingEngine(TextContextPolicy()) { words }

        assertEquals("hello", engine.decode(listOf("h", "e", "l", "o")))
        assertEquals("test", engine.decode(listOf("t", "e", "s", "t")))
        assertEquals("word", engine.decode(listOf("w", "o", "r", "d")))
    }

    @Test
    fun decodesRepeatedLetterPath() {
        val engine = GestureTypingEngine(TextContextPolicy()) { words }

        assertEquals("hello", engine.decode(listOf("h", "e", "l", "l", "o")))
    }

    @Test
    fun decodesPathsWithIntermediateCrossedKeys() {
        val engine = GestureTypingEngine(TextContextPolicy()) { words }

        assertEquals("test", engine.decode(listOf("t", "r", "e", "w", "s", "d", "t")))
        assertEquals("the", engine.decode(listOf("t", "y", "h", "g", "e")))
    }

    @Test
    fun geometryScoringFavorsPhysicallyNearCandidates() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("tost", "test")
        }

        assertEquals("test", engine.decode(listOf("t", "r", "s", "t")))
    }

    @Test
    fun touchTraceCanCorrectMisleadingCrossedKeyPath() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("tost", "test")
        }
        val trace = GlideTouchTrace(
            points = listOf(
                GlidePoint(4f, 0f),
                GlidePoint(2f, 0f),
                GlidePoint(1.5f, 1f),
                GlidePoint(4f, 0f),
            ),
            keyCenters = mapOf(
                't' to GlidePoint(4f, 0f),
                'o' to GlidePoint(8f, 0f),
                'e' to GlidePoint(2f, 0f),
                's' to GlidePoint(1.5f, 1f),
            ),
        )

        assertEquals(
            "test",
            engine.decode(
                pathLabels = listOf("t", "o", "s", "t"),
                options = GlideTypingOptions(),
                touchTrace = trace,
            ),
        )
    }

    @Test
    fun importedWordsWinTiesByProviderOrder() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("helo", "hello")
        }

        assertEquals("helo", engine.decode(listOf("h", "e", "l", "o")))
    }

    @Test
    fun rejectedCandidateIsSkippedForSamePath() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("then", "than")
        }
        val path = listOf("t", "h", "e", "n")

        assertEquals("then", engine.decode(path))
        engine.rejectCandidate(path, "then")

        assertEquals("than", engine.decode(path))
    }

    @Test
    fun candidatesExposeRankedAlternates() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("then", "than", "thin")
        }

        val candidates = engine.candidates(listOf("t", "h", "n"), limit = 3).map { it.word }

        assertTrue("then" in candidates)
        assertTrue("than" in candidates)
    }

    @Test
    fun localCorrectionWinsForExactPath() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "world")
        }

        engine.setCorrections(mapOf("wrd" to "ward"))

        assertEquals("ward", engine.decode(listOf("w", "r", "d")))
        assertEquals("world", engine.decode(listOf("w", "o", "r", "l", "d")))
    }

    @Test
    fun localCorrectionCannotBypassStrictEndpoints() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("test")
        }

        engine.setCorrections(mapOf("tresdt" to "rest"))

        assertEquals("test", engine.decode(listOf("t", "r", "e", "s", "d", "t")))
    }

    @Test
    fun localCorrectionCanBypassEndpointsWhenStrictEndpointsAreDisabled() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("test")
        }
        val options = GlideTypingOptions(strictFirstLastLetter = false)

        engine.setCorrections(mapOf("tresdt" to "rest"))

        assertEquals("rest", engine.decode(listOf("t", "r", "e", "s", "d", "t"), options))
    }

    @Test
    fun rejectedLocalCorrectionFallsBackToDictionaryCandidate() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word")
        }
        val path = listOf("w", "r", "d")

        engine.setCorrections(mapOf("wrd" to "ward"))
        engine.rejectCandidate(path, "ward")

        assertEquals("word", engine.decode(path))
    }

    @Test
    fun localCorrectionIsFirstSuggestion() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "world")
        }

        engine.setCorrections(mapOf("wrd" to "ward"))

        val candidates = engine.candidates(listOf("w", "r", "d"))

        assertEquals("ward", candidates.first().word)
        assertEquals(GlideCandidateSource.LOCAL_CORRECTION, candidates.first().source)
    }

    @Test
    fun pathSignatureNormalizesLabelsForCorrectionStorage() {
        val engine = GestureTypingEngine(TextContextPolicy()) { words }

        assertEquals("test", engine.pathSignature(listOf("T", "e", "e", "s", "t", "1")))
        assertNull(engine.pathSignature(listOf("t")))
    }

    @Test
    fun rejectsTooShortAndFallsBackToCollapsedUnknownPath() {
        val engine = GestureTypingEngine(TextContextPolicy()) { words }

        assertNull(engine.decode(listOf("t")))
        assertEquals("xyz", engine.decode(listOf("x", "y", "z")))
        assertNull(engine.decode(listOf("x", "c", "v", "b", "n", "m")))
    }

    @Test
    fun loosePathToleranceCanRelaxEndpointMatching() {
        val engine = GestureTypingEngine(TextContextPolicy()) { listOf("test") }
        val path = listOf("t", "e", "s", "r")
        val looseOptions = GlideTypingOptions(
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
            rawPathFallbackMode = GlideRawFallbackMode.OFF,
        )

        assertNull(engine.decode(path, GlideTypingOptions(rawPathFallbackMode = GlideRawFallbackMode.OFF)))
        assertEquals("test", engine.decode(path, looseOptions))
    }

    @Test
    fun rawPathFallbackCanBeDisabledOrExpanded() {
        val engine = GestureTypingEngine(TextContextPolicy()) { emptyList() }
        val path = listOf("x", "c", "v", "b", "n", "m")

        assertNull(engine.decode(path, GlideTypingOptions(rawPathFallbackMode = GlideRawFallbackMode.OFF)))
        assertEquals("xcvbnm", engine.decode(path, GlideTypingOptions(rawPathFallbackMode = GlideRawFallbackMode.ALWAYS)))
    }
}
