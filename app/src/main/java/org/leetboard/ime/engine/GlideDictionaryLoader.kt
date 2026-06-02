package org.leetboard.ime.engine

import android.content.Context

class GlideDictionaryLoader(context: Context) {
    private val appContext = context.applicationContext
    private var cache: CachedWords? = null

    @Synchronized
    fun loadWords(): List<String> {
        val importedFile = appContext.filesDir.resolve(IMPORTED_WORDS_FILE)
        val stamp = importedFile.cacheStamp()
        val cached = cache
        if (cached != null && cached.importedStamp == stamp) return cached.words
        return (importedWords(importedFile) + commonContractionWords + bundledWords()).distinct().also { words ->
            cache = CachedWords(stamp, words)
        }
    }

    private fun bundledWords(): List<String> {
        return appContext.assets.open(BUNDLED_WORDS_ASSET).bufferedReader().useLines { lines ->
            lines.mapNotNull(::normalizeWord).toList()
        }
    }

    private fun importedWords(file: java.io.File): List<String> {
        if (!file.isFile) return emptyList()
        return file.useLines { lines -> lines.mapNotNull(::normalizeWord).toList() }
    }

    private fun java.io.File.cacheStamp(): ImportedWordsStamp? {
        if (!isFile) return null
        return ImportedWordsStamp(lastModified(), length())
    }

    private data class CachedWords(
        val importedStamp: ImportedWordsStamp?,
        val words: List<String>,
    )

    private data class ImportedWordsStamp(
        val lastModifiedMs: Long,
        val byteCount: Long,
    )

    private companion object {
        const val BUNDLED_WORDS_ASSET = "glide_words_en.txt"
        const val IMPORTED_WORDS_FILE = "glide_words_user.txt"
        val commonContractionWords = listOf(
            "aren't",
            "can't",
            "couldn't",
            "didn't",
            "doesn't",
            "don't",
            "hadn't",
            "hasn't",
            "haven't",
            "he'd",
            "he'll",
            "he's",
            "i'd",
            "i'll",
            "i'm",
            "i've",
            "isn't",
            "it's",
            "let's",
            "she'd",
            "she'll",
            "she's",
            "shouldn't",
            "that's",
            "there's",
            "they'd",
            "they'll",
            "they're",
            "they've",
            "wasn't",
            "we'd",
            "we'll",
            "we're",
            "we've",
            "weren't",
            "what's",
            "where's",
            "who's",
            "won't",
            "wouldn't",
            "you'd",
            "you'll",
            "you're",
            "you've",
        ).mapNotNull(::normalizeWord)
    }
}
