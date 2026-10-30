package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.shared.util.Timecode
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.TrimRange
import org.litvin.i18n.tr
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.SidePanelHeader
import org.litvin.ui.commons.SliderRows
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JLabel
import javax.swing.JPanel

/** The two ends of the trim (B-60). */
enum class TrimEnd { START, END }

/**
 * The Trim sub-tab of the Colors & Transform tab (B-60): a header with the changed count and "Reset all", and a card
 * with the start and the end of the video. "Set here" sets an end to the playhead. The arrow button seeks to it.
 * The caption of the card shows the length of the trimmed video. The trim has no change points.
 * Component IDs: trim-panel, trim-reset, trim-status, trim-start-row, trim-end-row, trim-start-set, trim-end-set,
 * trim-start-go, trim-end-go
 */
class TrimControls(
    private val onSet: (TrimEnd) -> Unit,
    private val onGoTo: (TrimEnd) -> Unit,
    private val onReset: () -> Unit,
) : JPanel(BorderLayout()) {
    internal val resetButton = ResetAllButton("trim-reset", tr("adjust.trim.reset")).apply { addActionListener { onReset() } }

    private val statusLabel = JLabel().apply { name = "trim-status" }
    private val header = SidePanelHeader(tr("adjust.trim"), statusLabel, resetButton)

    internal val rows: Map<TrimEnd, TrimRow> = TrimEnd.entries.associateWith { end ->
        TrimRow(end, first = end == TrimEnd.START, onSet = { onSet(end) }, onGoTo = { onGoTo(end) })
    }

    private val card = TrimCard(rows.values.toList())

    /** The number of points that the trim cuts off. The banner shows while it is more than 0. */
    private var outsideCount = 0

    /** The yellow banner: the trim cuts off points. The points stay in the project (B-60). */
    internal val outsideBanner = LivePreviewNotice(
        text = { tr("adjust.trim.outside", "count" to outsideCount) },
        ikon = Material2OutlinedMZ.WARNING,
    ).apply {
        name = "trim-outside-banner"
        isVisible = false
    }

    /** The header status: "All at default" or "1 of 2 changed". */
    internal val statusText: String get() = statusLabel.text

    /** The length text in the caption of the card, for example "00:42:10". Empty while the length is not known. */
    internal val lengthText: String get() = card.lengthText

    init {
        name = "trim-panel"
        isOpaque = true
        background = Palette.BG
        val hint = WrapText(tr("adjust.trim.hint"), UiKit.font(12f), Palette.FG_3).apply { name = "trim-hint" }
        val column = CardColumn().apply {
            add(outsideBanner)
            add(card)
            add(hint)
        }
        add(header, BorderLayout.NORTH)
        add(column.inScrollPane(), BorderLayout.CENTER)
        render(AdjustmentsV1(), 0L, 0)
    }

    /**
     * Shows the trim of [adjustments] on a source of [durationMs]. A duration of 0 or less is not known yet.
     * [pointsOutside] is the number of points that the trim cuts off.
     */
    fun render(adjustments: AdjustmentsV1, durationMs: Long, pointsOutside: Int) {
        if (pointsOutside != outsideCount || outsideBanner.isVisible != (pointsOutside > 0)) {
            outsideCount = pointsOutside
            outsideBanner.isVisible = pointsOutside > 0
            outsideBanner.revalidate()
            outsideBanner.repaint()
        }
        val known = durationMs > 0
        val range = if (known) TrimRange.of(adjustments, durationMs) else null
        rows.getValue(TrimEnd.START).show(range?.startMs ?: adjustments.trimStartMs ?: 0L, changed = adjustments.trimStartMs != null)
        rows.getValue(TrimEnd.END).show(range?.endMs ?: adjustments.trimEndMs, changed = adjustments.trimEndMs != null)
        card.lengthText = range?.let { format(it.durationMs) }.orEmpty()
        val changed = listOf(adjustments.trimStartMs, adjustments.trimEndMs).count { it != null }
        header.showChanged(changed, TrimEnd.entries.size)
    }

    internal companion object {
        /** "HH:MM:SS", as the change points card shows the times. */
        fun format(ms: Long): String = Timecode.format(ms.coerceAtLeast(0L)).substring(0, 8)
    }
}

