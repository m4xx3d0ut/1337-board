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
}
