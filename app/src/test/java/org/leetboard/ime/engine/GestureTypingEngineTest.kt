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
            listOf("word", "ward", "world")
        }

        engine.setCorrections(mapOf("wrd" to GlideCorrectionEntry(word = "ward", acceptedCount = 6)))

        assertEquals("ward", engine.decode(listOf("w", "r", "d")))
        assertEquals("world", engine.decode(listOf("w", "o", "r", "l", "d")))
    }

    @Test
    fun localCorrectionCanSupplyWordMissingFromDictionary() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "world")
        }

        engine.setCorrections(mapOf("wrd" to GlideCorrectionEntry(word = "ward", acceptedCount = 6)))

        val candidates = engine.candidates(listOf("w", "r", "d"))

        assertEquals("ward", candidates.first().word)
        assertEquals(GlideCandidateSource.LOCAL_CORRECTION, candidates.first().source)
    }

    @Test
    fun localCorrectionCannotBypassStrictEndpoints() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("test")
        }

        engine.setCorrections(mapOf("tresdt" to GlideCorrectionEntry(word = "rest", acceptedCount = 10)))

        assertEquals("test", engine.decode(listOf("t", "r", "e", "s", "d", "t")))
    }

    @Test
    fun softLocalCorrectionCannotForceUnrelatedCandidate() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("test")
        }
        val options = GlideTypingOptions(strictFirstLastLetter = false)

        engine.setCorrections(mapOf("tresdt" to GlideCorrectionEntry(word = "rest", acceptedCount = 10)))

        assertEquals("test", engine.decode(listOf("t", "r", "e", "s", "d", "t"), options))
    }

    @Test
    fun rejectedLocalCorrectionFallsBackToDictionaryCandidate() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("ward", "word")
        }
        val path = listOf("w", "r", "d")

        engine.setCorrections(mapOf("wrd" to GlideCorrectionEntry(word = "ward", acceptedCount = 6)))
        engine.rejectCandidate(path, "ward")

        assertEquals("word", engine.decode(path))
    }

    @Test
    fun localCorrectionIsFirstSuggestion() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "ward", "world")
        }

        engine.setCorrections(mapOf("wrd" to GlideCorrectionEntry(word = "ward", acceptedCount = 6)))

        val candidates = engine.candidates(listOf("w", "r", "d"))

        assertEquals("ward", candidates.first().word)
        assertEquals(GlideCandidateSource.LOCAL_CORRECTION, candidates.first().source)
    }

    @Test
    fun demotedLocalCorrectionIsIgnored() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "ward")
        }

        engine.setCorrections(
            mapOf("wrd" to GlideCorrectionEntry(word = "ward", acceptedCount = 1, rejectedCount = 4)),
        )

        assertTrue(engine.candidates(listOf("w", "r", "d")).none { it.source == GlideCandidateSource.LOCAL_CORRECTION })
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
        assertNull(engine.decode(listOf("x", "y", "z")))
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
    fun shortNoisyPathsPreferShorterCommonMatches() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("three", "the")
        }
        val options = GlideTypingOptions(strictFirstLastLetter = false)

        assertEquals("the", engine.decode(listOf("t", "h", "r"), options))
    }

    @Test
    fun shortAnchoredWordsBeatLongNoisyPathMatches() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf(
                "you",
                "your",
                "are",
                "our",
                "young",
                "hour",
                "word",
                "weird",
                "weir",
                "writ",
                "error",
                "serve",
                "ate",
                "assure",
                "average",
                "assert",
            )
        }
        val options = GlideTypingOptions(
            preferShorterWords = true,
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
        )

        assertEquals("are", engine.decode(listOf("a", "s", "e", "r", "t", "r", "e"), options))
        assertEquals("you", engine.decode(listOf("y", "u", "i", "o", "u"), options))
        assertEquals("word", engine.decode(listOf("w", "e", "r", "t", "y", "u", "i", "o", "i", "u", "y", "t", "r", "d"), options))
    }

    @Test
    fun dwellOnInteriorKeysCanLiftLongerIntendedWord() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("weed", "wed", "weird", "weir", "writ")
        }
        val options = GlideTypingOptions(
            preferShorterWords = true,
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
        )
        val path = listOf("w", "e", "r", "t", "y", "u", "i", "u", "y", "t", "r", "d")

        assertEquals("weed", engine.decode(path, options))
        assertEquals("weird", engine.decode(path, options, touchTrace = dwellTrace("weird")))
    }

    @Test
    fun timedDwellCanLiftIntendedInteriorLetters() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("word", "weird", "wired", "writ")
        }
        val options = GlideTypingOptions(
            preferShorterWords = true,
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
        )
        val path = listOf("w", "e", "r", "t", "y", "u", "i", "u", "y", "t", "r", "d")
        val trace = timedTrace(
            "wertyuiuytrd",
            mapOf('i' to 150L),
        )

        assertEquals("weird", engine.decode(path, options, touchTrace = trace))
    }

    @Test
    fun traceAnchorsProtectShortCommonWordsFromNearbyAlternates() {
        val engine = GestureTypingEngine(TextContextPolicy()) {
            listOf("you", "your", "young", "hour")
        }
        val options = GlideTypingOptions(
            preferShorterWords = true,
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
        )
        val path = listOf("y", "u", "i", "o", "u")
        val trace = timedTrace("yuiou")

        assertEquals("you", engine.decode(path, options, touchTrace = trace))
    }

    @Test
    fun rawPathFallbackCanBeDisabledOrExpanded() {
        val engine = GestureTypingEngine(TextContextPolicy()) { emptyList() }
        val path = listOf("x", "c", "v", "b", "n", "m")

        assertNull(engine.decode(path, GlideTypingOptions(rawPathFallbackMode = GlideRawFallbackMode.OFF)))
        assertEquals("xcvbnm", engine.decode(path, GlideTypingOptions(rawPathFallbackMode = GlideRawFallbackMode.ALWAYS)))
    }

    private fun dwellTrace(word: String): GlideTouchTrace {
        val dwellWeights = word.toSet().associateWith { 1f }
        val tracePoints = word.mapNotNull { keyCenters[it] }
        return GlideTouchTrace(
            points = tracePoints,
            keyCenters = keyCenters,
            keyDwellWeights = dwellWeights,
        )
    }

    private fun timedTrace(
        path: String,
        pauseAfterKey: Map<Char, Long> = emptyMap(),
    ): GlideTouchTrace {
        var timeMs = 0L
        val tracePoints = path.mapNotNull { char ->
            val center = keyCenters[char] ?: return@mapNotNull null
            timeMs += pauseAfterKey[char] ?: 16L
            center.copy(timeMs = timeMs)
        }
        return GlideTouchTrace(
            points = tracePoints,
            keyCenters = keyCenters,
        )
    }

    private val keyCenters = buildMap {
        "qwertyuiop".forEachIndexed { index, char ->
            put(char, GlidePoint(index.toFloat(), 0f))
        }
        "asdfghjkl".forEachIndexed { index, char ->
            put(char, GlidePoint(index + 0.5f, 1f))
        }
        "zxcvbnm".forEachIndexed { index, char ->
            put(char, GlidePoint(index + 1.25f, 2f))
        }
    }
}
