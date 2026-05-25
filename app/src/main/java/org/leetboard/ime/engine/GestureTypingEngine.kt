package org.leetboard.ime.engine

import android.view.inputmethod.EditorInfo
import kotlin.math.abs

class GestureTypingEngine(
    private val textContextPolicy: TextContextPolicy,
    private val wordsProvider: () -> List<String> = { emptyList() },
) {
    private val rejectedWordsByPath = mutableMapOf<String, MutableSet<String>>()
    private var correctionMap: Map<String, String> = emptyMap()

    fun isEnabledFor(editorInfo: EditorInfo?): Boolean {
        return textContextPolicy.allowsGestureTyping(editorInfo)
    }

    fun decode(pathLabels: List<String>): String? {
        return decode(pathLabels, GlideTypingOptions())
    }

    fun decode(pathLabels: List<String>, options: GlideTypingOptions): String? {
        return candidates(pathLabels, options).firstOrNull()?.word
    }

    fun candidates(
        pathLabels: List<String>,
        options: GlideTypingOptions = GlideTypingOptions(),
        limit: Int = DEFAULT_CANDIDATE_LIMIT,
    ): List<GlideCandidate> {
        val path = normalizePath(pathLabels)
        if (path.size < MIN_KEYS_FOR_GESTURE) return emptyList()
        val pathSignature = path.joinToString(separator = "")
        val rejectedWords = rejectedWordsByPath[pathSignature].orEmpty()
        val correctionCandidate = correctionMap[pathSignature]
            ?.takeIf { word -> word !in rejectedWords }
            ?.let { correctedWord ->
                GlideCandidate(
                    word = correctedWord,
                    score = CORRECTION_SCORE,
                    source = GlideCandidateSource.LOCAL_CORRECTION,
                )
            }
        val dictionaryCandidates = wordsProvider()
            .asSequence()
            .mapIndexedNotNull { index, rawWord ->
                val word = normalizeWord(rawWord) ?: return@mapIndexedNotNull null
                if (word in rejectedWords || word == correctionCandidate?.word) return@mapIndexedNotNull null
                val score = scoreCandidate(word, pathSignature, index, options) ?: return@mapIndexedNotNull null
                GlideCandidate(
                    word = word,
                    score = score,
                    source = if (index < options.importedWordCount) {
                        GlideCandidateSource.IMPORTED_WORDLIST
                    } else {
                        GlideCandidateSource.BUNDLED_WORDLIST
                    },
                )
            }
            .distinctBy { candidate -> candidate.word }
            .sortedWith(
                compareBy<GlideCandidate> { it.score }
                    .thenBy { if (options.preferShorterWords) it.word.length else 0 }
                    .thenBy { it.word.length }
                    .thenBy { it.word },
            )
            .toList()

        val rankedCandidates = buildList {
            if (correctionCandidate != null) add(correctionCandidate)
            addAll(dictionaryCandidates)
        }
            .distinctBy { candidate -> candidate.word }

        return if (rankedCandidates.isNotEmpty()) {
            rankedCandidates.take(limit)
        } else {
            rawPathFallback(pathSignature, options)?.let { word ->
                listOf(GlideCandidate(word, RAW_FALLBACK_SCORE, GlideCandidateSource.RAW_PATH))
            }.orEmpty()
        }
    }

    fun setCorrections(corrections: Map<String, String>) {
        correctionMap = corrections.mapNotNull { (pathSignature, word) ->
            val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return@mapNotNull null
            val normalizedWord = normalizeWord(word) ?: return@mapNotNull null
            normalizedPathSignature to normalizedWord
        }.toMap()
    }

    fun pathSignature(pathLabels: List<String>): String? {
        return glidePathSignature(pathLabels)
    }

    fun rejectCandidate(pathLabels: List<String>, word: String) {
        val pathSignature = glidePathSignature(pathLabels) ?: return
        val normalizedWord = normalizeWord(word) ?: return
        rejectedWordsByPath.getOrPut(pathSignature) { mutableSetOf() } += normalizedWord
    }

    private fun scoreCandidate(
        word: String,
        pathSignature: String,
        priority: Int,
        options: GlideTypingOptions,
    ): Int? {
        if (word.length < MIN_WORD_LENGTH) return null
        if (options.strictFirstLastLetter && (word.first() != pathSignature.first() || word.last() != pathSignature.last())) {
            return null
        }
        val wordSignature = collapseRepeats(word)
        val priorityPenalty = priorityPenalty(priority, options)
        val orderedCost = orderedMatchCost(wordSignature, pathSignature)
        if (orderedCost != null) {
            val lengthPenalty = abs(pathSignature.length - wordSignature.length)
            return orderedCost * options.orderedSkipWeight() +
                lengthPenalty * LENGTH_WEIGHT +
                options.shortWordPenalty(word) +
                priorityPenalty
        }
        val distance = levenshtein(pathSignature, wordSignature)
        val maximumDistance = options.maximumDistance(pathSignature.length)
        if (distance > maximumDistance) return null
        val lengthPenalty = abs(pathSignature.length - wordSignature.length)
        return distance * DISTANCE_WEIGHT + lengthPenalty * LENGTH_WEIGHT + options.shortWordPenalty(word) + priorityPenalty
    }

    private fun rawPathFallback(pathSignature: String, options: GlideTypingOptions): String? {
        return when (options.rawPathFallbackMode) {
            GlideRawFallbackMode.OFF -> null
            GlideRawFallbackMode.SHORT_ONLY -> pathSignature.takeIf { it.length <= MAX_FALLBACK_SIGNATURE_LENGTH }
            GlideRawFallbackMode.ALWAYS -> pathSignature
        }
    }

    private fun priorityPenalty(priority: Int, options: GlideTypingOptions): Int {
        val orderPenalty = priority / options.priorityBucketSize()
        val importedBoost = if (
            options.importedWordsPriority == GlideImportedWordsPriority.HIGH &&
            priority < options.importedWordCount
        ) {
            IMPORTED_WORD_BOOST
        } else {
            0
        }
        return orderPenalty + importedBoost
    }

    private companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 5
        const val MIN_WORD_LENGTH = 2
        const val MAX_FALLBACK_SIGNATURE_LENGTH = 4
        const val CORRECTION_SCORE = -100_000
        const val RAW_FALLBACK_SCORE = 1_000_000
        const val IMPORTED_WORD_BOOST = -700
        const val DISTANCE_WEIGHT = 1000
        const val LENGTH_WEIGHT = 100
    }
}

