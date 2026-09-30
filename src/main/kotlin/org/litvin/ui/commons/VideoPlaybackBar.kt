package org.litvin.ui.commons

import com.formdev.flatlaf.ui.FlatSliderUI
import org.litvin.shared.util.Timecode
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import kotlin.math.max

/**
 * The playback bar under the video of the Colors and Transform tabs: the play button, the current time,
 * the seek bar and the video length, in one row.
 * The components are named "<namePrefix>-play-pause", "-current-time", "-total-time" and "-seek".
 */
internal class VideoPlaybackBar(
    namePrefix: String,
    barName: String,
    onTogglePlay: () -> Unit,
    private val onSeek: (targetMs: Long) -> Unit,
) : JPanel(null) {

    val playButton = PlayButton(PlayButtonStyle.COMPACT).apply {
        name = "$namePrefix-play-pause"
        getAccessibleContext().accessibleName = "Play or Pause"
    }

    private val nowLabel = JLabel(format(0)).apply {
        name = "$namePrefix-current-time"
        font = UiKit.font(13f)
        foreground = Palette.FG
        toolTipText = "Current time"
    }
    private val totalLabel = JLabel(format(0), SwingConstants.RIGHT).apply {
        name = "$namePrefix-total-time"
        font = UiKit.font(13f)
        foreground = Palette.FG_3
        toolTipText = "Video length"
    }
    val seekSlider: JSlider = JSlider(0, SEEK_STEPS, 0).apply {
        name = "$namePrefix-seek"
        toolTipText = "Seek"
        isOpaque = false
        // Space must stay the play hotkey, so the seek bar never keeps the focus.
        isFocusable = false
        setUI(SeekSliderUI())
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private var totalMs = 0L
    private var updatingFromPlayer = false

    init {
        name = barName
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(PAD_Y, PAD_X, PAD_Y, PAD_X),
        )
        add(playButton)
        add(nowLabel)
        add(seekSlider)
        add(totalLabel)
        playButton.addActionListener { onTogglePlay() }
        seekSlider.addChangeListener {
            if (updatingFromPlayer || totalMs <= 0) return@addChangeListener
            val target = (totalMs * seekSlider.value / SEEK_STEPS).coerceIn(0L, totalMs - 1)
            nowLabel.text = format(target)
            onSeek(target)
        }
        setPlaying(false)
    }

    fun setPlaying(playing: Boolean) {
        playButton.playing = playing
    }

    /** Shows the playhead time and moves the seek bar. It does not call the seek callback. */
    fun setTime(currentMs: Long, totalMs: Long) {
        this.totalMs = max(0, totalMs)
        val total = format(this.totalMs)
        if (totalLabel.text != total) totalLabel.text = total
        // While the user drags the seek bar, the drag controls the time label.
        if (seekSlider.valueIsAdjusting) return
        val now = format(currentMs)
        if (nowLabel.text != now) nowLabel.text = now
        val value = if (this.totalMs > 0) {
            (SEEK_STEPS * (currentMs.coerceIn(0L, this.totalMs).toDouble() / this.totalMs)).toInt()
        } else {
            0
        }
        updatingFromPlayer = true
        try {
            seekSlider.value = value
        } finally {
            updatingFromPlayer = false
        }
    }

    /** Clears the time labels and the seek bar, for example before a video is loaded. */
    fun reset() = setTime(0, 0)

    override fun getPreferredSize(): Dimension {
        val insets = insets
        val content = playButton.preferredSize
        return Dimension(
            insets.left + insets.right + content.width + TIME_WIDTH * 2 + GAP * 3 + 200,
            insets.top + insets.bottom + content.height,
        )
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    /** The grid "auto auto 1fr auto" of the design: the seek bar gets the free width. */
    override fun doLayout() {
        val insets = insets
        val height = height - insets.top - insets.bottom
        fun place(component: java.awt.Component, x: Int, w: Int, h: Int) =
            component.setBounds(x, insets.top + (height - h) / 2, w, h)

        val play = playButton.preferredSize
        var x = insets.left
        place(playButton, x, play.width, play.height)
        x += play.width + GAP
        val nowWidth = max(TIME_WIDTH, nowLabel.preferredSize.width + 2)
        place(nowLabel, x, nowWidth, TIME_LINE)
        x += nowWidth + GAP
        val totalWidth = max(TIME_WIDTH, totalLabel.preferredSize.width + 2)
        val right = width - insets.right
        place(totalLabel, right - totalWidth, totalWidth, TIME_LINE)
        place(seekSlider, x, max(0, right - totalWidth - GAP - x), SEEK_HEIGHT)
    }

    private companion object {
        const val PAD_X = 16
        const val PAD_Y = 10
        const val GAP = 14
        const val TIME_WIDTH = 62
        const val TIME_LINE = 18
        const val SEEK_HEIGHT = 20
        const val SEEK_STEPS = 10_000

        /** hh:mm:ss, as in the design. */
        fun format(ms: Long): String = Timecode.format(max(0, ms)).substring(0, 8)
    }
}

/** The seek bar: a thin track that is lime up to the knob, and a lime knob with a dark ring. */
internal class SeekSliderUI : FlatSliderUI() {
    override fun getThumbSize(): Dimension = Dimension(KNOB, KNOB)

    override fun paintFocus(g: Graphics) = Unit

    override fun paintTrack(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val y = trackRect.y + trackRect.height / 2.0 - TRACK / 2
            val fill = thumbRect.x + thumbRect.width / 2.0
            g2.color = TRACK_BG
            g2.fill(RoundRectangle2D.Double(0.0, y, slider.width.toDouble(), TRACK, TRACK, TRACK))
            g2.color = Palette.LIME
            g2.fill(RoundRectangle2D.Double(0.0, y, fill, TRACK, TRACK, TRACK))
        } finally {
            g2.dispose()
        }
    }

    override fun paintThumb(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val x = thumbRect.x + (thumbRect.width - KNOB) / 2.0
            val y = thumbRect.y + (thumbRect.height - KNOB) / 2.0
            g2.color = Palette.LIME
            g2.fill(Ellipse2D.Double(x, y, KNOB.toDouble(), KNOB.toDouble()))
            g2.color = Palette.OVERLAY
            g2.fill(Ellipse2D.Double(x + 1, y + 1, KNOB - 2.0, KNOB - 2.0))
            g2.color = Palette.LIME
            g2.fill(Ellipse2D.Double(x + 4, y + 4, KNOB - 8.0, KNOB - 8.0))
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val KNOB = 16
        const val TRACK = 4.0
        val TRACK_BG = Palette.LINE_3
    }
}
