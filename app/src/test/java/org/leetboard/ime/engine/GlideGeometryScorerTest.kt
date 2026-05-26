package org.leetboard.ime.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class GlideGeometryScorerTest {
    @Test
    fun exactPathHasLowerCostThanNoisyPath() {
        assertTrue(
            GlideGeometryScorer.cost("test", "test") <
                GlideGeometryScorer.cost("test", "tresdt"),
        )
    }

    @Test
    fun nearbyCrossedKeysBeatDistantAlternates() {
        assertTrue(
            GlideGeometryScorer.cost("test", "trst") <
                GlideGeometryScorer.cost("tost", "trst"),
        )
    }

    @Test
    fun touchTraceCostFavorsActualFingerPath() {
        val trace = traceFor("test")

        assertTrue(
            GlideGeometryScorer.touchCost("test", trace) <
                GlideGeometryScorer.touchCost("tost", trace),
        )
    }

    private fun traceFor(word: String): GlideTouchTrace {
        return GlideTouchTrace(
            points = word.mapNotNull { keyCenters[it] },
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
