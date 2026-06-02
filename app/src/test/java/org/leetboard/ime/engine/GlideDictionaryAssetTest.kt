package org.leetboard.ime.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideDictionaryAssetTest {
    @Test
    fun bundledDictionaryIncludesExpandedCommonWords() {
        val words = File("src/main/assets/glide_words_en.txt").readLines()

        assertEquals(25_000, words.size)
        assertTrue("keyboard" in words)
    }

    @Test
    fun bundledDictionaryDecodesPad3SmokeWords() {
        val words = File("src/main/assets/glide_words_en.txt").readLines()
        val engine = GestureTypingEngine(TextContextPolicy()) { words }
        val options = GlideTypingOptions(strictFirstLastLetter = false)

        assertEquals("test", engine.decode(listOf("t", "e", "s", "t"), options))
        assertEquals("you", engine.decode(listOf("y", "u", "i", "o", "u"), options, touchTrace = timedTrace("yuiou")))
        assertEquals("are", engine.decode(listOf("a", "s", "e", "r", "t", "r", "e"), options))
        assertEquals("word", engine.decode(listOf("w", "o", "r", "d"), options))
        assertEquals("keyboard", engine.decode(listOf("k", "e", "y", "b", "o", "a", "r", "d"), options))
    }

    @Test
    fun bundledDictionaryPrefersTheOverThreeForCrossedRPath() {
        val words = File("src/main/assets/glide_words_en.txt").readLines()
        val engine = GestureTypingEngine(TextContextPolicy()) { words }
        val options = GlideTypingOptions(strictFirstLastLetter = false)

        assertEquals("the", engine.decode(listOf("t", "h", "r", "e"), options))
        assertEquals("the", engine.decode(listOf("t", "y", "h", "t", "r", "e"), options))
    }

    @Test
    fun timedDwellCanSelectWeirdFromBundledDictionary() {
        val words = File("src/main/assets/glide_words_en.txt").readLines()
        val engine = GestureTypingEngine(TextContextPolicy()) { words }
        val options = GlideTypingOptions(
            preferShorterWords = true,
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
            dwellSensitivity = GlideDwellSensitivity.HIGH,
        )

        assertEquals(
            "weird",
            engine.decode(
                listOf("w", "e", "r", "t", "y", "u", "i", "u", "y", "t", "r", "d"),
                options,
                touchTrace = timedTrace("wertyuiuytrd", mapOf('i' to 150L)),
            ),
        )
    }

    @Test
    fun dwellProtectsTestingFromShortAnchoredSuffixWords() {
        val words = File("src/main/assets/glide_words_en.txt").readLines()
        val engine = GestureTypingEngine(TextContextPolicy()) { words }
        val options = GlideTypingOptions(
            strictFirstLastLetter = false,
            pathTolerance = GlidePathTolerance.LOOSE,
            dwellSensitivity = GlideDwellSensitivity.STANDARD,
        )
        val path = "tresdrtyuijnbhg".map(Char::toString)
        val trace = dwellWeightedTrace(
            path = "tresdrtyuijnbhg",
            dwellWeights = mapOf(
                't' to 1f,
                'i' to 0.78f,
                'g' to 0.68f,
                'e' to 0.52f,
                's' to 0.45f,
                'n' to 0.44f,
                'r' to 0.15f,
                'u' to 0.11f,
            ),
        )

        assertEquals("testing", engine.decode(path, options, touchTrace = trace))
    }

    private fun timedTrace(
        path: String,
        pauseAfterKey: Map<Char, Long> = emptyMap(),
    ): GlideTouchTrace {
        var timeMs = 0L
        val points = path.mapNotNull { char ->
            val center = keyCenters[char] ?: return@mapNotNull null
            timeMs += pauseAfterKey[char] ?: 16L
            center.copy(timeMs = timeMs)
        }
        return GlideTouchTrace(
            points = points,
            keyCenters = keyCenters,
        )
    }

    private fun dwellWeightedTrace(
        path: String,
        dwellWeights: Map<Char, Float>,
    ): GlideTouchTrace {
        val points = path.mapNotNull { char -> keyCenters[char] }
        return GlideTouchTrace(
            points = points,
            keyCenters = keyCenters,
            keyDwellWeights = dwellWeights,
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
