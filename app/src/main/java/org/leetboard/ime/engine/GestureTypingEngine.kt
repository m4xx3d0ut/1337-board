package org.leetboard.ime.engine

import android.view.inputmethod.EditorInfo
import kotlin.math.abs

class GestureTypingEngine(
    private val textContextPolicy: TextContextPolicy,
    private val predictionEngine: GlidePredictionEngine = FrequencyContextGlidePredictionEngine(),
    private val wordsProvider: () -> List<String> = { emptyList() },
) {
    private val rejectedWordsByPath = mutableMapOf<String, MutableSet<String>>()
    private var correctionMap: Map<String, GlideCorrectionEntry> = emptyMap()
    private var cachedProviderWords: List<String>? = null
    private var cachedNormalizedWords: List<String> = emptyList()

    fun isEnabledFor(editorInfo: EditorInfo?): Boolean {
        return textContextPolicy.allowsGestureTyping(editorInfo)
    }

    fun decode(pathLabels: List<String>): String? {
        return decode(pathLabels, GlideTypingOptions())
    }

    fun decode(
        pathLabels: List<String>,
        options: GlideTypingOptions,
        context: GlidePredictionContext = GlidePredictionContext(),
        touchTrace: GlideTouchTrace? = null,
    ): String? {
        return candidates(pathLabels, options, context, touchTrace).firstOrNull()?.word
    }

    fun candidates(
        pathLabels: List<String>,
        options: GlideTypingOptions = GlideTypingOptions(),
        context: GlidePredictionContext = GlidePredictionContext(),
        touchTrace: GlideTouchTrace? = null,
        limit: Int = DEFAULT_CANDIDATE_LIMIT,
    ): List<GlideCandidate> {
        val path = normalizePath(pathLabels)
        if (path.size < MIN_KEYS_FOR_GESTURE) return emptyList()
        val pathSignature = path.joinToString(separator = "")
        val rejectedWords = rejectedWordsByPath[pathSignature].orEmpty()
        val normalizedWords = normalizedWords()
        val traceProfile = GlideGeometryScorer.profile(touchTrace)
        val correctionCandidate = correctionMap[pathSignature]
            ?.takeIf { correction -> correction.word !in rejectedWords }
            ?.takeIf { correction -> localCorrectionAllowed(correction.word, pathSignature, options) }
            ?.takeUnless { correction -> correction.isDemoted() }
            ?.let { correction ->
                val score = scoreCandidate(
                    word = correction.word,
                    pathSignature = pathSignature,
                    priority = CORRECTION_PRIORITY,
                    options = options,
                    traceProfile = traceProfile,
                ) ?: return@let null
                GlideCandidate(
                    word = correction.word,
                    score = score +
                        localCorrectionEndpointPenalty(correction.word, pathSignature, options) -
                        correction.softBoost(),
                    source = GlideCandidateSource.LOCAL_CORRECTION,
                    priority = CORRECTION_PRIORITY,
                )
            }
        val dictionaryCandidates = normalizedWords
            .asSequence()
            .mapIndexedNotNull { index, rawWord ->
                val word = rawWord
                if (word in rejectedWords || word == correctionCandidate?.word) return@mapIndexedNotNull null
                val score = scoreCandidate(word, pathSignature, index, options, traceProfile)
                    ?: return@mapIndexedNotNull null
                GlideCandidate(
                    word = word,
                    score = score,
                    source = if (index < options.importedWordCount) {
                        GlideCandidateSource.IMPORTED_WORDLIST
                    } else {
                        GlideCandidateSource.BUNDLED_WORDLIST
                    },
                    priority = index,
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
            .let { candidates ->
                predictionEngine.rank(candidates, pathSignature, context, options)
            }

        return if (rankedCandidates.isNotEmpty()) {
            rankedCandidates.take(limit)
        } else {
            rawPathFallback(pathSignature, options)?.let { word ->
                listOf(GlideCandidate(word, RAW_FALLBACK_SCORE, GlideCandidateSource.RAW_PATH))
            }.orEmpty()
        }
    }

    fun setCorrections(corrections: Map<String, GlideCorrectionEntry>) {
        correctionMap = corrections.mapNotNull { (pathSignature, word) ->
            val normalizedPathSignature = normalizeGlidePathSignature(pathSignature) ?: return@mapNotNull null
            val normalizedWord = normalizeWord(word.word) ?: return@mapNotNull null
            normalizedPathSignature to word.copy(word = normalizedWord)
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

    fun recordAcceptedWord(word: String, context: GlidePredictionContext) {
        predictionEngine.recordAcceptedWord(word, context)
    }

    fun rejectAcceptedWord(word: String, context: GlidePredictionContext) {
        predictionEngine.rejectAcceptedWord(word, context)
    }

    private fun localCorrectionAllowed(
        word: String,
        pathSignature: String,
        options: GlideTypingOptions,
    ): Boolean {
        if (!options.strictFirstLastLetter) return true
        val wordSignature = glideWordSignature(word) ?: return false
        return wordSignature.firstOrNull() == pathSignature.firstOrNull() &&
            wordSignature.lastOrNull() == pathSignature.lastOrNull()
    }

    private fun localCorrectionEndpointPenalty(
        word: String,
        pathSignature: String,
        options: GlideTypingOptions,
    ): Int {
        if (pathSignature.isEmpty() || options.strictFirstLastLetter) return 0
        val wordSignature = glideWordSignature(word) ?: return 0
        var penalty = 0
        if (wordSignature.firstOrNull() != pathSignature.first()) penalty += LOCAL_CORRECTION_ENDPOINT_MISMATCH_PENALTY
        if (wordSignature.lastOrNull() != pathSignature.last()) penalty += LOCAL_CORRECTION_ENDPOINT_MISMATCH_PENALTY
        return penalty
    }

    private fun scoreCandidate(
        word: String,
        pathSignature: String,
        priority: Int,
        options: GlideTypingOptions,
        traceProfile: GlideTraceProfile?,
    ): Int? {
        val wordSignature = glideWordSignature(word) ?: return null
        if (wordSignature.length < MIN_WORD_LENGTH) return null
        if (
            options.strictFirstLastLetter &&
            (wordSignature.first() != pathSignature.first() || wordSignature.last() != pathSignature.last())
        ) {
            return null
        }
        val priorityPenalty = priorityPenalty(priority, options)
        val shortAnchoredCost = shortAnchoredMatchCost(wordSignature, pathSignature)
        if (shortAnchoredCost != null) {
            return shortAnchoredCost +
                tracePenalty(wordSignature, traceProfile, options) +
                priorityPenalty +
                options.shortWordPenalty(wordSignature)
        }
        val orderedCost = orderedMatchCost(wordSignature, pathSignature)
        if (orderedCost != null) {
            val lengthPenalty = abs(pathSignature.length - wordSignature.length)
            val geometryPenalty = GlideGeometryScorer.cost(wordSignature, pathSignature) * options.geometryWeight()
            return orderedCost * options.orderedSkipWeight() +
                lengthPenalty * LENGTH_WEIGHT +
                geometryPenalty +
                tracePenalty(wordSignature, traceProfile, options) +
                shortPathLengthPenalty(wordSignature, pathSignature) +
                options.shortWordPenalty(wordSignature) +
                priorityPenalty
        }
        val distance = levenshtein(pathSignature, wordSignature)
        val maximumDistance = options.maximumDistance(pathSignature.length)
        if (distance > maximumDistance) return null
        val lengthPenalty = abs(pathSignature.length - wordSignature.length)
        val geometryPenalty = GlideGeometryScorer.cost(wordSignature, pathSignature) * options.geometryWeight()
        return distance * DISTANCE_WEIGHT +
            lengthPenalty * LENGTH_WEIGHT +
            geometryPenalty +
            tracePenalty(wordSignature, traceProfile, options) +
            shortPathLengthPenalty(wordSignature, pathSignature) +
            options.shortWordPenalty(wordSignature) +
            priorityPenalty
    }

    private fun normalizedWords(): List<String> {
        val providerWords = wordsProvider()
        if (providerWords === cachedProviderWords) return cachedNormalizedWords
        return providerWords.mapNotNull(::normalizeWord).also { words ->
            cachedProviderWords = providerWords
            cachedNormalizedWords = words
        }
    }

    private fun tracePenalty(
        wordSignature: String,
        traceProfile: GlideTraceProfile?,
        options: GlideTypingOptions,
    ): Int {
        traceProfile ?: return 0
        return GlideGeometryScorer.touchCost(wordSignature, traceProfile) * options.touchTraceWeight() +
            GlideGeometryScorer.dwellCost(
                wordSignature = wordSignature,
                profile = traceProfile,
                activationThreshold = options.dwellActivationThreshold,
            ) * options.dwellWeight() +
            GlideGeometryScorer.cornerCost(wordSignature, traceProfile) * options.cornerTraceWeight() +
            GlideGeometryScorer.anchorCost(wordSignature, traceProfile) * options.anchorWeight() +
            GlideGeometryScorer.cornerKeyCost(wordSignature, traceProfile) * options.cornerKeyWeight()
    }

    private fun shortPathLengthPenalty(wordSignature: String, pathSignature: String): Int {
        if (pathSignature.length > SHORT_PATH_LENGTH) return 0
        return (wordSignature.length - pathSignature.length)
            .coerceAtLeast(0) * SHORT_PATH_EXTRA_LENGTH_WEIGHT
    }

    private fun shortAnchoredMatchCost(wordSignature: String, pathSignature: String): Int? {
        if (wordSignature.length !in 2..SHORT_ANCHORED_MAX_WORD_LENGTH) return null
        if (pathSignature.length <= wordSignature.length || pathSignature.length > SHORT_ANCHORED_MAX_PATH_LENGTH) {
            return null
        }
        if (wordSignature.first() != pathSignature.first() || wordSignature.last() != pathSignature.last()) return null
        val middleLetters = wordSignature.drop(1).dropLast(1)
        if (middleLetters.any { char -> char !in pathSignature }) return null
        val extraLetters = pathSignature.length - wordSignature.length
        return SHORT_ANCHORED_BASE_COST + extraLetters * SHORT_ANCHORED_EXTRA_LETTER_WEIGHT
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
        const val CORRECTION_PRIORITY = 3_000
        const val RAW_FALLBACK_SCORE = 1_000_000
        const val IMPORTED_WORD_BOOST = -700
        const val DISTANCE_WEIGHT = 1000
        const val LENGTH_WEIGHT = 100
        const val SHORT_PATH_LENGTH = 5
        const val SHORT_PATH_EXTRA_LENGTH_WEIGHT = 350
        const val SHORT_ANCHORED_MAX_WORD_LENGTH = 4
        const val SHORT_ANCHORED_MAX_PATH_LENGTH = 16
        const val SHORT_ANCHORED_BASE_COST = 1200
        const val SHORT_ANCHORED_EXTRA_LETTER_WEIGHT = 160
        const val LOCAL_CORRECTION_ENDPOINT_MISMATCH_PENALTY = 1200
    }
}

data class GlideCandidate(
    val word: String,
    val score: Int,
    val source: GlideCandidateSource,
    val priority: Int = Int.MAX_VALUE,
)

enum class GlideCandidateSource {
    LOCAL_CORRECTION,
    IMPORTED_WORDLIST,
    BUNDLED_WORDLIST,
    RAW_PATH,
}

data class GlideTypingOptions(
    val preferShorterWords: Boolean = true,
    val strictFirstLastLetter: Boolean = false,
    val pathTolerance: GlidePathTolerance = GlidePathTolerance.LOOSE,
    val spatialPrecision: GlideSpatialPrecision = GlideSpatialPrecision.STANDARD,
    val dwellSensitivity: GlideDwellSensitivity = GlideDwellSensitivity.HIGH,
    val dwellActivationThreshold: Float = DEFAULT_GLIDE_DWELL_ACTIVATION_THRESHOLD,
    val importedWordsPriority: GlideImportedWordsPriority = GlideImportedWordsPriority.HIGH,
    val rawPathFallbackMode: GlideRawFallbackMode = GlideRawFallbackMode.OFF,
    val importedWordCount: Int = 0,
    val predictiveRankingEnabled: Boolean = true,
)

enum class GlidePathTolerance {
    STRICT,
    BALANCED,
    LOOSE,
}

enum class GlideSpatialPrecision {
    FORGIVING,
    STANDARD,
    PRECISE,
}

enum class GlideDwellSensitivity {
    OFF,
    STANDARD,
    HIGH,
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
    val normalized = normalizedWordText(value)
    return normalized.takeIf { word ->
        word.length in 2..24 &&
            word.count { char -> char in 'a'..'z' } >= 2 &&
            word.firstOrNull()?.let { it in 'a'..'z' } == true &&
            word.lastOrNull()?.let { it in 'a'..'z' } == true &&
            word.hasValidWordCharacters(allowTrailingApostrophe = false)
    }
}

fun normalizeWordPrefix(value: String): String? {
    val normalized = normalizedWordText(value)
    return normalized.takeIf { word ->
        word.length in 1..24 &&
            word.any { char -> char in 'a'..'z' } &&
            word.firstOrNull()?.let { it in 'a'..'z' } == true &&
            word.hasValidWordCharacters(allowTrailingApostrophe = true)
    }
}

fun glideWordSignature(value: String): String? {
    val normalizedWord = normalizeWord(value) ?: return null
    return collapseRepeats(normalizedWord.filter { char -> char in 'a'..'z' })
        .takeIf { signature -> signature.length >= MIN_GLIDE_WORD_SIGNATURE_LENGTH }
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

private fun GlideTypingOptions.shortWordPenalty(wordSignature: String): Int {
    return if (preferShorterWords) wordSignature.length * GestureScoring.SHORT_WORD_WEIGHT else 0
}

private fun GlideTypingOptions.geometryWeight(): Int {
    return when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_GEOMETRY_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.GEOMETRY_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_GEOMETRY_WEIGHT
    }
}

private fun GlideTypingOptions.touchTraceWeight(): Int {
    return scaledSpatialWeight(when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_TOUCH_TRACE_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.TOUCH_TRACE_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_TOUCH_TRACE_WEIGHT
    })
}

private fun GlideTypingOptions.dwellWeight(): Int {
    if (dwellSensitivity == GlideDwellSensitivity.OFF) return 0
    val base = when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_DWELL_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.DWELL_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_DWELL_WEIGHT
    }
    return when (dwellSensitivity) {
        GlideDwellSensitivity.OFF -> 0
        GlideDwellSensitivity.STANDARD -> base
        GlideDwellSensitivity.HIGH -> base * 3
    }
}

private fun GlideTypingOptions.cornerTraceWeight(): Int {
    return scaledSpatialWeight(when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_CORNER_TRACE_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.CORNER_TRACE_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_CORNER_TRACE_WEIGHT
    })
}

private fun GlideTypingOptions.anchorWeight(): Int {
    return scaledSpatialWeight(when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_ANCHOR_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.ANCHOR_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_ANCHOR_WEIGHT
    })
}

