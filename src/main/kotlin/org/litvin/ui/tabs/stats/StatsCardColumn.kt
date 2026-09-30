package org.litvin.ui.tabs.stats

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.DefaultFillSliderUI
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.math.min

/**
 * The right side of the tab: the statistics card as the exported video shows it, the page buttons,
 * the card transparency and a short help. The column uses all the height; the preview gets smaller
 * in a low window, so that the controls under it stay visible.
 */
internal class StatsCardColumn(
    val preview: StatsCardPreview,
    onPage: (Int) -> Unit,
    onTransparency: (percent: Int, adjusting: Boolean) -> Unit,
) : JPanel(null), Scrollable {
    private val title = JLabel("In the video").apply {
        font = UiKit.font(15f, UiKit.Weight.BOLD)
        foreground = Palette.FG
    }
    val summary = JLabel(" ").apply {
        name = "stats-card-summary"
        font = UiKit.font(12f)
        foreground = Palette.FG_2
    }
    val previous = StatsButton("Previous page", Material2AL.CHEVRON_LEFT).apply {
        name = "stats-preview-previous"
        addActionListener { onPage(preview.page - 1) }
    }
    val next = StatsButton("Next page", Material2AL.CHEVRON_RIGHT, iconAfter = true).apply {
        name = "stats-preview-next"
        addActionListener { onPage(preview.page + 1) }
    }
    val pageLabel = JLabel("", SwingConstants.CENTER).apply {
        name = "stats-preview-page"
        font = UiKit.font(12.5f)
        foreground = Palette.FG_2
    }
    private val transparencyCaption = JLabel("Card transparency").apply {
        font = UiKit.font(12.5f)
        foreground = Palette.FG
        toolTipText = TRANSPARENCY_TIP
    }
    val transparencyValue = JLabel("", SwingConstants.RIGHT).apply {
        name = "stats-card-transparency-value"
        border = BorderFactory.createEmptyBorder(0, 0, 0, 3)
        font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    val transparency = DefaultFillSliderUI.slider(0, StatsSettingsV1.MAX_CARD_TRANSPARENCY_PERCENT, 0).apply {
        name = "stats-card-transparency"
        isFocusable = false
        toolTipText = TRANSPARENCY_TIP
        value = StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT
        addChangeListener {
            transparencyValue.text = "$value %"
            if (!showingTransparency) onTransparency(value, valueIsAdjusting)
        }
    }
    private val solid = scaleLabel("Solid card", SwingConstants.LEFT)
    private val moreVideo = scaleLabel("More video", SwingConstants.RIGHT)
    private val about = AboutSection()

    /** True while the column sets the slider value from the settings. */
    private var showingTransparency = false

    init {
        name = "stats-card-column"
        isOpaque = true
        background = Palette.CARD
        listOf(title, summary, preview, previous, pageLabel, next, transparencyCaption, transparencyValue, transparency, solid, moreVideo, about)
            .forEach { add(it) }
    }

    private fun scaleLabel(text: String, align: Int) = JLabel(text, align).apply {
        // The right padding keeps the last character visible when the text paints wider than measured.
        border = BorderFactory.createEmptyBorder(0, 0, 0, 3)
        font = UiKit.font(11f)
        foreground = Palette.FG_3
    }

    /** Shows the card summary, for example "Match statistics · 7 rows and the momentum chart". */
    fun showSummary(text: String) {
        summary.text = text
    }

    /** Shows the page label and the page buttons. A card with one page has no page buttons. */
    fun showPage(page: Int, count: Int) {
        pageLabel.text = "Page ${page + 1} of $count"
        val paged = count > 1
        if (previous.isVisible != paged) {
            listOf(previous, pageLabel, next).forEach { it.isVisible = paged }
            revalidate()
            repaint()
        }
        previous.isEnabled = page > 0
        next.isEnabled = page < count - 1
    }

    /** Sets the slider without a call to the transparency listener. */
    fun showTransparency(percent: Int) {
        showingTransparency = true
        try {
            transparency.value = percent
        } finally {
            showingTransparency = false
        }
        transparencyValue.text = "$percent %"
    }

    private val viewportHeight get() = (parent as? JViewport)?.height?.takeIf { it > 0 } ?: height.takeIf { it > 0 } ?: 700

    /** The width of the content. The preview grows with the column, but it stays short enough to show the controls. */
    private fun innerWidth(width: Int): Int {
        val maxWidth = max(MIN_MAX_WIDTH, (viewportHeight - RESERVED_HEIGHT) * 16 / 9 + PAD_X * 2)
        return (min(width, maxWidth) - PAD_X * 2).coerceAtLeast(MIN_INNER)
    }

    private fun layoutParts(width: Int, apply: Boolean): Int {
        val inner = innerWidth(width)
        val left = (width - inner - PAD_X * 2).coerceAtLeast(0) / 2 + PAD_X
        var y = PAD_TOP
        fun place(c: java.awt.Component, x: Int, w: Int, h: Int) {
            if (apply) c.setBounds(x, y, w, h)
        }
        val titleHeight = title.preferredSize.height
        place(title, left, inner, titleHeight)
        y += titleHeight + 2
        val summaryHeight = summary.preferredSize.height
        place(summary, left, inner, summaryHeight)
        y += summaryHeight + GAP
        val previewHeight = inner * 9 / 16
        place(preview, left, inner, previewHeight)
        y += previewHeight + GAP
        if (previous.isVisible) {
            val buttonHeight = previous.preferredSize.height
            place(previous, left, previous.preferredSize.width, buttonHeight)
            if (apply) next.setBounds(left + inner - next.preferredSize.width, y, next.preferredSize.width, buttonHeight)
            val labelX = left + previous.preferredSize.width + 8
            place(pageLabel, labelX, (inner - previous.preferredSize.width - next.preferredSize.width - 16).coerceAtLeast(0), buttonHeight)
            y += buttonHeight + GAP
        }
        val captionHeight = transparencyCaption.preferredSize.height
        // The labels get more than their preferred width, because some text paints wider than measured.
        place(transparencyCaption, left, (inner - VALUE_WIDTH).coerceAtLeast(0), captionHeight)
        place(transparencyValue, left + inner - VALUE_WIDTH, VALUE_WIDTH, captionHeight)
        y += captionHeight + 2
        place(transparency, left, inner, SLIDER_HEIGHT)
        y += SLIDER_HEIGHT
        val scaleHeight = solid.preferredSize.height
        place(solid, left, inner / 2, scaleHeight)
        place(moreVideo, left + inner / 2, inner - inner / 2, scaleHeight)
        y += scaleHeight + GAP
        val aboutHeight = about.heightForWidth(inner)
        place(about, left, inner, aboutHeight)
        y += aboutHeight
        return y + PAD_BOTTOM
    }

    override fun getPreferredSize(): Dimension {
        val viewportWidth = (parent as? JViewport)?.width?.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: 520
        return Dimension(viewportWidth, layoutParts(viewportWidth, apply = false))
    }

    override fun doLayout() {
        layoutParts(width, apply = true)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
        max(16, visibleRect.height - 16)
    override fun getScrollableTracksViewportWidth() = true
    // A short column fills the viewport, so its background goes to the bottom of the tab.
    override fun getScrollableTracksViewportHeight() =
        (parent as? JViewport)?.let { it.height > layoutParts(it.width, apply = false) } ?: false

    /** "How the card works": a line that opens and closes the help under it. */
    private inner class AboutSection : JPanel(null) {
        private val toggle = AboutToggle().apply {
            addActionListener {
                open = !open
                paragraphs.forEach { it.isVisible = open }
                this@StatsCardColumn.revalidate()
                this@StatsCardColumn.repaint()
            }
        }
        private val paragraphs = HELP.map { WrapText(it, UiKit.font(12f), Palette.FG_2, lineSpacing = 1.5f) }

        init {
            isOpaque = false
            add(toggle)
            paragraphs.forEach { add(it) }
        }

        fun heightForWidth(width: Int): Int {
            var h = 1 + PAD_TOP_ABOUT + toggle.preferredSize.height
            for (paragraph in paragraphs) if (paragraph.isVisible) h += PARAGRAPH_GAP + paragraph.heightForWidth(width)
            return h
        }

        override fun doLayout() {
            var y = 1 + PAD_TOP_ABOUT
            val toggleHeight = toggle.preferredSize.height
            toggle.setBounds(0, y, min(width, toggle.preferredSize.width), toggleHeight)
            y += toggleHeight
            for (paragraph in paragraphs) {
                if (!paragraph.isVisible) continue
                val h = paragraph.heightForWidth(width)
                paragraph.setBounds(0, y + PARAGRAPH_GAP, width, h)
                y += PARAGRAPH_GAP + h
            }
        }

        override fun paintComponent(g: Graphics) {
            g.color = Palette.LINE
            g.fillRect(0, 0, width, 1)
        }
    }

    /** The line that opens the help: an info icon, the text and a chevron. */
    private class AboutToggle : JButton() {
        var open = true
            set(value) {
                field = value
                getAccessibleContext().accessibleName = if (value) "Close $TEXT" else "Open $TEXT"
                repaint()
            }

        init {
            name = "stats-card-help-toggle"
            plain()
            open = true
        }

        override fun getPreferredSize() = Dimension((ICON + 6 + UiKit.textWidth(TEXT, FONT) + 4 + ICON).toInt() + 2, 22)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val color = if (model.isRollover) Palette.FG else Palette.FG_2
                val info = UiKit.icon(Material2OutlinedAL.INFO, ICON, color)
                info.paintIcon(this, g2, 0, (height - info.iconHeight) / 2)
                val textX = ICON + 6f
                UiKit.drawText(g2, TEXT, FONT, color, textX, 0f, height.toFloat())
                val chevron = UiKit.icon(if (open) Material2AL.EXPAND_LESS else Material2AL.EXPAND_MORE, ICON, color)
                chevron.paintIcon(this, g2, (textX + UiKit.textWidth(TEXT, FONT) + 4).toInt(), (height - chevron.iconHeight) / 2)
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            const val TEXT = "How the card works"
            const val ICON = 17
            val FONT = UiKit.font(12.5f)
        }
    }

    companion object {
        const val TRANSPARENCY_TIP = "How much of the video shows through the card. The exported video uses the same value."
        val HELP = listOf(
            "The exported video shows this card after the last point.",
            "The card shows the rows that you select and that have a value, ${StatsCard.ROWS_PER_PAGE} rows on each page. " +
                "A group of rows stays on one page when possible.",
            "When you select In video for the momentum chart, the last page shows the chart.",
        )
        private const val PAD_X = 20
        private const val PAD_TOP = 16
        private const val PAD_BOTTOM = 20
        private const val GAP = 12
        private const val PAD_TOP_ABOUT = 10
        private const val PARAGRAPH_GAP = 8
        private const val SLIDER_HEIGHT = 22
        private const val VALUE_WIDTH = 60
        private const val MIN_INNER = 280

        /** The content is never narrower than this width when the column has the space. */
        private const val MIN_MAX_WIDTH = 460

        /** The height of the parts under and over the preview, so that the preview keeps them visible. */
        private const val RESERVED_HEIGHT = 296
    }
}
