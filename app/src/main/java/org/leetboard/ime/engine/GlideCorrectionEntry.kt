package org.leetboard.ime.engine

data class GlideCorrectionEntry(
    val word: String,
    val acceptedCount: Int = 1,
    val rejectedCount: Int = 0,
    val lastUsedEpochMillis: Long = 0L,
) {
    fun accepted(nowEpochMillis: Long): GlideCorrectionEntry {
        return copy(
            acceptedCount = (acceptedCount + 1).coerceAtMost(MAX_HEALTH_COUNT),
            lastUsedEpochMillis = nowEpochMillis,
        )
    }

    fun rejected(nowEpochMillis: Long): GlideCorrectionEntry {
        return copy(
            rejectedCount = (rejectedCount + 1).coerceAtMost(MAX_HEALTH_COUNT),
            lastUsedEpochMillis = nowEpochMillis,
        )
    }

    fun softBoost(): Int {
        if (isDemoted()) return 0
        return (acceptedCount * ACCEPTED_BOOST - rejectedCount * REJECTED_PENALTY)
            .coerceIn(0, MAX_SOFT_BOOST)
    }

    fun isDemoted(): Boolean {
        return rejectedCount >= MIN_REJECTIONS_FOR_DEMOTION &&
            rejectedCount >= acceptedCount + DEMOTION_REJECTION_MARGIN
    }

    companion object {
        const val MAX_SOFT_BOOST = 900
        private const val ACCEPTED_BOOST = 220
        private const val REJECTED_PENALTY = 400
        private const val MAX_HEALTH_COUNT = 10_000
        private const val MIN_REJECTIONS_FOR_DEMOTION = 2
        private const val DEMOTION_REJECTION_MARGIN = 1

        fun fromWord(word: String, nowEpochMillis: Long = 0L): GlideCorrectionEntry? {
            val normalizedWord = normalizeWord(word) ?: return null
            return GlideCorrectionEntry(word = normalizedWord, lastUsedEpochMillis = nowEpochMillis)
        }
    }
}
