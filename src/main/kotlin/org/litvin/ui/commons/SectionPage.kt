package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.math.max

/**
 * A page of text and controls for a tool window, for example a section of the More window.
 * The look comes from design/dialogs-redesign/more.html: a title, and then cards.
 * Each [subheading] starts a new card. The items before the first subheading go into a card without a title.
 */
internal open class SectionPage(title: String) : ToolPage() {
    private var card: ToolCard? = null

    init {
        column.add(ToolPage.title(title), gapAfter = 16)
    }

    private fun currentCard(): ToolCard = card ?: ToolCard().also {
        card = it
        column.add(it, gapAfter = 12)
    }

    /** Starts a new card with [text] as its title. */
    fun subheading(text: String): JLabel = JLabel(text).apply {
        font = UiKit.font(13.5f, UiKit.Weight.SEMIBOLD)
        foreground = UiKit.FG
    }.also {
        card = null
        addItem(it)
    }

    fun paragraph(text: String, secondary: Boolean = false): WrapText =
        WrapText(text, UiKit.font(13f), if (secondary) UiKit.FG_3 else UiKit.FG_2, 1.55f, MAX_WIDTH)
            .also { addItem(it) }

    /** A line for the result of an action, directly under its button. The line is hidden while it has no text. */
    fun statusLine(color: Color = BAD): StatusLine = StatusLine(color).also { addItem(it) }

    /**
     * A row with a fixed-width label on the left and a value on the right. The value can be selected and copied.
     * A file path shows in a monospace font.
     */
    fun valueRow(label: String, value: String, mono: Boolean = looksLikePath(value)): JTextArea {
        val valueArea = JTextArea(value).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = false
            font = if (mono) MonoFont.of(12f) else UiKit.font(12.5f)
            foreground = UiKit.FG
            caretColor = UiKit.FG
            selectionColor = Color(161, 254, 0, 70)
            selectedTextColor = UiKit.FG
            border = BorderFactory.createEmptyBorder()
        }
        addItem(ValueRow(label, valueArea))
        return valueArea
    }

    fun buttonRow(vararg buttons: JComponent): JPanel = FlowRow(buttons.toList()).also { addItem(it) }

    /** Adds [component] to the current card. */
    fun addItem(component: JComponent, gapAfter: Int? = null) {
        currentCard().add(component, gapAfter)
    }

    /** A status line. Setting [text] shows or hides the line. */
    class StatusLine(color: Color) : WrapText("", UiKit.font(12f), color, 1.45f, MAX_WIDTH) {
        var color: Color = color
            set(value) {
                field = value
                text = text
            }

        override var text: String
            get() = super.text
            set(value) {
                runs = listOf(TextRun(value, UiKit.font(12f), color))
                isVisible = value.isNotEmpty()
                parent?.revalidate()
                repaint()
            }

        init {
            isVisible = false
        }
    }

    /** A label in a column of [LABEL_WIDTH] px and a wrapped value on the right. */
    private class ValueRow(label: String, private val value: JTextArea) : JPanel(null), HeightForWidth {
        private val key = JLabel(label).apply {
            font = UiKit.font(12.5f)
            foreground = UiKit.FG_3
        }

        init {
            isOpaque = false
            add(key)
            add(value)
        }

        private fun valueWidth(width: Int) = max(40, width - LABEL_WIDTH - GAP)

        override fun heightForWidth(width: Int) = max(key.preferredSize.height, heightAtWidth(value, valueWidth(width)))
        override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 300))

        override fun doLayout() {
            val w = valueWidth(width)
            val valueHeight = heightAtWidth(value, w)
            // The label and the first line of the value have the same baseline.
            val keyHeight = key.preferredSize.height
            val shift = value.getBaseline(w, valueHeight) - key.getBaseline(LABEL_WIDTH, keyHeight)
            key.setBounds(0, max(0, shift), LABEL_WIDTH, keyHeight)
            value.setBounds(LABEL_WIDTH + GAP, max(0, -shift), w, valueHeight)
        }
    }

    /** Buttons in a row with an 8 px gap. The buttons go to the next line when the row is too narrow. */
    private class FlowRow(private val items: List<JComponent>) : JPanel(null), HeightForWidth {
        init {
            isOpaque = false
            items.forEach { add(it) }
        }

        private fun place(width: Int, apply: Boolean): Int {
            var x = 0
            var y = 0
            var lineHeight = 0
            items.forEach { item ->
                val size = item.preferredSize
                if (x > 0 && x + size.width > width) {
                    x = 0
                    y += lineHeight + GAP_Y
                    lineHeight = 0
                }
                if (apply) item.setBounds(x, y, size.width, size.height)
                x += size.width + GAP_X
                lineHeight = max(lineHeight, size.height)
            }
            return y + lineHeight
        }

        override fun heightForWidth(width: Int) = place(width, apply = false)
        override fun getPreferredSize() = Dimension(items.sumOf { it.preferredSize.width + GAP_X }, heightForWidth(if (width > 0) width else Int.MAX_VALUE))
        override fun doLayout() {
            place(width, apply = true)
        }
    }

    companion object {
        private const val LABEL_WIDTH = 90
        private const val GAP = 12
        private const val GAP_X = 8
        private const val GAP_Y = 8

        /** The color of a status line that tells about a problem. */
        val BAD = Color(0xFF9A85)

        fun secondaryButton(text: String, onClick: () -> Unit): JButton = secondaryButton(text, null, onClick)

        /** A small button of a page, with an optional [ikon] in front of the text. */
        fun secondaryButton(text: String, ikon: Ikon?, onClick: () -> Unit): JButton =
            UiButton(text, ikon, UiButton.Kind.SECONDARY, UiButton.SMALL_HEIGHT, 12f).apply { addActionListener { onClick() } }

        private fun looksLikePath(value: String) =
            Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(value) || value.startsWith("\\\\") || value.startsWith("/")
    }
}
