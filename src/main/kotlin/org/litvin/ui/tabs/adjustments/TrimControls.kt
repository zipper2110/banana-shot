package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.TrimRange
import org.litvin.i18n.tr
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.SidePanelHeader
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** The two ends of the trim (B-62). */
enum class TrimEnd { START, END }

/**
 * The Trim sub-tab of the Colors & Transform tab (B-62): a header with the changed count and "Reset all", a card with
 * the length that the video keeps, and a card for each end of the trim. An end card shows the time, a large button
 * that sets the end to the playhead, and a "Go" button that seeks to the end. The trim has no change points.
 * Component IDs: trim-panel, trim-reset, trim-status, trim-summary, trim-start-card, trim-end-card, trim-start-set,
 * trim-end-set, trim-start-go, trim-end-go, trim-outside-banner, trim-hint
 */
class TrimControls(
    private val onSet: (TrimEnd) -> Unit,
    private val onGoTo: (TrimEnd) -> Unit,
    private val onReset: () -> Unit,
) : JPanel(BorderLayout()) {
    internal val resetButton = ResetAllButton("trim-reset", tr("adjust.trim.reset")).apply { addActionListener { onReset() } }

    private val statusLabel = JLabel().apply { name = "trim-status" }
    private val header = SidePanelHeader(tr("adjust.trim"), statusLabel, resetButton)

    internal val summary = TrimSummary()

    internal val cards: Map<TrimEnd, TrimEndCard> = TrimEnd.entries.associateWith { end ->
        TrimEndCard(end, onSet = { onSet(end) }, onGoTo = { onGoTo(end) })
    }

    /** The number of points that the trim cuts off. The banner shows while it is more than 0. */
    private var outsideCount = 0

    /** The yellow banner: the trim cuts off points. The points stay in the project (B-62). */
    internal val outsideBanner = LivePreviewNotice(
        text = { tr("adjust.trim.outside", "count" to outsideCount) },
        ikon = Material2OutlinedMZ.WARNING,
    ).apply {
        name = "trim-outside-banner"
        isVisible = false
    }

    /** The header status: "All at default" or "1 of 2 changed". */
    internal val statusText: String get() = statusLabel.text

    init {
        name = "trim-panel"
        isOpaque = true
        background = Palette.BG
        val hint = WrapText(tr("adjust.trim.hint"), UiKit.font(12f), Palette.FG_3).apply { name = "trim-hint" }
        val column = CardColumn().apply {
            add(summary)
            add(outsideBanner)
            TrimEnd.entries.forEach { add(cards.getValue(it)) }
            add(hint)
        }
        add(header, BorderLayout.NORTH)
        add(column.inScrollPane(), BorderLayout.CENTER)
        render(AdjustmentsV1(), 0L, 0, 0)
    }

    /**
     * Shows the trim of [adjustments] on a source of [durationMs]. A duration of 0 or less is not known yet.
     * [pointCount] is the number of points of the project, and [pointsOutside] the number that the trim cuts off.
     */
    fun render(adjustments: AdjustmentsV1, durationMs: Long, pointCount: Int, pointsOutside: Int) {
        if (pointsOutside != outsideCount || outsideBanner.isVisible != (pointsOutside > 0)) {
            outsideCount = pointsOutside
            outsideBanner.isVisible = pointsOutside > 0
            outsideBanner.revalidate()
            outsideBanner.repaint()
        }
        val range = if (durationMs > 0) TrimRange.of(adjustments, durationMs) else null
        cards.getValue(TrimEnd.START).show(range?.startMs ?: adjustments.trimStartMs ?: 0L, changed = adjustments.trimStartMs != null)
        cards.getValue(TrimEnd.END).show(range?.endMs ?: adjustments.trimEndMs, changed = adjustments.trimEndMs != null)
        summary.show(range, durationMs, pointCount, pointsOutside)
        val changed = listOf(adjustments.trimStartMs, adjustments.trimEndMs).count { it != null }
        header.showChanged(changed, TrimEnd.entries.size)
    }

    internal companion object {
        /** "H:MM:SS", for example "1:23:43". */
        fun clock(ms: Long): String {
            val seconds = ms.coerceAtLeast(0L) / 1000
            val text = StringBuilder().append(seconds / 3600).append(':')
            fun two(value: Long) = text.append(if (value < 10) "0" else "").append(value)
            two(seconds / 60 % 60).append(':')
            two(seconds % 60)
            return text.toString()
        }

        /** "M:SS" under one hour, else "H:MM:SS". For the time that the trim cuts off. */
        fun shortClock(ms: Long): String {
            if (ms >= 3_600_000) return clock(ms)
            val seconds = ms.coerceAtLeast(0L) / 1000
            val rest = seconds % 60
            return "${seconds / 60}:${if (rest < 10) "0" else ""}$rest"
        }

        const val CARD_RADIUS = 10
    }
}

/** The card "The video keeps": the kept length in large text, and the time cut off and the points under it. */
internal class TrimSummary : JComponent() {
    private val labelFont = UiKit.font(12.5f)
    private val bigFont = UiKit.font(32f, UiKit.Weight.BOLD)
    private val lineFont = UiKit.font(12.5f)

