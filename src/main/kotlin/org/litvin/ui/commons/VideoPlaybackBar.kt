package org.litvin.ui.commons

import com.formdev.flatlaf.ui.FlatSliderUI
import org.kordamp.ikonli.feather.Feather
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
import org.litvin.i18n.tr

/**
 * The playback bar under the video of the Colors & Transform tab, in two rows. The top row has the seek buttons and
 * the play button as on the Points tab, in the center. The bottom row has the current time, the seek bar and the
 * video length.
 * The components are named "<namePrefix>-play-pause", "-current-time", "-total-time", "-seek",
 * "-seek-back-5s", "-seek-back-1s", "-seek-forward-1s" and "-seek-forward-5s".
 * Without [onNudge], the bar has no seek buttons.
 */
internal class VideoPlaybackBar(
    namePrefix: String,
    barName: String,
    onTogglePlay: () -> Unit,
    private val onSeek: (targetMs: Long) -> Unit,
    onNudge: ((deltaMs: Long) -> Unit)? = null,
) : JPanel(null) {

    val playButton = PlayButton(PlayButtonStyle.COMPACT).apply {
        name = "$namePrefix-play-pause"
        getAccessibleContext().accessibleName = tr("playback.playPause")
    }

    /** The -5s and -1s buttons on the left of the play button, and the +1s and +5s buttons on its right. */
    private val seekButtons: List<SeekButton> = if (onNudge == null) emptyList() else listOf(
        SeekButton(seekLabel(-5), -5_000, listOf("⇧", "←"), Feather.CHEVRONS_LEFT, seekTip("points.seekBack.tooltip", 5, tr("key.withShift", "key" to tr("key.left"))), "$namePrefix-seek-back-5s"),
        SeekButton(seekLabel(-1), -1_000, listOf("←"), Feather.CHEVRON_LEFT, seekTip("points.seekBack.tooltip", 1, tr("key.left")), "$namePrefix-seek-back-1s"),
        SeekButton(seekLabel(1), 1_000, listOf("→"), Feather.CHEVRON_RIGHT, seekTip("points.seekForward.tooltip", 1, tr("key.right")), "$namePrefix-seek-forward-1s"),
        SeekButton(seekLabel(5), 5_000, listOf("⇧", "→"), Feather.CHEVRONS_RIGHT, seekTip("points.seekForward.tooltip", 5, tr("key.withShift", "key" to tr("key.right"))), "$namePrefix-seek-forward-5s"),
    )

    private val nowLabel = JLabel(format(0)).apply {
        name = "$namePrefix-current-time"
        font = UiKit.font(13f)
        foreground = Palette.FG
        toolTipText = tr("playback.currentTime")
    }
    private val totalLabel = JLabel(format(0), SwingConstants.RIGHT).apply {
        name = "$namePrefix-total-time"
        font = UiKit.font(13f)
        foreground = Palette.FG_3
        toolTipText = tr("playback.videoLength")
    }
    private val seekUi = SeekSliderUI()

    val seekSlider: JSlider = JSlider(0, SEEK_STEPS, 0).apply {
        name = "$namePrefix-seek"
        toolTipText = tr("playback.seek")
        isOpaque = false
        // Space must stay the play hotkey, so the seek bar never keeps the focus.
        isFocusable = false
        setUI(seekUi)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private var totalMs = 0L
    private var updatingFromPlayer = false
    private var markersMs: List<Long> = emptyList()

    init {
        name = barName
        isOpaque = true
        background = Palette.OVERLAY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(PAD_Y, PAD_X, PAD_Y, PAD_X),
        )
        seekButtons.forEach { add(it) }
        add(playButton)
        add(nowLabel)
        add(seekSlider)
        add(totalLabel)
        playButton.addActionListener { onTogglePlay() }
        if (onNudge != null) seekButtons.forEach { button -> button.addActionListener { onNudge(button.deltaMs) } }
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

    /** A seek hotkey briefly gives the matching button a lime border. */
    fun flashSeek(deltaMs: Long) {
        seekButtons.firstOrNull { it.deltaMs == deltaMs }?.flash()
    }

    /** A seek tooltip, for example "Seek back 5 seconds [Shift+Left]". */
    private fun seekTip(key: String, seconds: Int, keyName: String) = tr(key, "seconds" to seconds, "key" to keyName)

    private fun seekLabel(seconds: Int) =
        if (seconds < 0) tr("playback.seekBack.label", "seconds" to -seconds) else tr("playback.seekForward.label", "seconds" to seconds)

    /** The width of the seek buttons and the play button with the gaps between them. */
    private fun transportWidth(direction: Boolean): Int {
        val play = playButton.preferredSize.width
        if (seekButtons.isEmpty()) return play
        return seekButtons.sumOf { it.preferredWidth(direction) } + SEEK_GAP * 2 + (SEEK_GAP + PLAY_MARGIN) * 2 + play
    }

    /** Shows marks on the seek bar at these times, for example the change points of the Colors & Transform tab. */
    fun setMarkers(timesMs: List<Long>) {
        if (markersMs == timesMs) return
        markersMs = timesMs
        updateMarkers()
    }

    private fun updateMarkers() {
        val values = if (totalMs > 0) markersMs.map { (SEEK_STEPS * (it.coerceIn(0L, totalMs).toDouble() / totalMs)).toInt() } else emptyList()
        if (seekUi.markers == values) return
        seekUi.markers = values
        seekSlider.repaint()
    }

    /** Shows the playhead time and moves the seek bar. It does not call the seek callback. */
    fun setTime(currentMs: Long, totalMs: Long) {
        if (this.totalMs != max(0, totalMs)) {
            this.totalMs = max(0, totalMs)
            updateMarkers()
        }
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
        return Dimension(
            insets.left + insets.right + max(transportWidth(direction = true), TIME_WIDTH * 2 + GAP * 2 + 200),
            insets.top + insets.bottom + playButton.preferredSize.height + ROW_GAP + SEEK_ROW,
        )
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    /**
     * Two rows. The top row has the buttons in the center. The bottom row is the grid "auto 1fr auto" of the design:
     * the seek bar gets the free width. When the buttons do not fit, they hide their direction icons, as on the
     * Points tab.
     */
    override fun doLayout() {
        val insets = insets
        val inner = width - insets.left - insets.right
        val showDirection = transportWidth(direction = true) <= inner
        seekButtons.forEach { it.showDirection = showDirection }

        // The top row: -5s, -1s, play, +1s, +5s.
        val play = playButton.preferredSize
        val buttonsTop = insets.top
        fun placeButton(component: java.awt.Component, x: Int, size: Dimension) =
            component.setBounds(x, buttonsTop + (play.height - size.height) / 2, size.width, size.height)
        var x = insets.left + max(0, (inner - transportWidth(showDirection)) / 2)
        if (seekButtons.isEmpty()) placeButton(playButton, x, play)
        seekButtons.forEachIndexed { index, button ->
            if (index == 2) {
                x += PLAY_MARGIN
                placeButton(playButton, x, play)
                x += play.width + PLAY_MARGIN + SEEK_GAP
            }
            val size = button.preferredSize
            placeButton(button, x, size)
            x += size.width + SEEK_GAP
        }

        // The bottom row: the current time, the seek bar and the video length.
        val rowTop = buttonsTop + play.height + ROW_GAP
        fun place(component: java.awt.Component, x: Int, w: Int, h: Int) =
            component.setBounds(x, rowTop + (SEEK_ROW - h) / 2, w, h)
        x = insets.left
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
        const val SEEK_GAP = 6
        const val PLAY_MARGIN = 8
        const val ROW_GAP = 6
        const val SEEK_ROW = 20

        /** hh:mm:ss, as in the design. */
        fun format(ms: Long): String = Timecode.format(max(0, ms)).substring(0, 8)
    }
}

/** The seek bar: a thin track that is lime up to the knob, yellow marks, and a lime knob with a dark ring. */
internal class SeekSliderUI : FlatSliderUI() {
    /** Slider values with a yellow mark on the track. */
    var markers: List<Int> = emptyList()

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
            g2.color = Palette.YELLOW
            val markTop = trackRect.y + (trackRect.height - MARK_HEIGHT) / 2.0
            markers.forEach { value ->
                val x = xPositionForValue(value) - MARK_WIDTH / 2
                g2.fill(RoundRectangle2D.Double(x, markTop, MARK_WIDTH, MARK_HEIGHT, MARK_WIDTH, MARK_WIDTH))
            }
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
        const val MARK_WIDTH = 3.0
        const val MARK_HEIGHT = 12.0
        val TRACK_BG get() = Palette.LINE_3
    }
}
