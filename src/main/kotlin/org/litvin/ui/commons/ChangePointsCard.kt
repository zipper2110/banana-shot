package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.shared.util.Timecode
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import org.litvin.i18n.tr

/**
 * The change points of a tab (B-47) and the playhead. The active change point is the last one at or before the
 * playhead, or -1 before the first one.
 */
data class ChangePointsState(val times: List<Long> = emptyList(), val playheadMs: Long = 0L) {
    val activeIndex: Int get() = times.indexOfLast { it <= playheadMs }
    val canAdd: Boolean get() = times.none { it == playheadMs }
    val canRemove: Boolean get() = activeIndex >= 0
    val previousMs: Long? get() = times.lastOrNull { it < playheadMs }
    val nextMs: Long? get() = times.firstOrNull { it > playheadMs }
}

/**
 * The card with the change points of the Colors & Transform tab (B-47). The wind can move the phone, and the
 * light can change during a recording. A change point sets other values from its time on. The status shows
 * which values the controls edit. The card shows the change points of the open sub-tab, so [setHint] changes
 * the hint with the sub-tab. The components are named "<namePrefix>-change-status", "-add", "-remove",
 * "-previous", "-next" and "-hint".
 */
internal class ChangePointsCard private constructor(private val parts: Parts) :
    GroupCard("${parts.namePrefix}-group-change-points", tr("changePoints.title"), Material2MZ.TIMELINE, parts.rows, ROW_HEIGHT) {

    constructor(
        namePrefix: String,
        hint: String,
        onAdd: () -> Unit,
        onRemove: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
    ) : this(Parts(namePrefix, hint, onAdd, onRemove, onPrevious, onNext))

    internal val statusLabel: JLabel get() = parts.status
    internal val addButton: UiButton get() = parts.add
    internal val removeButton: UiButton get() = parts.remove
    internal val previousButton: UiButton get() = parts.previous
    internal val nextButton: UiButton get() = parts.next

    /** Sets the hint. [hint] has two lines, with a <br> between them. */
    fun setHint(hint: String) {
        parts.hintLabel.text = "<html>$hint</html>"
    }

    fun render(state: ChangePointsState) {
        val times = state.times
        val index = state.activeIndex
        parts.status.text = when {
            times.isEmpty() -> tr("changePoints.none")
            index < 0 -> tr("changePoints.beforeFirst")
            else -> tr("changePoints.current", "number" to index + 1, "count" to times.size, "time" to format(times[index]))
        }
        parts.status.foreground = if (index >= 0) Palette.YELLOW else Palette.FG_2
        parts.add.isEnabled = state.canAdd
        parts.remove.isEnabled = state.canRemove
        parts.previous.isEnabled = state.previousMs != null
        parts.next.isEnabled = state.nextMs != null
    }

    /** The rows of the card: the status, the buttons, and a short hint. */
    private class Parts(
        val namePrefix: String,
        hint: String,
        onAdd: () -> Unit,
        onRemove: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
    ) {
        val status = JLabel().apply {
            name = "$namePrefix-change-status"
            font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
            foreground = Palette.FG_2
        }
        val add = button("add", tr("changePoints.add"), Material2AL.ADD, tr("changePoints.add.tooltip"), onAdd)
        val remove = button("remove", tr("changePoints.remove"), Material2AL.DELETE, tr("changePoints.remove.tooltip"), onRemove)
        val previous = button("previous", "", Material2AL.CHEVRON_LEFT, tr("changePoints.previous.tooltip"), onPrevious)
        val next = button("next", "", Material2AL.CHEVRON_RIGHT, tr("changePoints.next.tooltip"), onNext)

        private val buttons = JPanel(FlowLayout(FlowLayout.LEFT, BUTTON_GAP, (ROW_HEIGHT - UiButton.SMALL_HEIGHT) / 2)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, PAD_X - BUTTON_GAP, 0, PAD_X)
            listOf(add, remove, previous, next).forEach { add(it) }
        }

        // The hint has two fixed lines (a <br> in [hint]): the card rows have one height, and a wrapped HTML
        // label can need a third line.
        val hintLabel = JLabel("<html>$hint</html>").apply {
            name = "$namePrefix-change-hint"
            font = UiKit.font(11.5f)
            foreground = Palette.FG_3
        }

        val rows = listOf(padded(status), buttons, padded(hintLabel))

        private fun button(suffix: String, text: String, ikon: Ikon, tooltip: String, action: () -> Unit) =
            UiButton(text, ikon, buttonHeight = UiButton.SMALL_HEIGHT).apply {
                name = "$namePrefix-change-$suffix"
                toolTipText = tooltip
                // Space must stay the play hotkey, so these buttons never keep the focus.
                isFocusable = false
                addActionListener { action() }
            }

        private fun padded(content: Component) = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, PAD_X, 0, PAD_X)
            add(content, BorderLayout.CENTER)
        }
    }

    private companion object {
        const val ROW_HEIGHT = 40
        const val PAD_X = 14
        const val BUTTON_GAP = 6

        fun format(ms: Long): String = Timecode.format(ms.coerceAtLeast(0L)).substring(0, 8)
    }
}
