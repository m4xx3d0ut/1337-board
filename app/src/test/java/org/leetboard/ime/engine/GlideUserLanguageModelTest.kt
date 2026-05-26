package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideUserLanguageModelTest {
    @Test
    fun recordsWordAndBigramBoosts() {
        val model = GlideUserLanguageModel(clock = { 100L })

        repeat(3) {
            model.recordAcceptedWord("morning", "good")
        }

        assertTrue(model.wordBoost("morning") > 0)
        assertTrue(model.bigramBoost("good", "morning") > model.wordBoost("morning"))
    }

    @Test
    fun serializesAndLoadsCounts() {
        val model = GlideUserLanguageModel(clock = { 200L })
        model.recordAcceptedWord("you", "thank")
        model.recordAcceptedWord("you", "thank")

        val restored = GlideUserLanguageModel()
        restored.loadSerialized(model.serialize())

        assertEquals(model.wordBoost("you"), restored.wordBoost("you"))
        assertEquals(model.bigramBoost("thank", "you"), restored.bigramBoost("thank", "you"))
    }

    @Test
    fun clearDropsLearnedBoosts() {
        val model = GlideUserLanguageModel(clock = { 300L })
        model.recordAcceptedWord("keyboard", null)

        model.clear()

        assertEquals(0, model.wordBoost("keyboard"))
    }
}
