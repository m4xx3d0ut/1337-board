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
}

data class GlidePredictionContext(
    val textBeforeCursor: CharSequence? = null,
) {
    fun previousWord(): String? {
        val text = textBeforeCursor?.toString()?.lowercase().orEmpty()
        var index = text.length - 1
        while (index >= 0 && text[index] !in 'a'..'z') index--
        if (index < 0) return null
        val end = index + 1
        while (index >= 0 && text[index] in 'a'..'z') index--
        return text.substring(index + 1, end).takeIf { it.length >= 2 }
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
        var penalty = 0
        if (word.firstOrNull() != pathSignature.first()) penalty += LOOSE_ENDPOINT_MISMATCH_PENALTY
        if (word.lastOrNull() != pathSignature.last()) penalty += LOOSE_ENDPOINT_MISMATCH_PENALTY
        return penalty
    }

    private companion object {
        const val FREQUENCY_LOG_WEIGHT = 90
        const val LOOSE_ENDPOINT_MISMATCH_PENALTY = 700
        const val NORMAL_IMPORT_BOOST = 250
        const val HIGH_IMPORT_BOOST = 650
    }
}
