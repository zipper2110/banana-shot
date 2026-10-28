package org.litvin.shared.util

import java.text.Normalizer

/**
 * The form of a text that the video draws (B-11 E7-T4).
 *
 * A user can type a letter with an accent as one character ("ё", NFC) or as a letter and a combining mark ("е" and
 * U+0308, NFD). Some scoreboard fonts (Arial Black, Georgia, Trebuchet MS, Ink Free) have no combining marks, so
 * the second form shows an empty box. The video draws each text in the composed form, so these fonts show it.
 */
object DrawnText {
    /** [text] in the composed form (NFC). */
    fun composed(text: String): String =
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text else Normalizer.normalize(text, Normalizer.Form.NFC)

    /** [text] for an ASS subtitle: composed, and without an override block or an escape sequence. */
    fun ass(text: String): String = composed(text)
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
}
