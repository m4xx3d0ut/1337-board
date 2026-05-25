package org.leetboard.ime.engine

import android.content.Context

class GlideDictionaryLoader(context: Context) {
    private val appContext = context.applicationContext

    fun loadWords(): List<String> {
        return importedWords() + bundledWords()
    }

    private fun bundledWords(): List<String> {
        return appContext.assets.open(BUNDLED_WORDS_ASSET).bufferedReader().useLines { lines ->
            lines.mapNotNull(::normalizeWord).toList()
        }
    }

    private fun importedWords(): List<String> {
        val file = appContext.filesDir.resolve(IMPORTED_WORDS_FILE)
        if (!file.isFile) return emptyList()
        return file.useLines { lines -> lines.mapNotNull(::normalizeWord).toList() }
    }

    private companion object {
        const val BUNDLED_WORDS_ASSET = "glide_words_en.txt"
        const val IMPORTED_WORDS_FILE = "glide_words_user.txt"
    }
}
