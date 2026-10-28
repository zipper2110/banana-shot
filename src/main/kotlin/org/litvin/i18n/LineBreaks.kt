package org.litvin.i18n

import java.text.BreakIterator
import java.util.Locale

/**
 * The line breaks inside a word of Chinese or Japanese text (B-11 E8-T4). These languages have no spaces between
 * words, so a line can break between most characters.
 *
 * The rules are the line break rules of [BreakIterator]: no line starts with a closing mark ("、", "。", "」", "）",
 * "ー"), and no line ends with an opening mark ("「", "（"). A Latin word or a number inside the text ("BananaShot",
 * "1080p") stays one piece. A word without Chinese or Japanese characters (also a Korean word, because Korean has
 * spaces) stays one piece, so the other languages break only at spaces, as before.
 */
object LineBreaks {
    /**
     * The pieces of [word] between its line break positions. A line can break after each piece. The pieces join
     * without a space. A space at the end of [word] stays on the last piece.
     */
    fun pieces(word: String): List<String> {
        if (word.none(::isCjk)) return listOf(word)
        val iterator = BreakIterator.getLineInstance(Locale.JAPANESE)
        iterator.setText(word)
        val pieces = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            pieces += word.substring(start, end)
            start = end
            end = iterator.next()
        }
        return pieces.ifEmpty { listOf(word) }
    }

    /** The positions in [text] where a line can break between two Chinese or Japanese characters. */
    fun cjkBreaks(text: String): Set<Int> {
        if (text.none(::isCjk)) return emptySet()
        val iterator = BreakIterator.getLineInstance(Locale.JAPANESE)
        iterator.setText(text)
        val breaks = mutableSetOf<Int>()
        var position = iterator.next()
        while (position != BreakIterator.DONE) {
            if (position < text.length && !text[position - 1].isWhitespace()) breaks += position
            position = iterator.next()
        }
        return breaks
    }

    /** The closing marks that stay on the line of the character before them. */
    private const val CLOSING_MARKS = "、。，．・：；？！ー」』）】〉》〕｝”’…"

    /**
     * True when [text] is one Chinese or Japanese character, with or without closing marks after it ("み", "ん。").
     * A paragraph whose last line has only this character looks wrong, so the line before gives the last line its
     * last piece.
     */
    fun isLoneCharacter(text: String): Boolean {
        val core = text.trimEnd().trimEnd { it in CLOSING_MARKS }
        return core.isNotEmpty() && core.codePointCount(0, core.length) == 1 && isCjk(core[0])
    }

    /** True when the line break piece [piece] has only Chinese or Japanese characters. */
    fun isCjkPiece(piece: String): Boolean = piece.isNotEmpty() && piece.all(::isCjk)

    /** A Chinese character, a kana, or a mark of the Chinese and Japanese punctuation or the full-width forms. */
    fun isCjk(char: Char): Boolean = when (Character.UnicodeBlock.of(char)) {
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
        Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
        -> true
        else -> false
    }
}
