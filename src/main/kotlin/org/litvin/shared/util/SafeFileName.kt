package org.litvin.shared.util

/** Makes a correct Windows file name from a text that the user typed, for example a project name. */
object SafeFileName {
    // Windows does not permit these characters in a file name.
    private const val INVALID_CHARACTERS = "<>:\"/\\|?*"
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    /**
     * Replaces each character that Windows does not permit with "_". Removes the periods and spaces at the end.
     * Adds "_" to a reserved name such as "CON" or "CON.txt". Returns [fallback] when no characters stay.
     */
    fun of(text: String, fallback: String): String {
        val replaced = text.map { if (it in INVALID_CHARACTERS || it.isISOControl()) '_' else it }.joinToString("")
        val trimmed = replaced.trim().trimEnd('.', ' ')
        if (trimmed.isEmpty()) return fallback
        // Windows reserves "CON" also with an extension, for example "CON.txt".
        val stem = trimmed.substringBefore('.')
        return if (stem.trimEnd().uppercase() in RESERVED_NAMES) stem + "_" + trimmed.substring(stem.length) else trimmed
    }
}
