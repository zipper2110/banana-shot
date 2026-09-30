package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max
import org.litvin.ui.commons.KeyChips
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.PlayButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.SeekButton
import org.litvin.ui.commons.SpeedControl

/**
 * The playback bar under the video (the `.pointbar` of design/scoring-redesign/final.html):
 * - the scrub bar of the selected point, with the times from the point start,
 * - the speed at the left end, the transport in the center, and the frame step toggle at the right end.
 */
internal class ScoringPlaybackBar(
    onTogglePlay: () -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    onScrub: (targetMs: Long) -> Unit,
    onSpeedIndex: (index: Int) -> Unit,
    onToggleFrameStep: () -> Unit,
) : JPanel(null) {

    val scrub = PointScrubBar(onScrub)

    private val seekButtons = listOf(
        SeekButton("−5s", -5_000, listOf("⇧", "←"), Feather.CHEVRONS_LEFT, "Seek back 5 seconds [Shift+Left]", "scoring-seek-back-5s"),
        SeekButton("−1s", -1_000, listOf("←"), Feather.CHEVRON_LEFT, "Seek back 1 second [Left]", "scoring-seek-back-1s"),
        SeekButton("+1s", 1_000, listOf("→"), Feather.CHEVRON_RIGHT, "Seek forward 1 second [Right]", "scoring-seek-forward-1s"),
        SeekButton("+5s", 5_000, listOf("⇧", "→"), Feather.CHEVRONS_RIGHT, "Seek forward 5 seconds [Shift+Right]", "scoring-seek-forward-5s"),
    )
    val playButton = PlayButton().apply { name = "scoring-play-pause" }
    private val transport = JPanel().apply {
        name = "scoring-video-controls"
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

    val speed = SpeedControl("scoring-speed", onSpeedIndex)
    val frameStep = FrameStepToggle().apply { addActionListener { onToggleFrameStep() } }

    init {
        name = "scoring-playback-bar"
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(8, 16, 10, 16),
        )
        add(scrub)
        add(speed)
        add(transport)
        add(frameStep)
        playButton.addActionListener { onTogglePlay() }
        // The seek buttons of the Scoring design have the step and the key chips, without the direction icons.
        seekButtons.forEach { button ->
            button.showDirection = false
            button.addActionListener { onNudge(button.deltaMs) }
        }
    }

    fun setPlaying(playing: Boolean) {
        playButton.playing = playing
    }

    /** A seek hotkey briefly gives the matching button a lime border. */
    fun flashSeek(deltaMs: Long) {
        seekButtons.firstOrNull { it.deltaMs == deltaMs }?.flash()
    }

    private fun controlsHeight() = listOf(speed, transport, frameStep).maxOf { it.preferredSize.height }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        val side = max(speed.preferredSize.width, frameStep.preferredSize.width)
        return Dimension(
            insets.left + insets.right + side * 2 + transport.preferredSize.width + GRID_GAP * 2,
            insets.top + insets.bottom + scrub.preferredSize.height + ROW_GAP + controlsHeight(),
        )
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    /**
     * The grid "1fr auto 1fr" of the design: the transport stays in the center, the speed uses the space on its left
     * and the frame step toggle the space on its right. A narrow bar hides the speed caption and key chips.
     */
    override fun doLayout() {
        val insets = insets
        val inner = width - insets.left - insets.right
        scrub.setBounds(insets.left, insets.top, inner, scrub.preferredSize.height)

        speed.compact = inner < COMPACT_SPEED_BELOW

        val top = insets.top + scrub.preferredSize.height + ROW_GAP
        val height = controlsHeight()
        fun place(component: Component, x: Int, w: Int) {
            val h = component.preferredSize.height
            component.setBounds(x, top + (height - h) / 2, w, h)
        }
        val center = transport.preferredSize.width
        val centerX = insets.left + (inner - center) / 2
        place(transport, centerX, center)
        place(speed, insets.left, speed.preferredSize.width.coerceAtMost(max(0, centerX - GRID_GAP - insets.left)))
        val toggle = frameStep.preferredSize.width
        place(frameStep, max(centerX + center + GRID_GAP, width - insets.right - toggle), toggle)
    }

    private companion object {
        const val GRID_GAP = 16
        const val ROW_GAP = 6
        const val TRANSPORT_GAP = 6
        const val PLAY_MARGIN = 6

        /** The design hides the speed caption and key chips below 760 px (a container query on the bar). */
        const val COMPACT_SPEED_BELOW = 760
    }
}

