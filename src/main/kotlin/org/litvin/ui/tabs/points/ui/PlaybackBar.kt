package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.litvin.shared.util.Timecode
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.PlayButton
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SeekButton
import org.litvin.ui.commons.SpeedControl
import org.litvin.i18n.tr

/**
 * The playback bar under the video: the current time with the video length under it, the speed, and Frame step on the
 * left, the seek and play buttons in the center, and Comment on the right. Frame step is next to the seek buttons,
 * because it changes what Left and Right do. The bar has playback controls only; the mark buttons are in the point bar
 * above it.
 */
internal class PlaybackBar(
    onTogglePlay: () -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    onAddComment: () -> Unit,
    onSpeedIndex: (index: Int) -> Unit,
    /** The Frame step toggle. */
    onToggleFrameStep: () -> Unit = {},
    /** The replay button at the right end of the bar (B-50 T5). */
    onReplay: () -> Unit = {},
) : JPanel() {

    /** A seek tooltip, for example "Seek back 5 seconds [Shift+Left]". */
    private fun seekTip(key: String, seconds: Int, keyName: String) = tr(key, "seconds" to seconds, "key" to keyName)

    private val nowLabel = TimeLabel(Timecode.format(0)).apply {
        name = "points-current-time"
        font = UiKit.font(15f)
        foreground = Palette.FG
        toolTipText = tr("playback.currentTime")
    }
    private val totalLabel = TimeLabel("").apply {
        name = "points-total-time"
        font = UiKit.font(12f)
        foreground = Palette.FG_3
    }

    /** The current time with the video length under it, so that the clock uses less width. */
    private val clock = JPanel().apply {
        name = "points-time-panel"
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        nowLabel.alignmentX = LEFT_ALIGNMENT
        totalLabel.alignmentX = LEFT_ALIGNMENT
        add(nowLabel)
        add(totalLabel)
    }

    private val seekButtons = listOf(
        SeekButton("−5s", -5_000, listOf("⇧", "←"), Feather.CHEVRONS_LEFT, seekTip("points.seekBack.tooltip", 5, tr("key.withShift", "key" to tr("key.left"))), "points-seek-back-5s"),
        SeekButton("−1s", -1_000, listOf("←"), Feather.CHEVRON_LEFT, seekTip("points.seekBack.tooltip", 1, tr("key.left")), "points-seek-back-1s"),
        SeekButton("+1s", 1_000, listOf("→"), Feather.CHEVRON_RIGHT, seekTip("points.seekForward.tooltip", 1, tr("key.right")), "points-seek-forward-1s"),
        SeekButton("+5s", 5_000, listOf("⇧", "→"), Feather.CHEVRONS_RIGHT, seekTip("points.seekForward.tooltip", 5, tr("key.withShift", "key" to tr("key.right"))), "points-seek-forward-5s"),
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
    private val commentButton = UiButton(tr("points.comment"), Material2AL.ADD_COMMENT, buttonHeight = 40).apply {
        name = "points-add-comment"
        toolTipText = tr("points.comment.tooltip")
        // Space must stay the play hotkey, so the bar buttons never keep the focus.
        isFocusable = false
    }

    /** Opens the replay editor for the point at the playhead (B-50). No key does this (decision of 2026-10-26). */
    private val replayButton = UiButton(tr("points.replay"), org.kordamp.ikonli.material2.Material2MZ.SLOW_MOTION_VIDEO, buttonHeight = 40).apply {
        name = "points-replay"
        toolTipText = tr("points.replay.tooltip")
        isFocusable = false
    }

    val frameStep = FrameStepToggle().apply {
        name = "points-frame-step"
        addActionListener { onToggleFrameStep() }
    }

    init {
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(10, 16, 10, 16),
        )
        layout = BarLayout()
        add(clock)
        add(speed)
        add(transport)
        add(commentButton)
        add(replayButton)
        add(frameStep)
        playButton.addActionListener { onTogglePlay() }
        seekButtons.forEach { button -> button.addActionListener { onNudge(button.deltaMs) } }
        commentButton.addActionListener { onAddComment() }
        replayButton.addActionListener { onReplay() }
        setPlaying(false)
    }

    fun setPlaying(playing: Boolean) {
        playButton.playing = playing
    }

    /** Shows the playhead time and the video length. A length of 0 hides the length. */
    fun setTime(currentMs: Long, totalMs: Long) {
        val now = replayText ?: Timecode.format(max(0, currentMs))
        if (nowLabel.text != now) nowLabel.text = now
        val total = if (totalMs > 0) Timecode.format(totalMs) else ""
        if (totalLabel.text != total) totalLabel.text = total
    }

    /** The text of the clock during a replay (B-50), or null for the time. */
    private var replayText: String? = null

    /** Shows [text] in lime in place of the current time while a replay plays. Null shows the time again. */
    fun setReplay(text: String?) {
        if (replayText == text) return
        replayText = text
        nowLabel.foreground = if (text != null) Palette.LIME else Palette.FG
        if (text != null) nowLabel.text = text
    }

    /** A seek hotkey briefly gives the matching button a lime border. */
    fun flashSeek(deltaMs: Long) {
        seekButtons.firstOrNull { it.deltaMs == deltaMs }?.flash()
    }

    /**
     * The grid "1fr auto 1fr" of the design: the transport stays in the center of the bar,
     * the clock, the speed, and Frame step use the space on its left and Comment the space on its right.
     * Frame step is at the right end of the left space, next to the seek buttons.
     * A narrow bar hides the direction icons of the seek buttons first. When the left space is too small,
     * the speed hides its caption and key chips, then Frame step hides its label, and then the speed hides.
     */
    private inner class BarLayout : LayoutManager {
        override fun addLayoutComponent(name: String?, comp: Component?) = Unit
        override fun removeLayoutComponent(comp: Component?) = Unit
        override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)

        override fun preferredLayoutSize(parent: Container): Dimension {
            val insets = parent.insets
            val content = listOf(clock, speed, transport, commentButton, replayButton, frameStep).maxOf { it.preferredSize.height }
            val left = leftWidth(speedCompact = false, frameStepCompact = false)
            val side = max(left, rightWidth())
            return Dimension(insets.left + insets.right + side * 2 + transport.preferredSize.width + GRID_GAP * 2, insets.top + insets.bottom + content)
        }

        override fun layoutContainer(parent: Container) {
            val insets = parent.insets
            val inner = parent.width - insets.left - insets.right
            val showDirection = inner > HIDE_DIRECTION_BELOW
            seekButtons.forEach { it.showDirection = showDirection }

            val height = parent.height - insets.top - insets.bottom
            fun place(component: Component, x: Int, w: Int, h: Int = component.preferredSize.height) {
                component.setBounds(x, insets.top + (height - h) / 2, w, h)
            }
            val center = transport.preferredSize.width
            val centerX = insets.left + (inner - center) / 2
            place(transport, centerX, center)
            val space = max(0, centerX - GRID_GAP - insets.left)
            fitLeftSide(space)
            val clockWidth = clock.preferredSize.width.coerceAtMost(space)
            place(clock, insets.left, clockWidth)
            val speedX = insets.left + clockWidth + SPEED_GAP
            if (speed.isVisible) place(speed, speedX, speed.preferredSize.width)
            val step = frameStep.preferredSize.width
            val stepMinX = if (speed.isVisible) speedX + speed.preferredSize.width + SPEED_GAP else speedX
            // Frame step has the height of the seek buttons, so it lines up with them.
            place(frameStep, max(stepMinX, centerX - GRID_GAP - step), step, seekButtons.first().preferredSize.height)
            val replay = replayButton.preferredSize.width
            val replayX = max(centerX + center + GRID_GAP, parent.width - insets.right - replay)
            place(replayButton, replayX, replay)
            val button = commentButton.preferredSize.width
            place(commentButton, max(centerX + center + GRID_GAP, replayX - SPEED_GAP - button), button)
        }

        /** The width of Comment and the replay button on the right of the transport. */
        private fun rightWidth() = commentButton.preferredSize.width + SPEED_GAP + replayButton.preferredSize.width

        /** The width of the clock, the speed, and Frame step in the given forms. */
        private fun leftWidth(speedCompact: Boolean, frameStepCompact: Boolean) =
            clock.preferredSize.width + SPEED_GAP + speed.preferredWidth(speedCompact) + SPEED_GAP +
                frameStep.preferredWidth(frameStepCompact)

        /**
         * Selects the first form of the speed and Frame step that fits in [space]. The widths come from the preferred
         * sizes of the parts, because a visibility change in each layout pass would start a new layout pass.
         */
        private fun fitLeftSide(space: Int) {
            val (speedCompact, stepCompact, showSpeed) = when {
                leftWidth(speedCompact = false, frameStepCompact = false) <= space -> Triple(false, false, true)
                leftWidth(speedCompact = true, frameStepCompact = false) <= space -> Triple(true, false, true)
                leftWidth(speedCompact = true, frameStepCompact = true) <= space -> Triple(true, true, true)
                else -> Triple(true, true, false)
            }
            speed.compact = speedCompact
            speed.isVisible = showSpeed
            frameStep.compact = stepCompact
        }
    }

    /**
     * A time label with the width of its widest time, so that the next parts of the bar (the speed)
     * do not move while the time changes. An empty label has no width.
     */
    private class TimeLabel(text: String) : JLabel(text) {
        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            if (text.isNullOrEmpty()) return size
            // The widest digit in each place of the time, for example "88:88:88.8" in most fonts.
            val widest = ('0'..'9').maxBy { UiKit.textWidth(it.toString(), font) }
            val sample = text.map { if (it.isDigit()) widest else it }.joinToString("")
            val textWidth = ceil(UiKit.textWidth(sample, font)).toInt() + 2
            return Dimension(max(size.width, textWidth + insets.left + insets.right), size.height)
        }

        /**
         * The label can use the full width of the clock. The default maximum is the exact text width, and the vertical
         * box of the clock never gives more. At some screen scales the drawn text is wider than that measured width,
         * and the label then cuts the text to "…".
         */
        override fun getMaximumSize(): Dimension = Dimension(Short.MAX_VALUE.toInt(), preferredSize.height)
    }

    private companion object {
        const val GRID_GAP = 16
        const val TRANSPORT_GAP = 6
        const val PLAY_MARGIN = 8
        const val SPEED_GAP = 8
        const val HIDE_DIRECTION_BELOW = 880
    }
}
