package org.litvin.ui.tabs.stats

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.UiKit
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagLayout
import java.awt.geom.Ellipse2D
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max

/**
 * The view of the tab when it has no statistics to show: no project, no scored points, or a read error.
 * The box is in the center of the tab.
 */
internal class StatsEmptyView(onOpenScoring: () -> Unit) : JPanel(GridBagLayout()) {
    enum class Kind { NO_PROJECT, NO_SCORED_POINTS, ERROR }

    private var kind = Kind.NO_PROJECT
    val title = JLabel("", SwingConstants.CENTER).apply {
        name = "stats-empty"
        font = UiKit.font(16f, UiKit.Weight.BOLD)
        foreground = UiKit.FG
    }
    val text = WrapText("", UiKit.font(13f), UiKit.FG_2, SwingConstants.CENTER, lineSpacing = 1.5f).apply {
        name = "stats-empty-text"
    }
    val openScoring = StatsButton("Open Scoring", Material2AL.ASSIGNMENT_TURNED_IN, lime = true).apply {
        name = "stats-open-scoring"
        addActionListener { onOpenScoring() }
    }
    private val box = object : JPanel(null) {
        init {
            isOpaque = false
            add(title)
            add(text)
            add(openScoring)
        }

        private val textWidth get() = BOX_WIDTH - PAD * 2

        override fun getPreferredSize(): Dimension {
            var h = PAD + ICON_BOX + ICON_GAP + title.preferredSize.height + TITLE_GAP + text.heightForWidth(textWidth)
            if (openScoring.isVisible) h += BUTTON_GAP + openScoring.preferredSize.height
            return Dimension(BOX_WIDTH, h + PAD)
        }

        override fun doLayout() {
            var y = PAD + ICON_BOX + ICON_GAP
            val titleHeight = title.preferredSize.height
            title.setBounds(PAD, y, textWidth, titleHeight)
            y += titleHeight + TITLE_GAP
            val textHeight = text.heightForWidth(textWidth)
            text.setBounds(PAD, y, textWidth, textHeight)
            y += textHeight + BUTTON_GAP
            val button = openScoring.preferredSize
            openScoring.setBounds((width - button.width) / 2, y, button.width, button.height)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val x = (width - ICON_BOX) / 2.0
                val circle = Ellipse2D.Double(x, PAD.toDouble(), ICON_BOX.toDouble(), ICON_BOX.toDouble())
                val error = kind == Kind.ERROR
                g2.color = if (error) UiKit.RED_TINT else UiKit.RAISED
                g2.fill(circle)
                g2.color = if (error) UiKit.RED_LINE else UiKit.LINE_2
                g2.draw(Ellipse2D.Double(x + 0.5, PAD + 0.5, ICON_BOX - 1.0, ICON_BOX - 1.0))
                val ikon = when (kind) {
                    Kind.NO_PROJECT -> Material2AL.FOLDER_OPEN
                    Kind.NO_SCORED_POINTS -> Material2AL.ASSIGNMENT_TURNED_IN
                    Kind.ERROR -> Material2OutlinedAL.ERROR_OUTLINE
                }
                val icon = UiKit.icon(ikon, 28, if (error) UiKit.RED else UiKit.FG_2)
                icon.paintIcon(this, g2, (width - icon.iconWidth) / 2, PAD + (ICON_BOX - icon.iconHeight) / 2)
            } finally {
                g2.dispose()
            }
        }
    }

    init {
        isOpaque = true
        background = UiKit.BG
        add(box)
    }

    fun show(kind: Kind, titleText: String, message: String) {
        this.kind = kind
        title.text = titleText
        text.text = message
        val error = kind == Kind.ERROR
        text.font = if (error) Font("Consolas", Font.PLAIN, 12) else UiKit.font(13f)
        text.color = if (error) StatsColors.ERROR_TEXT else UiKit.FG_2
        openScoring.isVisible = kind == Kind.NO_SCORED_POINTS
        box.revalidate()
        repaint()
    }

    private companion object {
        const val BOX_WIDTH = 420
        const val PAD = 24
        const val ICON_BOX = 56
        const val ICON_GAP = 14
        const val TITLE_GAP = 6
        const val BUTTON_GAP = 16
    }
}

/** A yellow note with an info icon, for example "Based on 180 of 186 points." Each line is a paragraph. */
internal class CoverageNote : JPanel(null), HeightForWidth {
    private val icon = UiKit.icon(Material2OutlinedAL.INFO, 17, StatsColors.NOTE_ICON)
    private val body = WrapText("", UiKit.font(12.5f), StatsColors.NOTE_TEXT)

    /** The lines of the note. The note is hidden when it has no lines. */
    var lines: List<String> = emptyList()
        set(value) {
            field = value
            body.text = value.joinToString("\n")
            isVisible = value.isNotEmpty()
            revalidate()
            repaint()
        }

    init {
        name = "stats-coverage"
        isOpaque = false
        isVisible = false
        add(body)
    }

    private fun bodyWidth(width: Int) = (width - PAD_X * 2 - icon.iconWidth - GAP).coerceAtLeast(0)

    override fun heightForWidth(width: Int) = PAD_Y * 2 + max(body.heightForWidth(bodyWidth(width)), icon.iconHeight)

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 600))

    override fun doLayout() {
        val w = bodyWidth(width)
        body.setBounds(PAD_X + icon.iconWidth + GAP, PAD_Y, w, body.heightForWidth(w))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 6, StatsColors.NOTE_BG, StatsColors.NOTE_LINE)
            icon.paintIcon(this, g2, PAD_X, PAD_Y + 1)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 10
        const val PAD_Y = 7
        const val GAP = 8
    }
}
