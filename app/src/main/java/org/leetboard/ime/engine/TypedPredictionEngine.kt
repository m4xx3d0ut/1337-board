package org.leetboard.ime.engine

import kotlin.math.min

class TypedPredictionEngine(
    private val wordsProvider: () -> List<String>,
    private val userLanguageModel: GlideUserLanguageModel,
) {
    private var cachedProviderWords: List<String>? = null
    private var cachedWords: List<String> = emptyList()

    fun suggestions(token: String, context: GlidePredictionContext, limit: Int): List<String> {
        val normalizedToken = normalizeWordPrefix(token) ?: return emptyList()
        if (normalizedToken.length < MIN_SUGGESTION_PREFIX_LENGTH) return emptyList()
        return rankedCandidates(normalizedToken, context, includeCorrections = normalizedToken.length >= MIN_CORRECTION_LENGTH)
            .asSequence()
            .filter { candidate -> candidate.word != normalizedToken }
            .map { candidate -> candidate.word }
            .distinct()
            .take(limit)
            .toList()
    }

    fun autocorrect(token: String, context: GlidePredictionContext): String? {
        val normalizedToken = normalizeWord(token) ?: return null
        if (normalizedToken.length < MIN_CORRECTION_LENGTH) return null
        if (normalizedWords().contains(normalizedToken)) return null
        val candidates = rankedCandidates(normalizedToken, context, includeCorrections = true)
            .filter { candidate -> candidate.word != normalizedToken && candidate.editDistance in 1..maximumCorrectionDistance(normalizedToken.length) }
        val best = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.drop(1).firstOrNull()
        if (runnerUp != null && runnerUp.score - best.score < AUTOCORRECT_RUNNER_UP_MARGIN) return null
        return best.word
    }

    fun recordAcceptedWord(word: String, context: GlidePredictionContext) {
        userLanguageModel.recordAcceptedWord(word, context.previousWord())
    }

    private fun rankedCandidates(
        token: String,
        context: GlidePredictionContext,
        includeCorrections: Boolean,
    ): List<TypedCandidate> {
        val previousWord = context.previousWord()
        return normalizedWords()
            .asSequence()
            .mapIndexedNotNull { index, word ->
                val editDistance = when {
                    word.startsWith(token) -> 0
                    includeCorrections -> levenshteinAtMost(token, word, maximumCorrectionDistance(token.length))
                    else -> null
                } ?: return@mapIndexedNotNull null
                TypedCandidate(
                    word = word,
                    editDistance = editDistance,
                    score = typedScore(word, token, editDistance, index, previousWord),
                )
            }
            .distinctBy { candidate -> candidate.word }
            .sortedWith(
                compareBy<TypedCandidate> { it.score }
                    .thenBy { it.editDistance }
                    .thenBy { it.word.length }
                    .thenBy { it.word },
            )
            .toList()
    }

    private fun typedScore(
        word: String,
        token: String,
        editDistance: Int,
        priority: Int,
        previousWord: String?,
    ): Int {
        var score = priority / PRIORITY_BUCKET_SIZE
        score += if (editDistance == 0) {
            (word.length - token.length).coerceAtLeast(0) * PREFIX_COMPLETION_WEIGHT
        } else {
            editDistance * EDIT_DISTANCE_WEIGHT + kotlin.math.abs(word.length - token.length) * LENGTH_DELTA_WEIGHT
        }
        score -= userLanguageModel.wordBoost(word)
        if (previousWord != null) score -= userLanguageModel.bigramBoost(previousWord, word)
        return score
    }

    private fun normalizedWords(): List<String> {
        val providerWords = wordsProvider()
        if (providerWords === cachedProviderWords) return cachedWords
        return providerWords.mapNotNull(::normalizeWord).also { words ->
            cachedProviderWords = providerWords
            cachedWords = words
        }
    }

    private data class TypedCandidate(
        val word: String,
        val editDistance: Int,
        val score: Int,
    )

    private companion object {
        const val MIN_SUGGESTION_PREFIX_LENGTH = 2
        const val MIN_CORRECTION_LENGTH = 3
        const val PRIORITY_BUCKET_SIZE = 40
        const val PREFIX_COMPLETION_WEIGHT = 20
        const val EDIT_DISTANCE_WEIGHT = 900
        const val LENGTH_DELTA_WEIGHT = 160
        const val AUTOCORRECT_RUNNER_UP_MARGIN = 260
    }
}

private fun maximumCorrectionDistance(length: Int): Int {
    return if (length >= 6) 2 else 1
}

private fun levenshteinAtMost(left: String, right: String, maximum: Int): Int? {
    adjacentTranspositionDistance(left, right)?.let { distance ->
        if (distance <= maximum) return distance
    }
    if (kotlin.math.abs(left.length - right.length) > maximum) return null
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftChar ->
        current[0] = leftIndex + 1
        var rowMinimum = current[0]
        right.forEachIndexed { rightIndex, rightChar ->
            val substitution = if (leftChar == rightChar) 0 else 1
            current[rightIndex + 1] = min(
                min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                previous[rightIndex] + substitution,
            )
            rowMinimum = min(rowMinimum, current[rightIndex + 1])
        }
        if (rowMinimum > maximum) return null
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length].takeIf { distance -> distance <= maximum }
}

private fun adjacentTranspositionDistance(left: String, right: String): Int? {
    if (left.length != right.length || left.length < 2) return null
    for (index in 0 until left.lastIndex) {
        if (
            left[index] == right[index + 1] &&
            left[index + 1] == right[index] &&
            left.removeRange(index, index + 2) == right.removeRange(index, index + 2)
        ) {
            return 1
        }
    }
    return null
}
