package org.litvin.i18n

import java.text.Normalizer
import java.util.Locale

/**
 * The formats of the texts that a program reads (B-11 plan, section 5, "Numbers for machines"): FFmpeg arguments,
 * ASS files, saved files, hex colors, and the time codes. They use `Locale.ROOT`, so the Windows regional format
 * does not change them. For example, `de-DE` does not give a decimal comma, and `ar-EG` does not give other digits.
 * The texts that the user reads use [DisplayFormats].
 */
object MachineFormats {
    /** [pattern] with [args], always with the digits 0–9 and "." as the decimal separator. */
    fun format(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)

    /**
     * [text] with the full-width forms ("１：２３") and the digits of other scripts ("١:٢٣") changed to the ASCII
     * forms ("1:23"). A reader of a text that the user typed uses it. Thus each digit has the same value in all
     * scripts.
     */
    fun asciiDigits(text: String): String {
        val compatible = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val result = StringBuilder(compatible.length)
        var index = 0
        while (index < compatible.length) {
            val codePoint = compatible.codePointAt(index)
            val digit = if (Character.isDigit(codePoint)) Character.digit(codePoint, 10) else -1
            if (digit >= 0) result.append('0' + digit) else result.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }
}
