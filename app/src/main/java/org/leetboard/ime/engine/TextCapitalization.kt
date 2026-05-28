package org.leetboard.ime.engine

import org.leetboard.ime.model.HeldModifiers
import org.leetboard.ime.model.ModifierState

fun String.applyKeyboardCapitalization(
    modifiers: ModifierState,
    heldModifiers: HeldModifiers,
    autoCapAfterPeriod: Boolean,
    textBeforeCursor: CharSequence?,
    allCapsOnShift: Boolean = true,
): String {
    if (isEmpty()) return this
    return when {
        modifiers.shiftLocked -> uppercase()
        modifiers.shift || heldModifiers.shift -> if (allCapsOnShift) uppercase() else capitalizeFirstChar()
        autoCapAfterPeriod && shouldAutoCapAfterPeriod(textBeforeCursor) -> replaceFirstChar { it.uppercase() }
        else -> this
    }
}

fun shouldAutoCapAfterPeriod(textBeforeCursor: CharSequence?): Boolean {
    val trimmed = textBeforeCursor?.toString()?.trimEnd().orEmpty()
    return trimmed.lastOrNull() == '.'
}

fun formatSpeechInsertionText(
    recognizedText: String,
    textBeforeCursor: CharSequence?,
    autoCapAfterSentence: Boolean,
): String {
    val trimmedSpeech = recognizedText.normalizeSpokenPunctuationCommands().trim()
    if (trimmedSpeech.isEmpty()) return ""

    val beforeCursor = textBeforeCursor?.toString().orEmpty()
    val previousNonSpace = beforeCursor.lastOrNull { !it.isWhitespace() }
    val cursorHasTrailingSpace = beforeCursor.lastOrNull()?.isWhitespace() == true
    val cursorAtLineStart = beforeCursor.isBlank() ||
        beforeCursor.substringAfterLast('\n', missingDelimiterValue = beforeCursor).isBlank()
    val startsWithPunctuation = trimmedSpeech.first().isSpeechPunctuation()
    val needsLeadingSpace = !startsWithPunctuation &&
        previousNonSpace != null &&
        !cursorHasTrailingSpace &&
        previousNonSpace.needsSpaceBeforeSpeechWord()
    val shouldCapitalize = autoCapAfterSentence &&
        (previousNonSpace == null || previousNonSpace.endsSentence() || cursorAtLineStart)
    val normalizedSpeech = when {
        shouldCapitalize -> trimmedSpeech
        previousNonSpace != null -> trimmedSpeech.lowercaseFirstWordStart()
        else -> trimmedSpeech
    }.applySpeechSentenceCase(capitalizeFirstWord = shouldCapitalize)

    return buildString {
        if (needsLeadingSpace) append(' ')
        append(normalizedSpeech)
    }
}

fun String.normalizeSpokenPunctuationCommands(): String {
    val tokens = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return ""

    val builder = StringBuilder()
    var index = 0
    while (index < tokens.size) {
        val current = tokens[index].speechCommandToken()
        val next = tokens.getOrNull(index + 1)?.speechCommandToken()
        when {
            current == "new" && next == "paragraph" -> {
                builder.appendLineBreak(paragraph = true)
                index += 2
            }
            current == "new" && next == "line" -> {
                builder.appendLineBreak(paragraph = false)
                index += 2
            }
            current == "full" && next == "stop" -> {
                builder.appendSpeechPunctuation(".")
                index += 2
            }
            current == "question" && next == "mark" -> {
                builder.appendSpeechPunctuation("?")
                index += 2
            }
            current == "exclamation" && (next == "point" || next == "mark") -> {
                builder.appendSpeechPunctuation("!")
                index += 2
            }
            current in singleWordSpeechPunctuation -> {
                builder.appendSpeechPunctuation(singleWordSpeechPunctuation.getValue(current))
                index += 1
            }
            else -> {
                builder.appendSpeechWord(tokens[index])
                index += 1
            }
        }
    }
    return builder.toString()
}

private fun String.capitalizeFirstChar(): String {
    return replaceFirstChar { it.uppercase() }
}

private fun String.lowercaseFirstWordStart(): String {
    if (this == "I" || startsWith("I ")) return this
    if (length < 2) return lowercase()
    val first = this[0]
    val second = this[1]
    return if (first.isUpperCase() && second.isLowerCase()) {
        first.lowercaseChar() + drop(1)
    } else {
        this
    }
}

private fun Char.endsSentence(): Boolean = this == '.' || this == '!' || this == '?'

private fun Char.isSpeechPunctuation(): Boolean = this in ",.!?;:"

private fun Char.needsSpaceBeforeSpeechWord(): Boolean = isLetterOrDigit() || isSpeechPunctuation()

private fun String.applySpeechSentenceCase(capitalizeFirstWord: Boolean): String {
    val builder = StringBuilder(length)
    var capitalizeNextLetter = capitalizeFirstWord
    forEach { char ->
        when {
            char.isLetter() && capitalizeNextLetter -> {
                builder.append(char.uppercaseChar())
                capitalizeNextLetter = false
            }
            else -> builder.append(char)
        }
        if (char.endsSentence() || char == '\n') {
            capitalizeNextLetter = true
        }
    }
    return builder.toString()
}

private fun StringBuilder.appendSpeechWord(word: String) {
    if (isNotEmpty() && lastOrNull()?.let { !it.isWhitespace() && it != '\n' } == true) {
        append(' ')
    }
    append(word)
}

private fun StringBuilder.appendSpeechPunctuation(punctuation: String) {
    while (lastOrNull() == ' ') {
        deleteAt(lastIndex)
    }
    append(punctuation)
}

private fun StringBuilder.appendLineBreak(paragraph: Boolean) {
    while (lastOrNull() == ' ') {
        deleteAt(lastIndex)
    }
    append(if (paragraph) "\n\n" else "\n")
}

private fun String.speechCommandToken(): String {
    return trim { !it.isLetter() }.lowercase()
}

private val singleWordSpeechPunctuation = mapOf(
    "comma" to ",",
    "period" to ".",
    "dot" to ".",
    "colon" to ":",
    "semicolon" to ";",
)
