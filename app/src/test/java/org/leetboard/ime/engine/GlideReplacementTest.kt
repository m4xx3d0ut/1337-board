package org.leetboard.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideReplacementTest {
    @Test
    fun pendingCommitMatchesExactTextBeforeCursor() {
        assertTrue(pendingGlideCommitMatchesBeforeCursor("hello ", "hello "))
    }

    @Test
    fun pendingCommitRejectsCursorMovedOrDifferentText() {
        assertFalse(pendingGlideCommitMatchesBeforeCursor("hello x", "hello "))
        assertFalse(pendingGlideCommitMatchesBeforeCursor("say hello ", "hello "))
        assertFalse(pendingGlideCommitMatchesBeforeCursor(null, "hello "))
    }

    @Test
    fun collapsedSelectionFindsPendingCommitImmediatelyBeforeCursor() {
        val span = findPendingGlideReplacementSpan(
            text = "say hello ",
            selectionStart = 10,
            selectionEnd = 10,
            pendingCommittedText = "hello ",
        )

        assertEquals(GlideReplacementSpan(4, 10), span)
    }

    @Test
    fun collapsedSelectionRejectsCursorMovedPastPendingCommit() {
        val span = findPendingGlideReplacementSpan(
            text = "say hello next",
            selectionStart = 14,
            selectionEnd = 14,
            pendingCommittedText = "hello ",
        )

        assertNull(span)
    }

    @Test
    fun selectedPendingCommitCanBeReplaced() {
        val span = findPendingGlideReplacementSpan(
            text = "say hello ",
            selectionStart = 4,
            selectionEnd = 10,
            pendingCommittedText = "hello ",
        )

        assertEquals(GlideReplacementSpan(4, 10), span)
    }

    @Test
    fun selectedMismatchIsNotReplaced() {
        val span = findPendingGlideReplacementSpan(
            text = "say hello ",
            selectionStart = 4,
            selectionEnd = 9,
            pendingCommittedText = "hello ",
        )

        assertNull(span)
    }

    @Test
    fun invalidSelectionIsNotReplaced() {
        assertNull(findPendingGlideReplacementSpan("hello ", -1, 6, "hello "))
        assertNull(findPendingGlideReplacementSpan("hello ", 6, 3, "hello "))
        assertNull(findPendingGlideReplacementSpan("hello ", 0, 7, "hello "))
        assertNull(findPendingGlideReplacementSpan("hello ", 6, 6, ""))
    }
}
