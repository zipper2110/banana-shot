package org.litvin.i18n

/**
 * The test language `qps` (B-11 plan, section 12). It changes each English text, for example "Export" to
 * "[Éxƥôŕţ ~~]": accented letters, about 40% more length, and brackets. A text that still shows in plain English
 * is hard coded. A text without its closing bracket is cut.
 *
 * Only the literal parts change. The placeholders and the ICU syntax stay. The letters are from Latin-1 and Latin
 * Extended-A, so that the scoreboard fonts can show them.
 */
object PseudoLocalization {
    /** The system property that switches on `qps`: `-Dbananashot.language=qps`. */
    const val CODE = "qps"

    private val letters: Map<Char, Char> = mapOf(
        'a' to 'á', 'b' to 'b', 'c' to 'ç', 'd' to 'ď', 'e' to 'é', 'f' to 'f', 'g' to 'ğ', 'h' to 'ĥ', 'i' to 'í',
        'j' to 'ĵ', 'k' to 'ķ', 'l' to 'ĺ', 'm' to 'm', 'n' to 'ñ', 'o' to 'ô', 'p' to 'p', 'q' to 'q', 'r' to 'ŕ',
        's' to 'š', 't' to 'ţ', 'u' to 'ü', 'v' to 'v', 'w' to 'ŵ', 'x' to 'x', 'y' to 'ý', 'z' to 'ž',
        'A' to 'Á', 'B' to 'B', 'C' to 'Ç', 'D' to 'Ď', 'E' to 'É', 'F' to 'F', 'G' to 'Ğ', 'H' to 'Ĥ', 'I' to 'Í',
        'J' to 'Ĵ', 'K' to 'Ķ', 'L' to 'Ĺ', 'M' to 'M', 'N' to 'Ñ', 'O' to 'Ö', 'P' to 'P', 'Q' to 'Q', 'R' to 'Ŕ',
        'S' to 'Š', 'T' to 'Ţ', 'U' to 'Ü', 'V' to 'V', 'W' to 'Ŵ', 'X' to 'X', 'Y' to 'Ý', 'Z' to 'Ž',
    )

    /** Changes the letters of a literal part. */
    fun transform(text: String): String {
        val result = StringBuilder(text.length)
        text.forEach { c ->
            result.append(letters[c] ?: c)
        }
        return result.toString()
    }

    /** Adds the brackets and the extra length to a full text. Markup and empty texts stay. */
    fun wrap(text: String): String {
        if (text.isBlank() || text.startsWith("<html>")) return text
        val padding = "~".repeat(((text.length * 0.4).toInt()).coerceAtLeast(1))
        return "[$text $padding]"
    }
}
