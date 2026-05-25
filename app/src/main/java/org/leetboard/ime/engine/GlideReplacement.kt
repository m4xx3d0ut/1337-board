package org.leetboard.ime.engine

data class GlideReplacementSpan(
    val start: Int,
    val end: Int,
)

fun pendingGlideCommitMatchesBeforeCursor(
    textBeforeCursor: CharSequence?,
    pendingCommittedText: String,
): Boolean {
    return pendingCommittedText.isNotEmpty() && textBeforeCursor?.toString() == pendingCommittedText
}

fun findPendingGlideReplacementSpan(
    text: CharSequence?,
    selectionStart: Int,
    selectionEnd: Int,
    pendingCommittedText: String,
): GlideReplacementSpan? {
    if (pendingCommittedText.isEmpty() || text == null) return null
    val content = text.toString()
    if (selectionStart !in 0..content.length || selectionEnd !in 0..content.length || selectionStart > selectionEnd) {
        return null
    }

    if (selectionStart == selectionEnd) {
        val start = selectionStart - pendingCommittedText.length
        if (start >= 0 && content.regionMatches(start, pendingCommittedText, 0, pendingCommittedText.length)) {
            return GlideReplacementSpan(start, selectionStart)
        }
        return null
    }

    val selectedText = content.substring(selectionStart, selectionEnd)
    return if (selectedText == pendingCommittedText) {
        GlideReplacementSpan(selectionStart, selectionEnd)
    } else {
        null
    }
}
