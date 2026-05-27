package org.leetboard.ime.prefs

enum class SettingsExportFormat {
    JSON,
    YAML,
}

data class SettingsImportResult(
    val preferenceCount: Int,
    val importedGlideWordCount: Int,
)

internal data class SettingsExportSnapshot(
    val preferences: Map<String, Any?>,
    val importedGlideWords: List<String> = emptyList(),
    val glideUserLanguageModel: String = "",
) {
    fun encode(format: SettingsExportFormat): String {
        return when (format) {
            SettingsExportFormat.JSON -> JsonCodec.write(rootMap())
            SettingsExportFormat.YAML -> YamlCodec.write(rootMap())
        }
    }

    private fun rootMap(): Map<String, Any?> = linkedMapOf(
        "format" to SETTINGS_EXPORT_FORMAT_ID,
        "version" to SETTINGS_EXPORT_VERSION,
        "preferences" to preferences,
        "imported_glide_words" to importedGlideWords,
        "glide_user_language_model" to glideUserLanguageModel,
    )
}

internal fun decodeSettingsExportSnapshot(text: String): SettingsExportSnapshot {
    val root = if (text.trimStart().startsWith("{")) {
        JsonCodec.parse(text)
    } else {
        YamlCodec.parse(text)
    }
    val format = root["format"] as? String
    require(format == SETTINGS_EXPORT_FORMAT_ID) { "Unsupported settings export format." }
    val version = (root["version"] as? Number)?.toInt()
    require(version == SETTINGS_EXPORT_VERSION) { "Unsupported settings export version." }
    val preferences = (root["preferences"] as? Map<*, *>)
        ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
        ?.toMap()
        ?: emptyMap()
    return SettingsExportSnapshot(
        preferences = preferences,
        importedGlideWords = (root["imported_glide_words"] as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty(),
        glideUserLanguageModel = root["glide_user_language_model"] as? String ?: "",
    )
}

private object JsonCodec {
    fun write(value: Any?): String = buildString { appendJson(value, 0) }

    fun parse(text: String): Map<String, Any?> {
        val parser = Parser(text)
        val value = parser.parse()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: error("Settings export must be a JSON object.")
    }

    private fun StringBuilder.appendJson(value: Any?, indent: Int) {
        when (value) {
            null -> append("null")
            is Boolean -> append(value)
            is Number -> append(value)
            is String -> appendQuoted(value)
            is Map<*, *> -> {
                append("{")
                if (value.isNotEmpty()) {
                    append('\n')
                    value.entries.forEachIndexed { index, entry ->
                        append(" ".repeat(indent + JSON_INDENT))
                        appendQuoted(entry.key.toString())
                        append(": ")
                        appendJson(entry.value, indent + JSON_INDENT)
                        if (index != value.size - 1) append(',')
                        append('\n')
                    }
                    append(" ".repeat(indent))
                }
                append("}")
            }
            is Iterable<*> -> {
                val items = value.toList()
                append("[")
                items.forEachIndexed { index, item ->
                    if (index > 0) append(", ")
                    appendJson(item, indent)
                }
                append("]")
            }
            else -> appendQuoted(value.toString())
        }
    }

    internal fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Any? {
            val value = parseValue()
            skipWhitespace()
            require(index == text.length) { "Unexpected trailing content in settings export." }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            require(index < text.length) { "Unexpected end of settings export." }
            return when (text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                expect('}')
                return map
            }
            while (true) {
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                if (peek('}')) {
                    expect('}')
                    return map
                }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) {
                expect(']')
                return list
            }
            while (true) {
                list += parseValue()
                skipWhitespace()
                if (peek(']')) {
                    expect(']')
                    return list
                }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < text.length) { "Invalid string escape." }
                        result.append(
                            when (val escaped = text[index++]) {
                                '"', '\\', '/' -> escaped
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    require(index + 4 <= text.length) { "Invalid unicode escape." }
                                    text.substring(index, index + 4).toInt(16).toChar().also {
                                        index += 4
                                    }
                                }
                                else -> error("Invalid string escape.")
                            },
                        )
                    }
                    else -> result.append(char)
                }
            }
            error("Unterminated string.")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            require(text.startsWith(literal, index)) { "Invalid JSON literal." }
            index += literal.length
            return value
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek('-')) index++
            while (index < text.length && text[index].isDigit()) index++
            val isDecimal = index < text.length && text[index] == '.'
            if (isDecimal) {
                index++
                while (index < text.length && text[index].isDigit()) index++
            }
            val hasExponent = index < text.length && (text[index] == 'e' || text[index] == 'E')
            if (hasExponent) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                while (index < text.length && text[index].isDigit()) index++
            }
            val token = text.substring(start, index)
            require(token.isNotBlank() && token != "-") { "Invalid number." }
            return if (isDecimal || hasExponent) token.toDouble() else token.toLong()
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun expect(char: Char) {
            skipWhitespace()
            require(index < text.length && text[index] == char) { "Expected '$char' in settings export." }
            index++
        }

        private fun peek(char: Char): Boolean = index < text.length && text[index] == char
    }

    private const val JSON_INDENT = 2
}