private fun GlideTypingOptions.cornerKeyWeight(): Int {
    return scaledSpatialWeight(when (pathTolerance) {
        GlidePathTolerance.STRICT -> GestureScoring.STRICT_CORNER_KEY_WEIGHT
        GlidePathTolerance.BALANCED -> GestureScoring.CORNER_KEY_WEIGHT
        GlidePathTolerance.LOOSE -> GestureScoring.LOOSE_CORNER_KEY_WEIGHT
    })
}

private fun GlideTypingOptions.scaledSpatialWeight(baseWeight: Int): Int {
    val percent = when (spatialPrecision) {
        GlideSpatialPrecision.FORGIVING -> 70
        GlideSpatialPrecision.STANDARD -> 100
        GlideSpatialPrecision.PRECISE -> 145
    }
    return ((baseWeight * percent) / 100).coerceAtLeast(1)
}

private object GestureScoring {
    const val ORDERED_SKIP_WEIGHT = 1000
    const val STRICT_ORDERED_SKIP_WEIGHT = 1300
    const val LOOSE_ORDERED_SKIP_WEIGHT = 750
    const val GEOMETRY_WEIGHT = 2
    const val STRICT_GEOMETRY_WEIGHT = 3
    const val LOOSE_GEOMETRY_WEIGHT = 1
    const val TOUCH_TRACE_WEIGHT = 7
    const val STRICT_TOUCH_TRACE_WEIGHT = 8
    const val LOOSE_TOUCH_TRACE_WEIGHT = 4
    const val DWELL_WEIGHT = 1
    const val STRICT_DWELL_WEIGHT = 1
    const val LOOSE_DWELL_WEIGHT = 1
    const val CORNER_TRACE_WEIGHT = 3
    const val STRICT_CORNER_TRACE_WEIGHT = 4
    const val LOOSE_CORNER_TRACE_WEIGHT = 2
    const val ANCHOR_WEIGHT = 5
    const val STRICT_ANCHOR_WEIGHT = 6
    const val LOOSE_ANCHOR_WEIGHT = 3
    const val CORNER_KEY_WEIGHT = 1
    const val STRICT_CORNER_KEY_WEIGHT = 1
    const val LOOSE_CORNER_KEY_WEIGHT = 1
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

private fun normalizedWordText(value: String): String {
    return value.trim().lowercase().replace('\u2019', '\'')
}

private fun String.hasValidWordCharacters(allowTrailingApostrophe: Boolean): Boolean {
    return allIndexed { index, char ->
        when {
            char in 'a'..'z' -> true
            char == '\'' -> {
                val hasLetterBefore = getOrNull(index - 1)?.let { it in 'a'..'z' } == true
                val hasLetterAfter = getOrNull(index + 1)?.let { it in 'a'..'z' } == true
                hasLetterBefore && (hasLetterAfter || (allowTrailingApostrophe && index == lastIndex))
            }
            else -> false
        }
    }
}

private inline fun String.allIndexed(predicate: (Int, Char) -> Boolean): Boolean {
    forEachIndexed { index, char ->
        if (!predicate(index, char)) return false
    }
    return true
}

private const val MIN_KEYS_FOR_GESTURE = 2
private const val MIN_GLIDE_WORD_SIGNATURE_LENGTH = 2
private const val MAX_GLIDE_PATH_SIGNATURE_LENGTH = 64
const val DEFAULT_GLIDE_DWELL_ACTIVATION_THRESHOLD = 0.30214944f

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
