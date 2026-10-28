package org.litvin.ui.commons

import org.litvin.i18n.LineBreaks
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.text.View
import kotlin.math.ceil

/** A part of a text with its own font and color, for example a bold name in a sentence. */
internal data class TextRun(val text: String, val font: Font, val color: Color)

/**
 * A text that wraps between words, and between the characters of a Chinese or Japanese text ([LineBreaks]).
 * The parts ([TextRun]) can have different fonts and colors. A "\n" in a part starts a new line. The height depends on the width: a parent that knows the width
 * asks [heightForWidth]. Before the first layout, the component uses [wrapWidth].
 */
internal open class WrapText(
    runs: List<TextRun>,
    private val lineFactor: Float = 1.5f,
    var wrapWidth: Int = 360,
) : JComponent(), HeightForWidth {
    constructor(text: String, font: Font, color: Color, lineFactor: Float = 1.5f, wrapWidth: Int = 360) :
        this(listOf(TextRun(text, font, color)), lineFactor, wrapWidth)

    private class Piece(val text: String, val run: TextRun, val width: Float, val newLine: Boolean)

    var runs: List<TextRun> = runs
        set(value) {
            field = value
            pieces = split(value)
            revalidate()
            repaint()
        }

    private var pieces = split(runs)

    /** The plain text, for tests and the accessibility name. */
    open val text: String get() = runs.joinToString("") { it.text }

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val lineHeight get() = (runs.maxOfOrNull { it.font.size2D } ?: 13f) * lineFactor

    private fun split(runs: List<TextRun>): List<Piece> = runs.flatMap { run ->
        run.text.split('\n').flatMapIndexed { index, part ->
            // Each word keeps the space after it, so the pieces of one line join without extra spaces.
            // A Chinese or Japanese word breaks between its characters (B-11 E8-T4).
            val words = Regex("\\S+\\s*|\\s+").findAll(part).flatMap { LineBreaks.pieces(it.value) }.toList()
            val list = words.map { Piece(it, run, UiKit.textWidth(it, run.font), false) }
            if (index > 0) listOf(Piece("", run, 0f, true)) + list else list
        }
    }

    private fun lines(width: Int): List<List<Piece>> {
        val lines = mutableListOf(mutableListOf<Piece>())
        var x = 0f
        var paragraphStart = 0
        pieces.forEach { whole ->
            if (whole.newLine) {
                keepLastCharacterCompany(lines, paragraphStart)
                lines += mutableListOf<Piece>()
                paragraphStart = lines.lastIndex
                x = 0f
                return@forEach
            }
            fitted(whole, width).forEach { piece ->
                val visible = UiKit.textWidth(piece.text.trimEnd(), piece.run.font)
                if (x > 0f && x + visible > width) {
                    lines += mutableListOf<Piece>()
                    x = 0f
                }
                lines.last() += piece
                x += piece.width
            }
        }
        keepLastCharacterCompany(lines, paragraphStart)
        return lines
    }

    /**
     * A Chinese or Japanese paragraph does not end with one character alone on its last line (B-11 E8-T4). The line
     * before gives the last line its last piece.
     */
    private fun keepLastCharacterCompany(lines: MutableList<MutableList<Piece>>, paragraphStart: Int) {
        if (lines.size - paragraphStart < 2) return
        val last = lines.last()
        val previous = lines[lines.lastIndex - 1]
        if (!LineBreaks.isLoneCharacter(last.joinToString("") { it.text })) return
        if (previous.size < 2 || !LineBreaks.isCjkPiece(previous.last().text)) return
        last.add(0, previous.removeAt(previous.lastIndex))
    }

    /** The piece, or its parts when the word is wider than [width] (for example a long German compound noun). */
    private fun fitted(piece: Piece, width: Int): List<Piece> =
        splitToWidth(piece.text, piece.run.font, width.toFloat()).let { parts ->
            if (parts.size == 1) listOf(piece) else parts.map { Piece(it, piece.run, UiKit.textWidth(it, piece.run.font), false) }
        }

    /** The text of each line at [width], for tests. */
    internal fun lineTexts(width: Int): List<String> = lines(width).map { line -> line.joinToString("") { it.text }.trimEnd() }

    override fun heightForWidth(width: Int): Int = ceil(lines(width).size * lineHeight).toInt()

    private val currentWidth get() = if (width > 0) width else wrapWidth

    override fun getPreferredSize() = Dimension(wrapWidth, heightForWidth(currentWidth))
    override fun getMinimumSize() = Dimension(0, preferredSize.height)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            lines(width).forEachIndexed { index, line ->
                var x = 0f
                line.forEach { piece ->
                    UiKit.drawText(g2, piece.text, piece.run.font, piece.run.color, x, index * lineHeight, lineHeight)
                    x += piece.width
                }
            }
        } finally {
            g2.dispose()
        }
    }
}

/**
 * Splits a word that is wider than [width] into parts that fit (B-11 section 7). A part has at least one character,
 * so a very narrow width does not loop. A space at the end of [text] stays on the last part and does not count.
 * A word that fits, and any word when [width] is zero or less, gives one part.
 */
internal fun splitToWidth(text: String, font: Font, width: Float): List<String> {
    if (width <= 0f || UiKit.textWidth(text.trimEnd(), font) <= width) return listOf(text)
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = nextCharEnd(text, start)
        while (end < text.length && UiKit.textWidth(text.substring(start, nextCharEnd(text, end)).trimEnd(), font) <= width) {
            end = nextCharEnd(text, end)
        }
        parts += text.substring(start, end)
        start = end
    }
    return parts
}

/** The index after the character at [index]. A surrogate pair is one character. */
private fun nextCharEnd(text: String, index: Int): Int = index + Character.charCount(text.codePointAt(index))

/**
 * The height of [component] at [width]. It uses [HeightForWidth] when the component has it,
 * the text view for a wrapping [JTextArea], and the preferred height for other components.
 */
internal fun heightAtWidth(component: Component, width: Int): Int = when {
    component is HeightForWidth -> component.heightForWidth(width)
    component is JTextArea && component.lineWrap -> {
        val insets = component.insets
        val view = component.ui.getRootView(component)
        view.setSize((width - insets.left - insets.right).coerceAtLeast(1).toFloat(), Float.MAX_VALUE)
        ceil(view.getPreferredSpan(View.Y_AXIS)).toInt() + insets.top + insets.bottom
    }
    else -> component.preferredSize.height
}
