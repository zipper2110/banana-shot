package org.litvin.ui.tabs.export

import org.litvin.export.preview.ExportPreviewTimeline
import org.litvin.i18n.tr
import org.litvin.ui.commons.Palette
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.roundToInt

/**
 * The item strip of the export preview (B-75 T5, decision 4): each point, replay, and card of the output is a block.
 * The width of a block shows its output time. The current item has a bright frame, and a line shows the playhead.
 * A click on a block goes to the start of its item ([onSelect]).
 *
 * [pointNumbers] gives the number of each point in the Points list, for the tooltips.
 */
internal class ExportPreviewStrip(
    private val timeline: ExportPreviewTimeline,
    private val pointNumbers: Map<String, Int>,
    private val onSelect: (index: Int) -> Unit,
) : JComponent() {
    private var currentIndex = -1
    private var playheadMs = 0L
    private var hoverIndex = -1

    init {
        name = "export-preview-strip"
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        ToolTipManager.sharedInstance().registerComponent(this)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                indexAtX(e.x).takeIf { it >= 0 }?.let(onSelect)
            }

            override fun mouseMoved(e: MouseEvent) {
                val index = indexAtX(e.x)
                if (index != hoverIndex) {
                    hoverIndex = index
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                hoverIndex = -1
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    /** Shows the item at [index] as the current item, and the playhead at [outMs]. */
    fun setPosition(index: Int, outMs: Long) {
        if (index == currentIndex && outMs == playheadMs) return
        currentIndex = index
        playheadMs = outMs
        repaint()
    }

    override fun getPreferredSize() = Dimension(200, HEIGHT)
    override fun getMinimumSize() = Dimension(0, HEIGHT)
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, HEIGHT)

    override fun getToolTipText(event: MouseEvent): String? = timeline.items.getOrNull(indexAtX(event.x))?.let(::describe)

    /** The text of an item for the tooltip. */
    internal fun describe(item: ExportPreviewTimeline.Item): String = when (item) {
        is ExportPreviewTimeline.Video -> item.pointId?.let { pointNumbers[it] }
            ?.let { tr("export.preview.item.point", "number" to it) }
            ?: tr("export.preview.item.video")
        is ExportPreviewTimeline.Replay -> pointNumbers[item.replay.pointId]
            ?.let { tr("export.preview.item.replay", "number" to it) }
            ?: tr("export.preview.item.replayOnly")
        is ExportPreviewTimeline.Card -> item.setNumber
            ?.let { tr("export.preview.item.setCard", "set" to it) }
            ?: tr("export.preview.item.matchCard")
    }

    private fun xOf(ms: Long): Int {
        val total = timeline.durationMs
        if (total <= 0) return 0
        return (width * (ms.toDouble() / total)).roundToInt()
    }

    private fun indexAtX(x: Int): Int {
        val total = timeline.durationMs
        if (total <= 0 || width <= 0) return -1
        val ms = (x.coerceIn(0, width - 1).toDouble() / width * total).toLong()
        return timeline.indexAt(ms)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as java.awt.Graphics2D
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val top = PAD_Y
            val h = height - PAD_Y * 2
            g2.color = Palette.TRACK
            g2.fillRoundRect(0, top, width, h, 4, 4)
            timeline.items.forEachIndexed { index, item ->
                val x0 = xOf(item.outStartMs)
                // A gap of 1 px between blocks, but each block is at least 2 px wide.
                val w = (xOf(item.outEndMs) - x0 - GAP).coerceAtLeast(MIN_BLOCK)
                val (fill, edge) = colors(item, index == currentIndex, index == hoverIndex)
                g2.color = fill
                g2.fillRect(x0, top, w, h)
                if (edge != null) {
                    g2.color = edge
                    g2.drawRect(x0, top, w - 1, h - 1)
                }
            }
            val playheadX = xOf(playheadMs).coerceIn(0, (width - 2).coerceAtLeast(0))
            g2.color = Palette.FG_STRONG
            g2.fillRect(playheadX, 0, 2, height)
        } finally {
            g2.dispose()
        }
    }

    /** The fill and the frame of a block. The replays use the lime of the Points tab. */
    private fun colors(item: ExportPreviewTimeline.Item, current: Boolean, hover: Boolean): Pair<Color, Color?> {
        val base = when (item) {
            is ExportPreviewTimeline.Video -> Palette.withAlpha(Palette.GREEN, if (current) 200 else 128)
            is ExportPreviewTimeline.Replay -> if (current) Palette.LIME_LINE else Palette.LIME_GLOW
            is ExportPreviewTimeline.Card -> Palette.withAlpha(Palette.BLUE, if (current) 200 else 120)
        }
        val edge = when {
            current -> Palette.FG_STRONG
            item is ExportPreviewTimeline.Replay -> Palette.LIME
            hover -> Palette.FG_2
            else -> null
        }
        return base to edge
    }

    private companion object {
        const val HEIGHT = 26
        const val PAD_Y = 4
        const val GAP = 1
        const val MIN_BLOCK = 2
    }
}
