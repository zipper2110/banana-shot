package org.litvin.shared.util

/** Makes a correct Windows file name from a text that the user typed, for example a project name. */
object SafeFileName {
    // Windows does not permit these characters in a file name.
    private const val INVALID_CHARACTERS = "<>:\"/\\|?*"
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    /**
     * Replaces each character that Windows does not permit with "_". Removes the periods and spaces at the end.
     * Adds "_" to a reserved name such as "CON" or "CON.txt". Returns [fallback] when no character of [text] stays,
     * for example for "???" or only spaces.
     *
     * The name is in the composed form (NFC), so "é" as one character and "e" with U+0301 give the same name. A
     * letter of any script and an emoji stay. Half of an emoji (a lone surrogate) becomes "_".
     */
    fun of(text: String, fallback: String): String {
        val composed = DrawnText.composed(text)
        val replaced = StringBuilder(composed.length)
        var kept = false
        var index = 0
        while (index < composed.length) {
            val codePoint = composed.codePointAt(index)
            val count = Character.charCount(codePoint)
            val invalid = Character.isISOControl(codePoint) || Character.isSurrogate(composed[index]) && count == 1 ||
                count == 1 && composed[index] in INVALID_CHARACTERS
            if (invalid) {
                replaced.append('_')
            } else {
                replaced.appendCodePoint(codePoint)
                if (codePoint != ' '.code && codePoint != '.'.code && !Character.isWhitespace(codePoint)) kept = true
            }
            index += count
        }
        if (!kept) return fallback
        val trimmed = replaced.toString().trim().trimEnd('.', ' ')
        if (trimmed.isEmpty()) return fallback
        // Windows reserves "CON" also with an extension, for example "CON.txt".
        val stem = trimmed.substringBefore('.')
        return if (stem.trimEnd().uppercase() in RESERVED_NAMES) stem + "_" + trimmed.substring(stem.length) else trimmed
    }
}