/**
 * The scrub bar of the selected point: "Point start" with 0:00.0 on the left, the track, and "Point end" with the
 * point length on the right. A press or a drag on the track seeks; the owner pauses the player.
 */
internal class PointScrubBar(private val onScrub: (targetMs: Long) -> Unit) : JComponent() {
    private var startMs = 0L
    private var endMs = 0L
    private var positionMs = 0L
    private var dragging = false

    private val captionFont get() = UiKit.trackedFont(9.5f, 0.08)
    private val timeFont get() = UiKit.font(11.5f)

    /** True while a point with a length is shown. */
    val hasSegment: Boolean get() = endMs > startMs

    init {
        name = "scoring-seek"
        toolTipText = "Scrub within the selected point"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!hasSegment || !trackContains(e.x)) return
                dragging = true
                seekAt(e.x)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (dragging) seekAt(e.x)
            }

            override fun mouseReleased(e: MouseEvent) {
                dragging = false
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    /** Shows the point [startMs]..[endMs] with the playhead at its start. */
    fun setSegment(startMs: Long, endMs: Long) {
        this.startMs = startMs
        this.endMs = endMs
        this.positionMs = startMs
        repaint()
    }

    fun setPosition(absMs: Long) {
        if (positionMs == absMs) return
        positionMs = absMs
        repaint()
    }

    /** Shows no point: dashes instead of the times and a disabled look. */
    fun reset() = setSegment(0, 0)

    /** The playhead as a fraction of the point, from 0 to 1. */
    val fraction: Double
        get() = if (hasSegment) ((positionMs - startMs).toDouble() / (endMs - startMs)).coerceIn(0.0, 1.0) else 0.0

    private fun edgeWidth(caption: String, time: String) =
        ceil(max(UiKit.textWidth(caption, captionFont), UiKit.textWidth(time, timeFont))).toInt()

    private fun startTime() = if (hasSegment) formatRelative(0) else "—"
    private fun endTime() = if (hasSegment) formatRelative(endMs - startMs) else "—"

    /** The left and right ends of the track. */
    internal fun trackBounds(): IntArray {
        val left = edgeWidth(START, startTime()) + GAP
        val right = width - edgeWidth(END, endTime()) - GAP
        return intArrayOf(left, max(left, right))
    }

    private fun trackContains(x: Int): Boolean {
        val (left, right) = trackBounds()
        return x in (left - THUMB)..(right + THUMB)
    }

    private fun seekAt(x: Int) {
        val (left, right) = trackBounds()
        val f = if (right > left) ((x - left).toDouble() / (right - left)).coerceIn(0.0, 1.0) else 0.0
        val target = (startMs + f * (endMs - startMs)).toLong().coerceIn(startMs, max(startMs, endMs - 1))
        positionMs = target
        repaint()
        onScrub(target)
    }

    override fun getPreferredSize() = Dimension(200, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            if (!hasSegment) g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ScoringUi.DISABLED_ALPHA)
            val captionTop = 2f
            val captionHeight = 13f
            val timeTop = captionTop + captionHeight
            val timeHeight = 15f
            UiKit.drawText(g2, START, captionFont, Palette.FG_3, 0f, captionTop, captionHeight)
            UiKit.drawText(g2, startTime(), timeFont, Palette.FG_2, 0f, timeTop, timeHeight)
            val end = endTime()
            UiKit.drawText(g2, END, captionFont, Palette.FG_3, width - UiKit.textWidth(END, captionFont), captionTop, captionHeight)
            UiKit.drawText(g2, end, timeFont, Palette.FG_2, width - UiKit.textWidth(end, timeFont), timeTop, timeHeight)

            val (left, right) = trackBounds()
            val trackY = height / 2.0 - RAIL / 2.0
            val length = (right - left).toDouble()
            g2.color = RAIL_COLOR
            g2.fill(RoundRectangle2D.Double(left.toDouble(), trackY, length, RAIL, RAIL, RAIL))
            val x = left + length * fraction
            g2.color = Palette.LIME
            g2.fill(RoundRectangle2D.Double(left.toDouble(), trackY, x - left, RAIL, RAIL, RAIL))
            // The thumb: a light knob with a 3 px lime glow ring.
            val cy = height / 2.0
            g2.color = Palette.LIME_GLOW
            g2.fill(Ellipse2D.Double(x - THUMB / 2.0 - 3, cy - THUMB / 2.0 - 3, THUMB + 6.0, THUMB + 6.0))
            g2.color = THUMB_COLOR
            g2.fill(Ellipse2D.Double(x - THUMB / 2.0, cy - THUMB / 2.0, THUMB.toDouble(), THUMB.toDouble()))
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 32
        const val GAP = 12
        const val THUMB = 12
        const val RAIL = 4.0
        const val START = "POINT START"
        const val END = "POINT END"
        val RAIL_COLOR = Palette.LINE_2
        val THUMB_COLOR = Palette.NEUTRAL_LIGHT

        /** A time from the point start, for example "0:07.6". */
        fun formatRelative(ms: Long): String {
            val value = max(0, ms)
            return String.format(Locale.ROOT, "%d:%02d.%d", value / 60_000, value / 1_000 % 60, value % 1_000 / 100)
        }
    }
}

