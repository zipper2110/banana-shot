package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.i18n.tr
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

/**
 * The notice under the points list when points after the end of the match have a winner. The score does not count
 * those points. Maybe the match has more sets than the format, or a point before the end has a wrong winner.
 * The two buttons open the Scoring settings and go to the match point.
 * The notice shows only while such points exist; it has no close button.
 */
internal class MatchEndNotice(
    onChangeFormat: () -> Unit,
    onGoToMatchPoint: () -> Unit,
) : JPanel(null), HeightForWidth {
    private val message = WrapText("", UiKit.font(12f), Palette.YELLOW_TEXT, 1.45f).apply {
        name = "points-match-end-notice-text"
    }
    val changeFormatButton = UiButton(tr("points.matchEnd.changeFormat"), Material2MZ.TUNE, UiButton.Kind.SECONDARY, UiButton.SMALL_HEIGHT, 12f).apply {
        name = "points-match-end-change-format"
        addActionListener { onChangeFormat() }
    }
    val goToMatchPointButton = UiButton(tr("points.matchEnd.goToMatchPoint"), Material2AL.FLAG, UiButton.Kind.SECONDARY, UiButton.SMALL_HEIGHT, 12f).apply {
        name = "points-match-end-go-to-match-point"
        addActionListener { onGoToMatchPoint() }
    }

    init {
        name = "points-match-end-notice"
        isOpaque = false
        isVisible = false
        add(message)
        add(changeFormatButton)
        add(goToMatchPointButton)
        addComponentListener(object : ComponentAdapter() {
            // The height depends on the width. A new width can need a new height.
            override fun componentResized(e: ComponentEvent) {
                if (heightForWidth(width) != height) revalidate()
            }
        })
    }

    /** The plain text of the message, for tests. */
    val text: String get() = message.text

    /**
     * Shows the notice for the match point with [matchPointNumber] (1 is the first point) and [scoredAfter] points
     * with a winner after it. A [scoredAfter] of 0 hides the notice.
     */
    fun show(matchPointNumber: Int, scoredAfter: Int) {
        val visible = scoredAfter > 0
        if (visible) {
            message.runs = listOf(
                TextRun(tr("points.matchEnd.title") + "\n", UiKit.font(12f, UiKit.Weight.BOLD), Palette.YELLOW_TEXT),
                TextRun(
                    tr("points.matchEnd.message", "number" to matchPointNumber, "count" to scoredAfter),
                    UiKit.font(12f),
                    Palette.YELLOW_TEXT,
                ),
            )
        }
        if (visible != isVisible) {
            isVisible = visible
            parent?.revalidate()
        }
        revalidate()
        repaint()
    }

    private fun innerWidth(width: Int) = (width - 2 * MARGIN - 2 * PAD).coerceAtLeast(1)

    /** True when the two buttons do not fit side by side. */
    private fun stacked(width: Int): Boolean =
        changeFormatButton.preferredSize.width + GAP + goToMatchPointButton.preferredSize.width > innerWidth(width)

    override fun heightForWidth(width: Int): Int {
        val buttons = if (stacked(width)) 2 * UiButton.SMALL_HEIGHT + GAP else UiButton.SMALL_HEIGHT
        return 2 * MARGIN + 2 * PAD + message.heightForWidth(innerWidth(width)) + GAP + buttons
    }

    override fun getPreferredSize(): Dimension {
        val w = if (width > 0) width else parent?.width?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        return Dimension(w, heightForWidth(w))
    }

    override fun doLayout() {
        val x = MARGIN + PAD
        val w = innerWidth(width)
        val textH = message.heightForWidth(w)
        message.setBounds(x, MARGIN + PAD, w, textH)
        val y = MARGIN + PAD + textH + GAP
        val first = changeFormatButton.preferredSize.width
        if (stacked(width)) {
            changeFormatButton.setBounds(x, y, w, UiButton.SMALL_HEIGHT)
            goToMatchPointButton.setBounds(x, y + UiButton.SMALL_HEIGHT + GAP, w, UiButton.SMALL_HEIGHT)
        } else {
            changeFormatButton.setBounds(x, y, first, UiButton.SMALL_HEIGHT)
            goToMatchPointButton.setBounds(x + first + GAP, y, goToMatchPointButton.preferredSize.width, UiButton.SMALL_HEIGHT)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, MARGIN, MARGIN, width - 2 * MARGIN, height - 2 * MARGIN, RADIUS, Palette.YELLOW_TINT, Palette.YELLOW_LINE)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val MARGIN = 10
        const val PAD = 10
        const val GAP = 8
        const val RADIUS = 6
        const val DEFAULT_WIDTH = 360
    }
}
