package org.leetboard.ime.engine

import java.io.File

class GlideUserLanguageModel(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val words = linkedMapOf<String, LanguageEntry>()
    private val bigrams = linkedMapOf<String, LanguageEntry>()

    @Synchronized
    fun recordAcceptedWord(word: String, previousWord: String?) {
        val normalizedWord = normalizeWord(word) ?: return
        increment(words, normalizedWord, MAX_WORDS)
        val normalizedPrevious = previousWord?.let(::normalizeWord)
        if (normalizedPrevious != null && normalizedPrevious != normalizedWord) {
            increment(bigrams, bigramKey(normalizedPrevious, normalizedWord), MAX_BIGRAMS)
        }
    }

    @Synchronized
    fun rejectAcceptedWord(word: String, previousWord: String?) {
        val normalizedWord = normalizeWord(word) ?: return
        decrement(words, normalizedWord)
        val normalizedPrevious = previousWord?.let(::normalizeWord)
        if (normalizedPrevious != null && normalizedPrevious != normalizedWord) {
            decrement(bigrams, bigramKey(normalizedPrevious, normalizedWord))
        }
    }

    @Synchronized
    fun wordBoost(word: String): Int {
        val normalizedWord = normalizeWord(word) ?: return 0
        return (words[normalizedWord]?.count.orZero() * WORD_BOOST).coerceAtMost(MAX_WORD_BOOST)
    }

    @Synchronized
    fun bigramBoost(previousWord: String, word: String): Int {
        val normalizedPrevious = normalizeWord(previousWord) ?: return 0
        val normalizedWord = normalizeWord(word) ?: return 0
        return (bigrams[bigramKey(normalizedPrevious, normalizedWord)]?.count.orZero() * BIGRAM_BOOST)
            .coerceAtMost(MAX_BIGRAM_BOOST)
    }

    @Synchronized
    fun clear() {
        words.clear()
        bigrams.clear()
    }

    @Synchronized
    fun loadFrom(file: File) {
        clear()
        if (!file.isFile) return
        loadLines(file.readLines())
    }

    @Synchronized
    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(serialize())
    }

    @Synchronized
    fun serialize(): String {
        return buildString {
            words.forEach { (word, entry) ->
                append(WORD_PREFIX)
                append(ENTRY_SEPARATOR)
                append(word)
                append(ENTRY_SEPARATOR)
                append(entry.count)
                append(ENTRY_SEPARATOR)
                append(entry.lastUsedEpochMillis)
                append('\n')
            }
            bigrams.forEach { (key, entry) ->
                val (previousWord, word) = key.split(BIGRAM_SEPARATOR, limit = 2)
                append(BIGRAM_PREFIX)
                append(ENTRY_SEPARATOR)
                append(previousWord)
                append(ENTRY_SEPARATOR)
                append(word)
                append(ENTRY_SEPARATOR)
                append(entry.count)
                append(ENTRY_SEPARATOR)
                append(entry.lastUsedEpochMillis)
                append('\n')
            }
        }
    }

    @Synchronized
    fun loadSerialized(value: String) {
        loadLines(value.lineSequence().toList())
    }

    private fun loadLines(lines: List<String>) {
        clear()
        lines.forEach { line ->
            val parts = line.split(ENTRY_SEPARATOR)
            when (parts.getOrNull(0)) {
                WORD_PREFIX -> {
                    val word = parts.getOrNull(1)?.let(::normalizeWord) ?: return@forEach
                    val entry = parts.toLanguageEntry() ?: return@forEach
                    words[word] = entry
                }
                BIGRAM_PREFIX -> {
                    val previousWord = parts.getOrNull(1)?.let(::normalizeWord) ?: return@forEach
                    val word = parts.getOrNull(2)?.let(::normalizeWord) ?: return@forEach
                    val entry = parts.toLanguageEntry(offset = 1) ?: return@forEach
                    bigrams[bigramKey(previousWord, word)] = entry
                }
            }
        }
        trim(words, MAX_WORDS)
        trim(bigrams, MAX_BIGRAMS)
    }

    private fun increment(map: LinkedHashMap<String, LanguageEntry>, key: String, maxSize: Int) {
        val nextEntry = (map.remove(key) ?: LanguageEntry()).incremented(clock())
        map[key] = nextEntry
        trim(map, maxSize)
    }

    private fun decrement(map: LinkedHashMap<String, LanguageEntry>, key: String) {
        val entry = map.remove(key) ?: return
        val nextEntry = entry.decremented(clock()) ?: return
        map[key] = nextEntry
    }

    private fun trim(map: LinkedHashMap<String, LanguageEntry>, maxSize: Int) {
        while (map.size > maxSize) {
            map.remove(map.keys.first())
        }
    }

    private fun List<String>.toLanguageEntry(offset: Int = 0): LanguageEntry? {
        val count = getOrNull(offset + 2)?.toIntOrNull()?.coerceIn(1, MAX_LANGUAGE_COUNT) ?: return null
        val lastUsed = getOrNull(offset + 3)?.toLongOrNull()?.coerceAtLeast(0L) ?: return null
        return LanguageEntry(count = count, lastUsedEpochMillis = lastUsed)
    }

    private data class LanguageEntry(
        val count: Int = 0,
        val lastUsedEpochMillis: Long = 0L,
    ) {
        fun incremented(nowEpochMillis: Long): LanguageEntry {
            return copy(
                count = (count + 1).coerceAtMost(MAX_LANGUAGE_COUNT),
                lastUsedEpochMillis = nowEpochMillis,
            )
        }

        fun decremented(nowEpochMillis: Long): LanguageEntry? {
            if (count <= 1) return null
            return copy(
                count = count - 1,
                lastUsedEpochMillis = nowEpochMillis,
            )
        }
    }

    companion object {
        const val STORAGE_FILE = "glide_user_language_model.tsv"
        const val MAX_WORDS = 5_000
        const val MAX_BIGRAMS = 10_000
        private const val WORD_PREFIX = "w"
        private const val BIGRAM_PREFIX = "b"
        private const val ENTRY_SEPARATOR = "\t"
        private const val BIGRAM_SEPARATOR = "\u0001"
        private const val WORD_BOOST = 160
        private const val BIGRAM_BOOST = 360
        private const val MAX_WORD_BOOST = 900
        private const val MAX_BIGRAM_BOOST = 1800
        private const val MAX_LANGUAGE_COUNT = 10_000

        fun storageFile(filesDir: File): File = filesDir.resolve(STORAGE_FILE)

        private fun bigramKey(previousWord: String, word: String): String {
            return "$previousWord$BIGRAM_SEPARATOR$word"
        }
    }
}

private fun Int?.orZero() = this ?: 0