/** The frame step toggle: a check box, "Frame step", and the F key chip. It has a lime border when it is on. */
internal class FrameStepToggle : JButton("Frame step") {
    var on = false
        set(value) {
            if (field == value) return
            field = value
            model.isSelected = value
            repaint()
        }
    private val textFont get() = UiKit.font(12f)
    private val check by lazy { UiKit.icon(Material2AL.CHECK, 12, Palette.ON_LIME) }

    init {
        name = "scoring-frame-step"
        toolTipText = "When enabled, Left/Right step a single frame while paused [F]"
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isFocusable = false
        isOpaque = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun getPreferredSize() = Dimension(
        PAD * 2 + BOX + GAP + ceil(UiKit.textWidth(text, textFont)).toInt() + GAP + KeyChips.width(KEY),
        HEIGHT,
    )

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            val border = when {
                on -> Palette.LIME_LINE
                hover -> Palette.HOVER_LINE
                else -> Palette.LINE_2
            }
            UiKit.paintBox(g2, 0, 0, width, height, 4, if (hover) Palette.RAISED_2 else Palette.RAISED, border)
            var x = PAD
            val boxY = (height - BOX) / 2
            if (on) {
                UiKit.paintBox(g2, x, boxY, BOX, BOX, 3, Palette.LIME, null)
                check.paintIcon(this, g2, x + (BOX - check.iconWidth) / 2, boxY + (BOX - check.iconHeight) / 2)
            } else {
                UiKit.paintBox(g2, x, boxY, BOX, BOX, 3, null, Palette.LINE_5)
            }
            x += BOX + GAP
            UiKit.drawText(g2, text, textFont, Palette.FG, x.toFloat(), 0f, height.toFloat())
            x += ceil(UiKit.textWidth(text, textFont)).toInt() + GAP
            KeyChips.paint(g2, KEY, x, (height - KeyChips.HEIGHT) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT = 32
        const val PAD = 12
        const val BOX = 14
        const val GAP = 7
        const val KEY = "F"
    }
}
