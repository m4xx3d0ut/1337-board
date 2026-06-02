package org.leetboard.ime.engine

import kotlin.math.ln

interface GlidePredictionEngine {
    fun rank(
        candidates: List<GlideCandidate>,
        pathSignature: String,
        context: GlidePredictionContext,
        options: GlideTypingOptions,
    ): List<GlideCandidate>

    fun recordAcceptedWord(word: String, context: GlidePredictionContext) = Unit

    fun rejectAcceptedWord(word: String, context: GlidePredictionContext) = Unit
}

data class GlidePredictionContext(
    val textBeforeCursor: CharSequence? = null,
) {
    fun previousWord(): String? {
        val text = textBeforeCursor?.toString()?.lowercase().orEmpty()
        var index = text.length - 1
        while (index >= 0 && !text[index].isPredictionWordLetter()) index--
        if (index < 0) return null
        val end = index + 1
        while (index >= 0) {
            val char = text[index]
            when {
                char.isPredictionWordLetter() -> index--
                char.isPredictionApostrophe() &&
                    index > 0 &&
                    index + 1 < end &&
                    text[index - 1].isPredictionWordLetter() &&
                    text[index + 1].isPredictionWordLetter() -> index--
                else -> break
            }
        }
        return normalizeWord(text.substring(index + 1, end))
    }
}

object NoOpGlidePredictionEngine : GlidePredictionEngine {
    override fun rank(
        candidates: List<GlideCandidate>,
        pathSignature: String,
        context: GlidePredictionContext,
        options: GlideTypingOptions,
    ): List<GlideCandidate> = candidates
}

class FrequencyContextGlidePredictionEngine(
    private val userLanguageModel: GlideUserLanguageModel = GlideUserLanguageModel(),
) : GlidePredictionEngine {

    override fun rank(
        candidates: List<GlideCandidate>,
        pathSignature: String,
        context: GlidePredictionContext,
        options: GlideTypingOptions,
    ): List<GlideCandidate> {
        if (!options.predictiveRankingEnabled || candidates.size <= 1) return candidates
        val previousWord = context.previousWord()
        return candidates.sortedWith(
            compareBy<GlideCandidate> { candidate ->
                predictiveScore(candidate, pathSignature, previousWord, options)
            }
                .thenBy { it.score }
                .thenBy { it.word.length }
                .thenBy { it.word },
        )
    }

    override fun recordAcceptedWord(word: String, context: GlidePredictionContext) {
        val normalizedWord = normalizeWord(word) ?: return
        userLanguageModel.recordAcceptedWord(normalizedWord, context.previousWord())
    }

    override fun rejectAcceptedWord(word: String, context: GlidePredictionContext) {
        val normalizedWord = normalizeWord(word) ?: return
        userLanguageModel.rejectAcceptedWord(normalizedWord, context.previousWord())
    }

    private fun predictiveScore(
        candidate: GlideCandidate,
        pathSignature: String,
        previousWord: String?,
        options: GlideTypingOptions,
    ): Int {
        if (candidate.source == GlideCandidateSource.LOCAL_CORRECTION) return candidate.score
        var score = candidate.score
        score += frequencyPenalty(candidate.priority)
        score += endpointPenalty(candidate.word, pathSignature, options)
        score -= commonShortWordBoost(candidate.word, pathSignature, candidate.priority)
        score -= userLanguageModel.wordBoost(candidate.word)
        if (previousWord != null) {
            score -= userLanguageModel.bigramBoost(previousWord, candidate.word)
        }
        if (candidate.source == GlideCandidateSource.IMPORTED_WORDLIST) {
            score -= if (options.importedWordsPriority == GlideImportedWordsPriority.HIGH) {
                HIGH_IMPORT_BOOST
            } else {
                NORMAL_IMPORT_BOOST
            }
        }
        return score
    }

    private fun frequencyPenalty(priority: Int): Int {
        if (priority == Int.MAX_VALUE) return 0
        val rank = priority.coerceAtLeast(0) + 1
        return (ln(rank.toDouble()) * FREQUENCY_LOG_WEIGHT).toInt()
    }

    private fun endpointPenalty(word: String, pathSignature: String, options: GlideTypingOptions): Int {
        if (pathSignature.isEmpty() || options.strictFirstLastLetter) return 0
        val wordSignature = glideWordSignature(word) ?: return 0
        var penalty = 0
        if (wordSignature.firstOrNull() != pathSignature.first()) penalty += LOOSE_ENDPOINT_MISMATCH_PENALTY
        if (wordSignature.lastOrNull() != pathSignature.last()) penalty += LOOSE_ENDPOINT_MISMATCH_PENALTY
        return penalty
    }

    private fun commonShortWordBoost(word: String, pathSignature: String, priority: Int): Int {
        if (priority > COMMON_SHORT_WORD_PRIORITY_MAX) return 0
        val wordSignature = glideWordSignature(word) ?: return 0
        if (wordSignature.length !in 2..3 || pathSignature.length <= wordSignature.length) return 0
        if (wordSignature.firstOrNull() != pathSignature.firstOrNull()) return 0
        if (wordSignature.lastOrNull() != pathSignature.lastOrNull()) return 0
        val middleLetters = wordSignature.drop(1).dropLast(1)
        if (middleLetters.any { char -> char !in pathSignature }) return 0
        return (COMMON_SHORT_WORD_MAX_BOOST - priority * COMMON_SHORT_WORD_PRIORITY_DECAY)
            .coerceAtLeast(COMMON_SHORT_WORD_MIN_BOOST)
    }

    private companion object {
        const val FREQUENCY_LOG_WEIGHT = 90
        const val LOOSE_ENDPOINT_MISMATCH_PENALTY = 700
        const val NORMAL_IMPORT_BOOST = 250
        const val HIGH_IMPORT_BOOST = 650
        const val COMMON_SHORT_WORD_PRIORITY_MAX = 120
        const val COMMON_SHORT_WORD_MAX_BOOST = 7600
        const val COMMON_SHORT_WORD_MIN_BOOST = 1600
        const val COMMON_SHORT_WORD_PRIORITY_DECAY = 12
    }
}

private fun Char.isPredictionWordLetter(): Boolean {
    return this in 'a'..'z'
}

private fun Char.isPredictionApostrophe(): Boolean {
    return this == '\'' || this == '\u2019'
}