    /** The kept length, for example "1:23:43", or "--:--:--" while the source length is not known. */
    var keptText: String = UNKNOWN
        private set

    /** The line under the length, for example "8:57 cut off · all 46 points kept". */
    var detailText: String = ""
        private set

    private var outside = false

    init {
        name = "trim-summary"
        isOpaque = false
    }

    fun show(range: TrimRange?, durationMs: Long, pointCount: Int, pointsOutside: Int) {
        keptText = range?.let { TrimControls.clock(it.durationMs) } ?: UNKNOWN
        val cut = range?.let { tr("adjust.trim.cutOff", "cut" to TrimControls.shortClock(durationMs - it.durationMs)) }
        val points = when {
            pointsOutside > 0 -> tr("adjust.trim.pointsOutside", "count" to pointsOutside)
            pointCount > 0 -> tr("adjust.trim.allKept", "count" to pointCount)
            else -> null
        }
        detailText = listOfNotNull(cut, points).joinToString(" · ")
        outside = pointsOutside > 0
        repaint()
    }

    override fun getPreferredSize() = Dimension(200, HEIGHT_PX)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT_PX)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, TrimControls.CARD_RADIUS, Palette.CARD, Palette.LINE)
            UiKit.drawText(g2, tr("adjust.trim.keeps"), labelFont, Palette.FG_2, PAD, 14f, 18f)
            UiKit.drawText(g2, keptText, bigFont, Palette.FG_STRONG, PAD, 34f, 42f)
            if (detailText.isNotEmpty()) {
                UiKit.drawText(g2, detailText, lineFont, if (outside) Palette.RED_TEXT else Palette.FG_3, PAD, 80f, 18f)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT_PX = 112
        const val PAD = 18f
        const val UNKNOWN = "-:--:--"
    }
}

/**
 * The card of one end of the trim: the name and the time on the first line, and under it a large button that sets
 * the end to the playhead and a "Go" button that seeks to the end. The start button is lime, the end button has a
 * lime border, so the two ends look different.
 */
internal class TrimEndCard(val end: TrimEnd, onSet: () -> Unit, onGoTo: () -> Unit) : JPanel(null) {
    private val key = end.name.lowercase()

    val setButton = UiButton(
        tr(if (end == TrimEnd.START) "adjust.trim.startAtPosition" else "adjust.trim.endAtPosition"),
        kind = if (end == TrimEnd.START) UiButton.Kind.LIME else UiButton.Kind.LIME_OUTLINE,
        buttonHeight = BUTTON_HEIGHT,
        fontSize = 13.5f,
    ).apply {
        name = "trim-$key-set"
        toolTipText = tr("adjust.trim.setHere.tooltip")
        isFocusable = false
        addActionListener { onSet() }
    }

    val goButton = UiButton(tr("adjust.trim.go"), Material2OutlinedAL.ARROW_FORWARD, buttonHeight = BUTTON_HEIGHT).apply {
        name = "trim-$key-go"
        toolTipText = tr("adjust.trim.goTo.tooltip")
        isFocusable = false
        addActionListener { onGoTo() }
    }

    private val label = tr(if (end == TrimEnd.START) "adjust.trim.start" else "adjust.trim.end")
    private val labelFont = UiKit.font(13f)
    private val timeFont = UiKit.font(20f, UiKit.Weight.BOLD)

    /** The shown time, or null while the end of the source is not known. */
    var timeMs: Long? = null
        private set
    private var changed = false

    /** The time text, for example "0:04:12", or "-:--:--" while the time is not known. */
    val timeText: String get() = timeMs?.let { TrimControls.clock(it) } ?: "-:--:--"

    init {
        name = "trim-$key-card"
        isOpaque = false
        add(setButton)
        add(goButton)
    }

    fun show(ms: Long?, changed: Boolean) {
        if (timeMs == ms && this.changed == changed) return
        timeMs = ms
        this.changed = changed
        goButton.isEnabled = ms != null
        repaint()
    }

    override fun getPreferredSize() = Dimension(200, HEIGHT_PX)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT_PX)

    override fun doLayout() {
        val go = goButton.preferredSize.width + 8
        val top = BUTTON_TOP
        goButton.setBounds(width - PAD - go, top, go, BUTTON_HEIGHT)
        setButton.setBounds(PAD, top, width - PAD * 2 - go - GAP, BUTTON_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, TrimControls.CARD_RADIUS, Palette.CARD, Palette.LINE)
            UiKit.drawText(g2, label, labelFont, Palette.FG_2, PAD.toFloat(), 12f, 30f)
            val time = timeText
            val timeColor = if (changed) Palette.FG_STRONG else Palette.FG_3
            UiKit.drawText(g2, time, timeFont, timeColor, width - PAD - UiKit.textWidth(time, timeFont), 12f, 30f)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT_PX = 112
        const val PAD = 18
        const val GAP = 8
        const val BUTTON_TOP = 52
        const val BUTTON_HEIGHT = 42
    }
}
