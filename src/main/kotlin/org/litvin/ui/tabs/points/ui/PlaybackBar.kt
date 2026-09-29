package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.litvin.shared.util.Timecode
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max
import org.litvin.ui.commons.PlayButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SeekButton
import org.litvin.ui.commons.SpeedControl

/**
 * The playback bar under the video: the current time and the speed on the left, the seek and play buttons in the
 * center, and Add comment on the right. It has playback controls only; the mark buttons are in [MarkPanel].
 */
internal class PlaybackBar(
    onTogglePlay: () -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    onAddComment: () -> Unit,
    onSpeedIndex: (index: Int) -> Unit,
) : JPanel() {

    private val nowLabel = JLabel(Timecode.format(0)).apply {
        name = "points-current-time"
        font = UiKit.font(15f)
        foreground = UiKit.FG
        toolTipText = "Current time"
    }
    private val totalLabel = JLabel("").apply {
        font = UiKit.font(12f)
        foreground = UiKit.FG_3
    }
    private val clock = JPanel(FlowLayout(FlowLayout.LEFT, CLOCK_GAP, 0)).apply {
        name = "points-time-panel"
        isOpaque = false
        (layout as FlowLayout).alignOnBaseline = true
        add(nowLabel)
        add(totalLabel)
    }

    private val seekButtons = listOf(
        SeekButton("−5s", -5_000, listOf("⇧", "←"), Feather.CHEVRONS_LEFT, "Seek back 5 seconds [Shift+Left]", "points-seek-back-5s"),
        SeekButton("−1s", -1_000, listOf("←"), Feather.CHEVRON_LEFT, "Seek back 1 second [Left]", "points-seek-back-1s"),
        SeekButton("+1s", 1_000, listOf("→"), Feather.CHEVRON_RIGHT, "Seek forward 1 second [Right]", "points-seek-forward-1s"),
        SeekButton("+5s", 5_000, listOf("⇧", "→"), Feather.CHEVRONS_RIGHT, "Seek forward 5 seconds [Shift+Right]", "points-seek-forward-5s"),
    )
    private val playButton = PlayButton().apply { name = "points-play-pause" }
    private val transport = JPanel().apply {
        name = "points-video-controls"
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        seekButtons.forEachIndexed { index, button ->
            if (index == 2) {
                add(Box.createHorizontalStrut(TRANSPORT_GAP + PLAY_MARGIN))
                add(playButton)
                add(Box.createHorizontalStrut(TRANSPORT_GAP + PLAY_MARGIN))
            } else if (index > 0) {
                add(Box.createHorizontalStrut(TRANSPORT_GAP))
            }
            button.alignmentY = CENTER_ALIGNMENT
            add(button)
        }
        playButton.alignmentY = CENTER_ALIGNMENT
    }
    val speed = SpeedControl("points-speed", onSpeedIndex)
    private val commentButton = PointsButton("Add comment", Material2AL.ADD_COMMENT, buttonHeight = 40).apply {
        name = "points-add-comment"
        toolTipText = "Add a comment at the playhead"
        // Space must stay the play hotkey, so the bar buttons never keep the focus.
        isFocusable = false
    }

    init {
        isOpaque = true
        background = UiKit.PLAYBAR
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiKit.LINE),
            BorderFactory.createEmptyBorder(10, 16, 10, 16),
        )
        layout = BarLayout()
        add(clock)
        add(speed)
        add(transport)
        add(commentButton)
        playButton.addActionListener { onTogglePlay() }
        seekButtons.forEach { button -> button.addActionListener { onNudge(button.deltaMs) } }
        commentButton.addActionListener { onAddComment() }
        setPlaying(false)
    }

    fun setPlaying(playing: Boolean) {
        playButton.playing = playing
    }

    /** Shows the playhead time and the video length. A length of 0 hides the length. */
    fun setTime(currentMs: Long, totalMs: Long) {
        val now = Timecode.format(max(0, currentMs))
        if (nowLabel.text != now) nowLabel.text = now
        val total = if (totalMs > 0) "/ " + Timecode.format(totalMs) else ""
        if (totalLabel.text != total) totalLabel.text = total
    }

    /** A seek hotkey briefly gives the matching button a lime border. */
    fun flashSeek(deltaMs: Long) {
        seekButtons.firstOrNull { it.deltaMs == deltaMs }?.flash()
    }

    /**
     * The grid "1fr auto 1fr" of the design: the transport stays in the center of the bar,
     * the clock and the speed use the space on its left and Add comment the space on its right.
     * A narrow bar hides the direction icons of the seek buttons first. When the left space is too small,
     * the speed hides its caption and key chips, then the clock hides the video length, and then the speed hides.
     */
    private inner class BarLayout : LayoutManager {
        override fun addLayoutComponent(name: String?, comp: Component?) = Unit
        override fun removeLayoutComponent(comp: Component?) = Unit
        override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)

        override fun preferredLayoutSize(parent: Container): Dimension {
            val insets = parent.insets
            val content = listOf(clock, speed, transport, commentButton).maxOf { it.preferredSize.height }
            val left = clock.preferredSize.width + SPEED_GAP + speed.preferredSize.width
            val side = max(left, commentButton.preferredSize.width)
            return Dimension(insets.left + insets.right + side * 2 + transport.preferredSize.width + GRID_GAP * 2, insets.top + insets.bottom + content)
        }

        override fun layoutContainer(parent: Container) {
            val insets = parent.insets
            val inner = parent.width - insets.left - insets.right
            val showDirection = inner > HIDE_DIRECTION_BELOW
            seekButtons.forEach { it.showDirection = showDirection }

            val height = parent.height - insets.top - insets.bottom
            fun place(component: Component, x: Int, w: Int) {
                val h = component.preferredSize.height
                component.setBounds(x, insets.top + (height - h) / 2, w, h)
            }
            val center = transport.preferredSize.width
            val centerX = insets.left + (inner - center) / 2
            place(transport, centerX, center)
            val space = max(0, centerX - GRID_GAP - insets.left)
            fitLeftSide(space)
            val clockWidth = clock.preferredSize.width.coerceAtMost(space)
            place(clock, insets.left, clockWidth)
            if (speed.isVisible) place(speed, insets.left + clockWidth + SPEED_GAP, speed.preferredSize.width)
            val button = commentButton.preferredSize.width
            place(commentButton, max(centerX + center + GRID_GAP, parent.width - insets.right - button), button)
        }

        /**
         * Selects the first form of the clock and the speed that fits in [space]. The widths come from the preferred
         * sizes of the parts, because a visibility change in each layout pass would start a new layout pass.
         */
        private fun fitLeftSide(space: Int) {
            val now = nowLabel.preferredSize.width
            val clockFull = now + totalLabel.preferredSize.width + CLOCK_GAP * 3
            val clockShort = now + CLOCK_GAP * 2
            fun fits(clockWidth: Int, compact: Boolean) = clockWidth + SPEED_GAP + speed.preferredWidth(compact) <= space
            val (showTotal, compact, showSpeed) = when {
                fits(clockFull, compact = false) -> Triple(true, false, true)
                fits(clockFull, compact = true) -> Triple(true, true, true)
                fits(clockShort, compact = true) -> Triple(false, true, true)
                else -> Triple(false, true, false)
            }
            totalLabel.isVisible = showTotal
            speed.compact = compact
            speed.isVisible = showSpeed
        }
    }

    private companion object {
        const val GRID_GAP = 16
        const val TRANSPORT_GAP = 6
        const val PLAY_MARGIN = 8
        const val CLOCK_GAP = 8
        const val SPEED_GAP = 8
        const val HIDE_DIRECTION_BELOW = 880
    }
}
