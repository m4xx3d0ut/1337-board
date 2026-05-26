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
        return (importedWords(importedFile) + bundledWords()).also { words ->
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
    }
}
