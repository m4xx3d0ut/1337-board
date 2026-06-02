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

data class SpeechTextAutomationOptions(
    val smartCleanupEnabled: Boolean = true,
    val autoSpacingEnabled: Boolean = true,
    val autoCapSentencesEnabled: Boolean = true,
    val autoCapNamesEnabled: Boolean = true,
    val spokenPunctuationCommandsEnabled: Boolean = true,
    val customNames: Set<String> = emptySet(),
)

data class SpeechInsertion(
    val text: String,
    val deleteBeforeChars: Int = 0,
)

fun formatSpeechInsertionText(
    recognizedText: String,
    textBeforeCursor: CharSequence?,
    autoCapAfterSentence: Boolean,
    customNames: Set<String> = emptySet(),
): String {
    return formatSpeechInsertion(
        recognizedText = recognizedText,
        textBeforeCursor = textBeforeCursor,
        options = SpeechTextAutomationOptions(
            autoCapSentencesEnabled = autoCapAfterSentence,
            customNames = customNames,
        ),
    ).text
}

fun formatSpeechInsertion(
    recognizedText: String,
    textBeforeCursor: CharSequence?,
    options: SpeechTextAutomationOptions,
): SpeechInsertion {
    val rawSpeech = recognizedText.trim()
    if (rawSpeech.isEmpty()) return SpeechInsertion("")
    if (!options.smartCleanupEnabled) return SpeechInsertion(rawSpeech)

    val trimmedSpeech = if (options.spokenPunctuationCommandsEnabled) {
        rawSpeech.normalizeSpokenPunctuationCommands().trim()
    } else {
        rawSpeech
    }
    if (trimmedSpeech.isEmpty()) return SpeechInsertion("")

    val beforeCursor = textBeforeCursor?.toString().orEmpty()
    val previousNonSpace = beforeCursor.lastOrNull { !it.isWhitespace() }
    val cursorAtLineStart = beforeCursor.isBlank() ||
        beforeCursor.substringAfterLast('\n', missingDelimiterValue = beforeCursor).isBlank()
    val startsWithPunctuation = trimmedSpeech.first().isSpeechPunctuation()
    val deleteBeforeChars = if (options.autoSpacingEnabled && startsWithPunctuation) {
        beforeCursor.takeLastWhile { it == ' ' || it == '\t' }.length
    } else {
        0
    }
    val needsLeadingSpace = options.autoSpacingEnabled &&
        shouldInsertLeadingSpaceBeforeText(
            textBeforeCursor = beforeCursor,
            insertedText = trimmedSpeech,
            treatDigitAfterDigitAsContinuation = true,
        )
    val shouldCapitalize = options.autoCapSentencesEnabled &&
        (previousNonSpace == null || previousNonSpace.endsSentence() || cursorAtLineStart)
    val normalizedSpeech = when {
        shouldCapitalize -> trimmedSpeech
        options.autoCapSentencesEnabled && previousNonSpace != null -> trimmedSpeech.lowercaseFirstWordStart()
        else -> trimmedSpeech
    }.applySpeechTextCase(
        capitalizeFirstWord = shouldCapitalize,
        autoCapSentences = options.autoCapSentencesEnabled,
        autoCapNames = options.autoCapNamesEnabled,
        autoCapPronounI = options.autoCapSentencesEnabled || options.autoCapNamesEnabled,
        properNames = speechProperNameMap(options.customNames),
    )

    return SpeechInsertion(
        text = buildString {
            if (needsLeadingSpace) append(' ')
            append(normalizedSpeech)
        },
        deleteBeforeChars = deleteBeforeChars,
    )
}

fun shouldInsertLeadingSpaceBeforeText(
    textBeforeCursor: CharSequence?,
    insertedText: String,
    treatDigitAfterDigitAsContinuation: Boolean = false,
): Boolean {
    val trimmedInsertedText = insertedText.trimStart()
    if (trimmedInsertedText.isEmpty() || trimmedInsertedText.first().isSpeechPunctuation()) return false
    val beforeCursor = textBeforeCursor?.toString().orEmpty()
    if (beforeCursor.lastOrNull()?.isWhitespace() == true) return false
    val previousNonSpace = beforeCursor.lastOrNull { !it.isWhitespace() } ?: return false
    if (
        treatDigitAfterDigitAsContinuation &&
        previousNonSpace.isDigit() &&
        trimmedInsertedText.first().isDigit()
    ) {
        return false
    }
    return previousNonSpace.needsSpaceBeforeInsertedWord()
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

private fun Char.needsSpaceBeforeInsertedWord(): Boolean = isLetterOrDigit() || isSpeechPunctuation()

private fun String.applySpeechTextCase(
    capitalizeFirstWord: Boolean,
    autoCapSentences: Boolean,
    autoCapNames: Boolean,
    autoCapPronounI: Boolean,
    properNames: Map<String, String>,
): String {
    val builder = StringBuilder(length)
    val word = StringBuilder()
    var capitalizeNextLetter = capitalizeFirstWord

    fun flushWord() {
        if (word.isEmpty()) return
        val rawWord = word.toString()
        builder.append(
            rawWord.applySpeechWordCase(
                capitalizeWordStart = capitalizeNextLetter,
                autoCapNames = autoCapNames,
                autoCapPronounI = autoCapPronounI,
                properNames = properNames,
            ),
        )
        if (rawWord.any { it.isLetter() }) {
            capitalizeNextLetter = false
        }
        word.clear()
    }

    forEach { char ->
        when {
            char.isSpeechWordChar() -> word.append(char)
            else -> {
                flushWord()
                builder.append(char)
                if (autoCapSentences && (char.endsSentence() || char == '\n')) {
                    capitalizeNextLetter = true
                }
            }
        }
    }
    flushWord()
    return builder.toString()
}

private fun String.applySpeechWordCase(
    capitalizeWordStart: Boolean,
    autoCapNames: Boolean,
    autoCapPronounI: Boolean,
    properNames: Map<String, String>,
): String {
    val lowerWord = lowercase()
    if (autoCapNames) {
        properNames[lowerWord]?.let { return it }
    }
    if (autoCapPronounI && lowerWord == "i") return "I"
    if (autoCapPronounI && lowerWord.startsWith("i'")) return "I" + drop(1)
    return if (capitalizeWordStart) capitalizeFirstChar() else this
}

private fun Char.isSpeechWordChar(): Boolean {
    return isLetterOrDigit() || this == '\'' || this == '-'
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

private fun speechProperNameMap(customNames: Set<String>): Map<String, String> {
    return linkedMapOf<String, String>().apply {
        builtInSpeechProperNames.forEach { name -> put(name.lowercase(), name) }
        customNames.forEach { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) put(trimmed.lowercase(), trimmed)
        }
    }
}

private val builtInSpeechProperNames = listOf(
    "Michael",
    "Sarah",
    "David",
    "James",
    "Maria",
    "Robert",
    "John",
    "Mary",
    "Jennifer",
    "Jessica",
    "Daniel",
    "Matthew",
    "Christopher",
    "Elizabeth",
    "Joseph",
    "Thomas",
    "Susan",
    "Karen",
    "Lisa",
    "Nancy",
    "Steven",
    "Kevin",
    "Brian",
    "George",
    "Edward",
    "Jason",
    "Michelle",
    "Amanda",
    "Melissa",
    "Stephanie",
)
