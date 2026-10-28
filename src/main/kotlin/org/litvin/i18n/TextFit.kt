package org.litvin.i18n

import java.text.BreakIterator
import java.util.Locale

/**
 * The fit of a text in its box in the video (B-11 E3-T19).
 *
 * A text that is too wide first gets a smaller font, down to [MIN_SCALE] of its size. A text that is still too wide
 * is cut with "…". The cut is at a grapheme boundary, so an emoji or a letter with a combining accent stays complete.
 * If not even "…" fits, the box shows no text.
 *
 * The code that makes the video texts gives the width function, so this object does not depend on a font. The
 * export, the preview, and the style pictures use the same scene, thus the same fit result.
 */
object TextFit {
    /** The smallest font, as a part of the normal size. */
    const val MIN_SCALE = 0.8

    /** The part of the box that the text does not use. The Java2D width and the libass width can be a little different. */
    const val MARGIN = 0.05

    const val ELLIPSIS = "…"

    /** The cut mark for a font without the "…" letter. */
    const val ASCII_ELLIPSIS = "..."

    private const val STEP = 0.5

    /** The result of [fit]: the [text] to draw and its font [size]. [normalSize] is the size before the fit. */
    data class Fitted(val text: String, val size: Double, val normalSize: Double, val cut: Boolean = false) {
        val shrunk: Boolean get() = size < normalSize - 1e-9
    }

    private val recorded = ThreadLocal<MutableList<Fitted>?>()

    /**
     * Fits [text] and the [suffix] after it in [maxWidth]. [width] gives the width of a text at a font size. The
     * [suffix] (for example " +12") is never cut. [hasEllipsis] is false when the font has no "…" letter. The cut then
     * uses "...".
     */
    fun fit(
        text: String,
        size: Double,
        maxWidth: Double,
        width: (String, Double) -> Double,
        suffix: String = "",
        hasEllipsis: Boolean = true,
    ): Fitted = record(compute(text, size, maxWidth, width, suffix, hasEllipsis))

    private fun compute(text: String, size: Double, maxWidth: Double, width: (String, Double) -> Double, suffix: String, hasEllipsis: Boolean): Fitted {
        if (text.isEmpty() && suffix.isEmpty() || maxWidth >= Double.MAX_VALUE) return Fitted(text + suffix, size, size)
        val limit = maxWidth * (1 - MARGIN)
        fun fits(shown: String, at: Double) = width(shown + suffix, at) <= limit
        if (fits(text, size)) return Fitted(text + suffix, size, size)

        val minSize = size * MIN_SCALE
        var fitted = size
        while (fitted > minSize && !fits(text, fitted)) fitted = maxOf(minSize, fitted - STEP)
        if (fits(text, fitted)) return Fitted(text + suffix, fitted, size)

        val mark = if (hasEllipsis) ELLIPSIS else ASCII_ELLIPSIS
        for (end in boundaries(text).reversed()) {
            if (end == text.length) continue
            val shown = text.substring(0, end).trimEnd() + mark
            if (fits(shown, minSize)) return Fitted(shown + suffix, minSize, size, cut = true)
        }
        return Fitted("", minSize, size, cut = true)
    }

    /**
     * [text], or its start with "…" when it has more than [maxChars] chars. The cut is at a grapheme boundary.
     * The result has [maxChars] chars or fewer.
     */
    fun cutToChars(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return takeWhole(text, maxChars - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }

    /** The longest start of [text] with [maxChars] chars or fewer that ends at a grapheme boundary. */
    fun takeWhole(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val end = boundaries(text).lastOrNull { it <= maxChars } ?: 0
        return text.substring(0, end)
    }

    /** The grapheme boundaries of [text], from 0 to the length. */
    private fun boundaries(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        return generateSequence(iterator.first()) { iterator.next().takeIf { it != BreakIterator.DONE } }.toList()
    }

    /** Runs [block] and returns its result and the fit results in it that have a smaller font or a cut. The fit test uses it. */
    fun <T> changedFits(block: () -> T): Pair<T, List<Fitted>> {
        val previous = recorded.get()
        val list = mutableListOf<Fitted>()
        recorded.set(list)
        try {
            return block() to list.toList()
        } finally {
            recorded.set(previous)
        }
    }

    private fun record(fitted: Fitted): Fitted {
        if (fitted.shrunk || fitted.cut) recorded.get()?.add(fitted)
        return fitted
    }
}