/** One end of the trim: the name, the time, the arrow button that seeks to the time, and "Set here". */
internal class TrimRow(
    val end: TrimEnd,
    private val first: Boolean,
    onSet: () -> Unit,
    onGoTo: () -> Unit,
) : JPanel(null) {
    private val key = end.name.lowercase()

    val setButton = UiButton(tr("adjust.trim.setHere"), Material2OutlinedMZ.MY_LOCATION, UiButton.Kind.QUIET, buttonHeight = 26, fontSize = 12f).apply {
        name = "trim-$key-set"
        toolTipText = tr("adjust.trim.setHere.tooltip")
        isFocusable = false
        addActionListener { onSet() }
    }

    val goButton = RowIconButton(Material2OutlinedAL.ARROW_FORWARD, tr("adjust.trim.goTo.tooltip")).apply {
        name = "trim-$key-go"
        addActionListener { onGoTo() }
    }

    private val label = tr(if (end == TrimEnd.START) "adjust.trim.start" else "adjust.trim.end")
    private val nameFont = UiKit.font(13f)
    private val timeFont = UiKit.font(12.5f)

    /** The shown time, or null while the end of the source is not known. */
    var timeMs: Long? = null
        private set
    private var changed = false

    /** The time text, for example "00:05:12", or "--:--:--" while the time is not known. */
    val timeText: String get() = timeMs?.let { TrimControls.format(it) } ?: UNKNOWN_TIME

    init {
        name = "trim-$key-row"
        isOpaque = false
        add(goButton)
        add(setButton)
    }

    fun show(ms: Long?, changed: Boolean) {
        if (timeMs == ms && this.changed == changed) return
        timeMs = ms
        this.changed = changed
        goButton.isEnabled = ms != null
        repaint()
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, ROW_HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

    override fun doLayout() {
        val set = setButton.preferredSize
        val setX = width - SliderRows.PAD_X - set.width
        setButton.setBounds(setX, (height - set.height) / 2, set.width, set.height)
        val go = goButton.preferredSize
        goButton.setBounds(setX - BUTTON_GAP - go.width, (height - go.height) / 2, go.width, go.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            SliderRows.paintFrame(g2, width, height, first = first, changed = changed)
            UiKit.drawText(g2, label, nameFont, Palette.FG, SliderRows.PAD_X.toFloat(), 0f, height.toFloat())
            val time = timeText
            val timeX = goButton.x - BUTTON_GAP - UiKit.textWidth(time, timeFont)
            UiKit.drawText(g2, time, timeFont, if (changed) Palette.FG else Palette.FG_3, timeX, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val MIN_WIDTH = 200
        const val ROW_HEIGHT = 48
        const val BUTTON_GAP = 8
        const val UNKNOWN_TIME = "--:--:--"
    }
}

/** The "START AND END" card. The caption shows the length of the trimmed video on the right. */
internal class TrimCard(rows: List<TrimRow>) :
    GroupCard("trim-group", tr("adjust.trim.group"), Material2OutlinedAL.CONTENT_CUT, rows, rows.first().preferredSize.height) {
    private val lengthFont = UiKit.font(11.5f)

    var lengthText: String = ""
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    override fun paintCaptionEnd(g2: Graphics2D, right: Float, top: Float, height: Float) {
        if (lengthText.isEmpty()) return
        val text = tr("adjust.trim.length", "length" to lengthText)
        UiKit.drawText(g2, text, lengthFont, Palette.FG_3, right - UiKit.textWidth(text, lengthFont), top, height)
    }
}