private object YamlCodec {
    fun write(root: Map<String, Any?>): String = buildString {
        append("format: ")
        appendYamlScalar(root["format"])
        append('\n')
        append("version: ${root["version"]}\n")
        append("preferences:\n")
        @Suppress("UNCHECKED_CAST")
        (root["preferences"] as? Map<String, Any?>).orEmpty().forEach { (key, value) ->
            appendYamlEntry("  ", key, value)
        }
        val importedWords = root["imported_glide_words"] as? List<*> ?: emptyList<Any>()
        if (importedWords.isEmpty()) {
            append("imported_glide_words: []\n")
        } else {
            append("imported_glide_words:\n")
            appendYamlList("  ", importedWords)
        }
        append("glide_user_language_model: ")
        appendYamlScalar(root["glide_user_language_model"])
        append('\n')
    }

    fun parse(text: String): Map<String, Any?> {
        val root = linkedMapOf<String, Any?>()
        var section: String? = null
        var listKey: String? = null
        val preferences = linkedMapOf<String, Any?>()
        val importedWords = mutableListOf<String>()
        text.lineSequence().forEach { rawLine ->
            if (rawLine.isBlank()) return@forEach
            val indent = rawLine.takeWhile { it == ' ' }.length
            val line = rawLine.trim()
            if (line.startsWith("#")) return@forEach
            when {
                indent == 0 -> {
                    val (key, value) = splitYamlEntry(line)
                    section = if (value == null) key else null
                    listKey = null
                    when {
                        key == "preferences" -> root[key] = preferences
                        key == "imported_glide_words" -> root[key] = importedWords
                        value != null -> root[key] = parseYamlScalar(value)
                    }
                }
                section == "preferences" && indent == 2 -> {
                    val (key, value) = splitYamlEntry(line)
                    if (value == null) {
                        val list = mutableListOf<String>()
                        preferences[key] = list
                        listKey = key
                    } else {
                        preferences[key] = parseYamlScalar(value)
                        listKey = null
                    }
                }
                section == "preferences" && indent == 4 && line.startsWith("- ") -> {
                    val key = listKey ?: return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val list = preferences[key] as? MutableList<String> ?: return@forEach
                    list += parseYamlScalar(line.removePrefix("- "))?.toString().orEmpty()
                }
                section == "imported_glide_words" && indent == 2 && line.startsWith("- ") -> {
                    importedWords += parseYamlScalar(line.removePrefix("- "))?.toString().orEmpty()
                }
            }
        }
        root.putIfAbsent("preferences", preferences)
        root.putIfAbsent("imported_glide_words", importedWords)
        return root
    }

    private fun StringBuilder.appendYamlEntry(indent: String, key: String, value: Any?) {
        when (value) {
            is Iterable<*> -> {
                append(indent)
                append(key)
                val items = value.toList()
                if (items.isEmpty()) {
                    append(": []\n")
                } else {
                    append(":\n")
                    appendYamlList("$indent  ", items)
                }
            }
            else -> {
                append(indent)
                append(key)
                append(": ")
                appendYamlScalar(value)
                append('\n')
            }
        }
    }

    private fun StringBuilder.appendYamlList(indent: String, values: List<*>) {
        if (values.isEmpty()) {
            append(indent)
            append("[]\n")
            return
        }
        values.forEach { value ->
            append(indent)
            append("- ")
            appendYamlScalar(value)
            append('\n')
        }
    }

    private fun StringBuilder.appendYamlScalar(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean, is Number -> append(value)
            else -> JsonCodec.run { appendQuoted(value.toString()) }
        }
    }

    private fun splitYamlEntry(line: String): Pair<String, String?> {
        val separator = line.indexOf(':')
        require(separator >= 0) { "Invalid YAML settings export line." }
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
        return key to value
    }

    private fun parseYamlScalar(value: String): Any? {
        return when {
            value == "[]" -> emptyList<String>()
            value.startsWith("\"") || value.startsWith("[") -> JsonCodec.parse("{\"value\":$value}")["value"]
            value == "null" -> null
            value == "true" -> true
            value == "false" -> false
            value.contains('.') -> value.toDoubleOrNull() ?: value
            else -> value.toLongOrNull() ?: value
        }
    }
}

private const val SETTINGS_EXPORT_FORMAT_ID = "org.leetboard.ime.settings"
private const val SETTINGS_EXPORT_VERSION = 1