data class GlideCandidate(
    val word: String,
    val score: Int,
    val source: GlideCandidateSource,
)

enum class GlideCandidateSource {
    LOCAL_CORRECTION,
    IMPORTED_WORDLIST,
    BUNDLED_WORDLIST,
    RAW_PATH,
}

data class GlideTypingOptions(
    val preferShorterWords: Boolean = false,
    val strictFirstLastLetter: Boolean = true,
    val pathTolerance: GlidePathTolerance = GlidePathTolerance.BALANCED,
    val importedWordsPriority: GlideImportedWordsPriority = GlideImportedWordsPriority.NORMAL,
    val rawPathFallbackMode: GlideRawFallbackMode = GlideRawFallbackMode.SHORT_ONLY,
    val importedWordCount: Int = 0,
)

enum class GlidePathTolerance {
    STRICT,
    BALANCED,
    LOOSE,
}

enum class GlideImportedWordsPriority {
    NORMAL,
    HIGH,
}

enum class GlideRawFallbackMode {
    OFF,
    SHORT_ONLY,
    ALWAYS,
}

fun normalizeWord(value: String): String? {
    val normalized = value.trim().lowercase()
    return normalized.takeIf { word ->
        word.length in 2..24 && word.all { char -> char in 'a'..'z' }
    }
}

private fun GlideTypingOptions.maximumDistance(pathLength: Int): Int {
    return when (pathTolerance) {
        GlidePathTolerance.STRICT -> (pathLength / 4).coerceAtLeast(1)
        GlidePathTolerance.BALANCED -> (pathLength / 3).coerceAtLeast(1)
        GlidePathTolerance.LOOSE -> (pathLength / 2).coerceAtLeast(2)
    }
}

private fun GlideTypingOptions.orderedSkipWeight(): Int {
    return when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_ORDERED_SKIP_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.ORDERED_SKIP_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_ORDERED_SKIP_WEIGHT
    }
}

private fun GlideTypingOptions.priorityBucketSize(): Int {
    return when (importedWordsPriority) {
        GlideImportedWordsPriority.NORMAL -> GestureScoring.NORMAL_PRIORITY_BUCKET_SIZE
        GlideImportedWordsPriority.HIGH -> GestureScoring.HIGH_PRIORITY_BUCKET_SIZE
    }
}

private fun GlideTypingOptions.shortWordPenalty(word: String): Int {
    return if (preferShorterWords) word.length * GestureScoring.SHORT_WORD_WEIGHT else 0
}

private object GestureScoring {
    const val ORDERED_SKIP_WEIGHT = 1000
    const val STRICT_ORDERED_SKIP_WEIGHT = 1300
    const val LOOSE_ORDERED_SKIP_WEIGHT = 750
    const val NORMAL_PRIORITY_BUCKET_SIZE = 50
    const val HIGH_PRIORITY_BUCKET_SIZE = 18
    const val SHORT_WORD_WEIGHT = 20
}

fun glidePathSignature(labels: List<String>): String? {
    val path = normalizePath(labels)
    return path
        .joinToString(separator = "")
        .takeIf { signature -> signature.length >= MIN_KEYS_FOR_GESTURE }
}

fun normalizeGlidePathSignature(value: String): String? {
    val normalized = collapseRepeats(value.trim().lowercase())
    return normalized.takeIf { signature ->
        signature.length in MIN_KEYS_FOR_GESTURE..MAX_GLIDE_PATH_SIGNATURE_LENGTH &&
            signature.all { char -> char in 'a'..'z' }
    }
}

fun collapseRepeats(value: String): String {
    return buildString {
        value.forEach { char ->
            if (lastOrNull() != char) append(char)
        }
    }
}

private const val MIN_KEYS_FOR_GESTURE = 2
private const val MAX_GLIDE_PATH_SIGNATURE_LENGTH = 64

private fun normalizePath(labels: List<String>): List<String> {
    return labels
        .mapNotNull { label ->
            label.trim().lowercase().takeIf { it.length == 1 && it.first() in 'a'..'z' }
        }
        .fold(emptyList()) { path, label ->
            if (path.lastOrNull() == label) path else path + label
        }
}

private fun orderedMatchCost(wordSignature: String, pathSignature: String): Int? {
    if (wordSignature.length > pathSignature.length) return null
    var searchIndex = 0
    var skipped = 0
    wordSignature.forEach { char ->
        val foundIndex = pathSignature.indexOf(char, searchIndex)
        if (foundIndex < 0) return null
        skipped += foundIndex - searchIndex
        searchIndex = foundIndex + 1
    }
    return skipped + pathSignature.length - searchIndex
}

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length

    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftChar ->
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            val substitution = if (leftChar == rightChar) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + substitution,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}
