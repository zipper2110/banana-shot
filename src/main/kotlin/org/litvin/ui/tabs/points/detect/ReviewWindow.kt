package org.litvin.ui.tabs.points.detect

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryTimeline
import org.litvin.autodetect.DetectOverlayData
import org.litvin.autodetect.DetectedRally
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.points.AutoKind
import org.litvin.points.PointV1
import org.litvin.scoring.Outcome
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SpeedControl
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.SessionSettings
import org.litvin.ui.tabs.points.ui.FrameStepToggle
import org.litvin.ui.tabs.points.ui.ScorePlayers
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the review window needs from the Points tab. Each change goes into the project at once. */
internal interface ReviewHost {
    val players: ScorePlayers

    /** All clips of the project, in time order. */
    fun points(): List<PointV1>

    fun winnerOf(pointId: String): Outcome

    /** The player (1 or 2) that the detection saw at the near end in the clip, or null when it is not known. */
    fun nearPlayerOf(pointId: String): Int?

    /** Gives the clip the winner, with no ending kind. */
    fun setWinner(pointId: String, winner: Outcome)

    /** Moves the start and the end of the clip. Returns false when the project does not accept the new times. */
    fun setBounds(pointId: String, startMs: Long, endMs: Long): Boolean

    fun deletePoint(pointId: String)

    /** Adds a clip. Returns its id, or null when the project does not accept the clip. */
    fun addPoint(startMs: Long, endMs: Long): String?

    /** Marks the automatic clips as reviewed. */
    fun markChecked(pointIds: Collection<String>)

    /** Gives each of the clips the other winner. */
    fun swapWinners(pointIds: Set<String>)

    /** The detected rally of the clip, or null when the project has no detection data for it. */
    fun detectionOf(pointId: String): DetectedRally? = null

    /** The note of the user about the detection of the clip. */
    fun noteOf(pointId: String): String = ""

    fun setNote(pointId: String, text: String) {}

    /** The names of the overlay parts ([OverlayLayer]) that the user had on the last time. */
    fun overlayLayers(): Set<String> = emptySet()

    fun setOverlayLayers(layers: Set<String>) {}
}

/** Which automatic points "Swap players" changes. */
internal enum class SwapScope(val text: String) {
    POINT("This point"),
    FROM_POINT("This point and the next points"),
    ALL("All automatic points"),
}

/**
 * The bar under the video of the review window (decision 22): the selected clip in lime with an edge at each end, the
 * time around it, the other clips as dim blocks, and the playhead. A drag of an edge moves the start or the end of
 * the clip. A click or a drag in another place moves the playhead.
 *
 * During a drag of an edge, [onPreview] gets the time of the edge, so the video can show it. [onCommit] gets the new
 * start and end when the user releases the edge.
 */
internal class ReviewBoundsBar(
    private val onPreview: (Long) -> Unit,
    private val onCommit: (Long, Long) -> Unit,
    private val onSeek: (Long) -> Unit,
) : JComponent() {
    private var windowStart = 0L
    private var windowEnd = 1L
    private var start = 0L
    private var end = 0L
    private var minStart = 0L
    private var maxEnd = 1L
    private var others: List<LongRange> = emptyList()
    private var playheadMs = 0L
    private var hasPoint = false
    private var shots: List<ShotView> = emptyList()

    private enum class Drag { NONE, START, END, SEEK }

    private var drag = Drag.NONE

    /** The start and the end that the bar shows now: during a drag, the times of the drag. */
    val shownStart: Long get() = start
    val shownEnd: Long get() = end

    init {
        name = "review-bounds-bar"
        isOpaque = false
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!isEnabled || !hasPoint) return
                drag = edgeAt(e.x).takeUnless { it == Drag.NONE } ?: Drag.SEEK
                moved(e.x)
            }

            override fun mouseDragged(e: MouseEvent) = moved(e.x)

            override fun mouseReleased(e: MouseEvent) {
                val was = drag
                drag = Drag.NONE
                if (was == Drag.START || was == Drag.END) onCommit(start, end)
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = if (hasPoint && edgeAt(e.x) != Drag.NONE) Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) else Cursor.getDefaultCursor()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this)
    }

    /**
     * Shows the shots of the clip below the bar, on two tracks. The upper track has the color of the hitter from the
     * start of the shot to the next shot. The lower track shows the time with a ball track, and its color says how
     * the hitter is known (white: the hit was seen, blue: the ball came back, gray: the hit was not seen). A line at
     * the end of the shot is green for in, red for out, and yellow for net.
     */
    fun setShots(shots: List<ShotView>) {
        if (shots == this.shots) return
        this.shots = shots
        repaint()
    }

    /** The shot under the pointer on the shot tracks. */
    override fun getToolTipText(event: MouseEvent): String? {
        if (event.y < shotsTop) return null
        val ms = msAt(event.x)
        return shots.lastOrNull { ms >= it.startMs && ms <= it.untilMs }?.let(ShotViews::tip)
    }

    private val shotsTop get() = LABEL_HEIGHT + TRACK_HEIGHT + SHOTS_GAP

    /**
     * Shows the clip from [pointStart] to [pointEnd] in the window from [windowStart] to [windowEnd]. The edges stay
     * between [minStart] and [maxEnd], the ends of the free time around the clip. Null times show an empty bar.
     */
    fun setState(windowStart: Long, windowEnd: Long, pointStart: Long?, pointEnd: Long?, minStart: Long, maxEnd: Long, others: List<LongRange>) {
        if (drag == Drag.START || drag == Drag.END) return
        this.windowStart = windowStart
        this.windowEnd = windowEnd.coerceAtLeast(windowStart + 1)
        hasPoint = pointStart != null && pointEnd != null
        start = pointStart ?: 0L
        end = pointEnd ?: 0L
        this.minStart = minStart
        this.maxEnd = maxEnd
        this.others = others
        repaint()
    }

    fun setPlayhead(ms: Long) {
        if (ms == playheadMs) return
        playheadMs = ms
        repaint()
    }

    private val trackX get() = PAD
    private val trackWidth get() = (width - 2 * PAD).coerceAtLeast(1)

    private fun xOf(ms: Long): Int = trackX + ((ms - windowStart).toDouble() / (windowEnd - windowStart) * trackWidth).roundToInt()

    private fun msAt(x: Int): Long =
        (windowStart + (x - trackX).toDouble() / trackWidth * (windowEnd - windowStart)).roundToLong().coerceIn(windowStart, windowEnd)

    private fun edgeAt(x: Int): Drag {
        val toStart = abs(x - xOf(start))
        val toEnd = abs(x - xOf(end))
        return when {
            toStart > GRAB && toEnd > GRAB -> Drag.NONE
            // Between two near edges, the nearer edge wins. Outside the clip, the edge on that side wins.
            toStart <= toEnd -> Drag.START
            else -> Drag.END
        }
    }

    private fun moved(x: Int) {
        val ms = msAt(x)
        when (drag) {
            Drag.START -> {
                start = ms.coerceIn(minStart, end - MIN_LENGTH_MS)
                onPreview(start)
            }
            Drag.END -> {
                end = ms.coerceIn(start + MIN_LENGTH_MS, maxEnd)
                onPreview(end)
            }
            Drag.SEEK -> onSeek(ms)
            Drag.NONE -> return
        }
        repaint()
    }

    override fun getPreferredSize() = Dimension(400, BAR_HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val top = LABEL_HEIGHT
            val h = TRACK_HEIGHT
            g2.color = Palette.BG
            g2.fillRoundRect(trackX, top, trackWidth, h, 8, 8)
            g2.color = Palette.LINE_2
            g2.drawRoundRect(trackX, top, trackWidth, h, 8, 8)
            // A tick for each second, so the user sees the length of a drag.
            g2.color = Palette.LINE_2
            var tick = (windowStart / 1000 + 1) * 1000
            while (tick < windowEnd) {
                val x = xOf(tick)
                g2.drawLine(x, top + h - (if (tick % 5000 == 0L) 9 else 4), x, top + h - 1)
                tick += 1000
            }
            g2.clipRect(trackX, 0, trackWidth + 1, height)
            others.forEach { other ->
                val x1 = xOf(other.first.coerceAtLeast(windowStart))
                val x2 = xOf(other.last.coerceAtMost(windowEnd))
                if (x2 > x1) {
                    g2.color = Palette.LINE_3
                    g2.fillRoundRect(x1, top + 6, x2 - x1, h - 12, 6, 6)
                }
            }
            if (hasPoint) {
                val x1 = xOf(start)
                val x2 = xOf(end)
                g2.color = Palette.LIME_GLOW
                g2.fillRect(x1, top + 1, x2 - x1, h - 1)
                g2.color = Palette.LIME
                g2.fillRoundRect(x1 - EDGE / 2, top - 2, EDGE, h + 4, 4, 4)
                g2.fillRoundRect(x2 - EDGE / 2, top - 2, EDGE, h + 4, 4, 4)
            }
            g2.clip = null
            if (hasPoint) {
                g2.font = UiKit.font(11f)
                g2.color = Palette.FG_2
                val metrics = g2.fontMetrics
                val startText = Timecode.format(start)
                val endText = Timecode.format(end)
                val length = String.format(Locale.ROOT, "%.1f s", (end - start) / 1000.0)
                val startX = (xOf(start) - metrics.stringWidth(startText) / 2).coerceIn(0, width - metrics.stringWidth(startText))
                val endX = (xOf(end) - metrics.stringWidth(endText) / 2).coerceIn(0, width - metrics.stringWidth(endText))
                g2.drawString(startText, startX, metrics.ascent)
                // The two times can be at the same place for a short clip: then only the start shows.
                if (endX > startX + metrics.stringWidth(startText) + 8) g2.drawString(endText, endX, metrics.ascent)
                val middle = (xOf(start) + xOf(end) - metrics.stringWidth(length)) / 2
                if (xOf(end) - xOf(start) > metrics.stringWidth(length) + 24) {
                    g2.color = Palette.FG
                    g2.drawString(length, middle, top + (h + metrics.ascent) / 2 - 1)
                }
            }
            g2.clipRect(trackX, 0, trackWidth + 1, height)
            val upper = shotsTop
            val lower = upper + SHOT_TRACK + 2
            shots.forEach { shot ->
                val x1 = xOf(shot.startMs)
                g2.color = shot.color
                g2.fillRoundRect(x1, upper, (xOf(shot.untilMs) - x1 - 1).coerceAtLeast(2), SHOT_TRACK, 3, 3)
                g2.color = shot.howColor
                g2.fillRoundRect(x1, lower, (xOf(shot.trackedMs) - x1).coerceAtLeast(2), SHOT_TRACK, 3, 3)
            }
            shots.forEach { shot ->
                val color = shot.endColor ?: return@forEach
                val x = xOf(shot.endMs ?: shot.trackedMs)
                g2.color = Palette.BG
                g2.fillRect(x - 2, upper - 2, 5, 2 * SHOT_TRACK + 6)
                g2.color = color
                if (shot.predicted) {
                    // A predicted end: a broken line.
                    val part = (2 * SHOT_TRACK + 6) / 5
                    (0..2).forEach { g2.fillRect(x - 1, upper - 2 + 2 * it * part, 3, part) }
                } else {
                    g2.fillRect(x - 1, upper - 2, 3, 2 * SHOT_TRACK + 6)
                }
            }
            g2.clip = null
            if (playheadMs in windowStart..windowEnd) {
                val x = xOf(playheadMs)
                g2.color = Palette.FG
                g2.fillRect(x - 1, top - 3, 2, height - top + 2)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD = 10
        const val LABEL_HEIGHT = 18
        const val TRACK_HEIGHT = 42

        /** Below the bar: two shot tracks of this height. */
        const val SHOTS_GAP = 6
        const val SHOT_TRACK = 7
        const val BAR_HEIGHT = LABEL_HEIGHT + TRACK_HEIGHT + SHOTS_GAP + 2 * SHOT_TRACK + 6
        const val EDGE = 6
        const val GRAB = 9
        const val MIN_LENGTH_MS = 300L
    }
}

/**
 * The review window of the automatic points (decisions 21 to 26 of docs/auto-analysis/app-integration.md): a window
 * that is not modal, with its own player, as the replay editor is. The user plays each point, corrects its winner and
 * its bounds, deletes wrong points, and adds missing points. "Accept all points and close" completes the review.
 *
 * The window owns [player] and closes it. [onClosed] runs after the window closes.
 */
internal class ReviewWindow(
    owner: Window?,
    private val player: SwingMediaPlayer,
    videoFile: File,
    private val adjustments: AdjustmentsV1,
    private val host: ReviewHost,
    /** The data of the detection overlay, or null when the project has none. */
    private val overlayFile: File?,
    /** The point to select first, or null for the first point to review. */
    selectId: String?,
    private val onClosed: () -> Unit,
) : JFrame("Review the detected points") {
    private var points: List<PointV1> = emptyList()
    private var selectedId: String? = null
    private var closed = false

    /** The playback pauses at this time: the end of the point that plays. Null plays on. */
    private var stopAtMs: Long? = null

    private val bar = ReviewBoundsBar(
        onPreview = { ms -> stopAtMs = null; player.pause(); player.seek(ms); showTime(ms) },
        onCommit = { start, end -> commitBounds(start, end) },
        onSeek = { ms -> stopAtMs = null; player.seek(ms); showTime(ms) },
    )
    private val clock = JLabel().apply {
        name = "review-clock"
        font = UiKit.font(15f)
        foreground = Palette.FG
    }
    private val playButton = button("Play point", Material2MZ.PLAY_ARROW, "review-play", "Space", UiButton.Kind.LIME) { togglePlay() }
    /** The frame step toggle of the Points tab, with the same setting: Left and Right then step one frame while paused. */
    private val frameStep = FrameStepToggle().apply {
        name = "review-frame-step"
        on = SessionSettings.frameStepWhenPaused
        addActionListener { toggleFrameStep() }
    }

    /**
     * The speed list of the Points tab. It is compact: Up and Down select the points in this window, and do not
     * change the speed. The speed stays for the session.
     */
    private val speed = SpeedControl("review-speed") { index -> setSpeedIndex(index); focusVideo() }.apply {
        compact = true
        setSpeedIndex(SessionSettings.clampIndex(SessionSettings.playbackSpeedIndex))
    }
    private val speedCaption = JLabel(SpeedControl.CAPTION).apply {
        font = UiKit.font(12f)
        foreground = Palette.FG_2
    }

    /** Shows what the colors of the timeline mean. */
    private val colorsHelp = UiButton("Colors", Material2AL.HELP_OUTLINE, UiButton.Kind.SECONDARY).apply {
        name = "review-colors-help"
        toolTipText = "Shows what the colors of the timeline mean"
        isFocusable = false
        addActionListener { showColorsHelp() }
    }

    /** Shows or hides all parts of the overlay. The switches of the parts keep their state. */
    private val overlayMaster = SwitchBox("Overlay", 12f).apply {
        name = "review-overlay-all"
        toolTipText = "Shows or hides all parts of the detection overlay (V)"
        isFocusable = false
        isSelected = OVERLAY_OFF !in host.overlayLayers()
    }

    /** One switch for each part of the detection overlay (debug view). The window keeps their state for the next time. */
    private val overlaySwitches = OverlayLayer.entries.associateWith { layer ->
        SwitchBox(layer.text, 12f).apply {
            name = "review-overlay-${layer.name.lowercase(Locale.ROOT)}"
            toolTipText = "Draws this part of the detection data on the video (debug view)"
            isFocusable = false
            isSelected = overlayFile != null && layer.name in host.overlayLayers()
        }
    }
    /** Opens the window with the switches of the overlay parts. */
    private val overlaySettings = UiButton("Overlay settings", Material2MZ.TUNE, UiButton.Kind.SECONDARY).apply {
        name = "review-overlay-settings"
        toolTipText = "Opens the switches of the parts of the detection overlay"
        isFocusable = false
        addActionListener { showOverlaySettings() }
    }

    /** The switches go to a second line when the window is too narrow for one line: the row then has the height of its lines. */
    private val overlayRow = object : JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, OVERLAY_ROW_GAP, OVERLAY_ROW_GAP / 2)) {
        override fun getPreferredSize(): Dimension {
            val one = super.getPreferredSize()
            val width = parent?.width?.takeIf { it > 0 } ?: return one
            var lines = 1
            var x = OVERLAY_ROW_GAP
            var lineHeight = 0
            components.filter { it.isVisible }.forEach { part ->
                val size = part.preferredSize
                if (x > OVERLAY_ROW_GAP && x + size.width + OVERLAY_ROW_GAP > width) {
                    lines++
                    x = OVERLAY_ROW_GAP
                }
                x += size.width + OVERLAY_ROW_GAP
                lineHeight = maxOf(lineHeight, size.height)
            }
            return Dimension(width, lines * lineHeight + (lines + 1) * OVERLAY_ROW_GAP / 2)
        }
    }.apply {
        isOpaque = false
        isVisible = overlayFile != null
        add(overlayMaster)
        add(overlaySettings)
    }

    /** The window with the switches of the overlay parts. It is not modal: the user changes a switch and sees the video. */
    private var overlayDialog: javax.swing.JDialog? = null
    private var overlay: DetectOverlay? = null

    /** The shots of the selected point, from the shot chain of its rally. */
    private var shotViews: List<ShotView> = emptyList()
    private var overlayShownMs = -1L
    private val overlayTicker = Timer(OVERLAY_TICK_MS) {
        val now = player.currentTimeMs()
        if (now != overlayShownMs) drawOverlay(now)
    }

    private val sub = JLabel().apply {
        name = "review-count"
        font = UiKit.font(12f)
        foreground = Palette.FG_3
    }
    private val model = DefaultListModel<Row>()
    private val list = JList(model).apply {
        name = "review-list"
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RowRenderer()
        fixedCellHeight = ROW_HEIGHT
        background = Palette.PANEL
        // The keys of the window select the rows. A list with the focus would use the letter keys for a search.
        isFocusable = false
    }
    private var listSync = false
    private val pointTitle = JLabel(" ").apply {
        name = "review-point-title"
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    private val pointNote = JLabel(" ").apply {
        name = "review-point-note"
        font = UiKit.font(12f)
        foreground = Palette.FG_3
    }
    private val nearLabel = JLabel(" ").apply {
        name = "review-near"
        font = UiKit.font(12.5f)
        foreground = Palette.FG
        toolTipText = "The player that the detection saw at the near end in this point. " +
            "If the name is wrong, the detection has the players wrong: click \"Swap players\"."
    }
    private val winner = SegmentedChoice(
        "review-winner",
        listOf(
            SegmentedChoice.Option(Outcome.P1, host.players.name(1), tooltip = "Q"),
            SegmentedChoice.Option(Outcome.NONE, "No winner", tooltip = "W"),
            SegmentedChoice.Option(Outcome.P2, host.players.name(2), tooltip = "E"),
        ),
    )
    private val acceptButton = button("Accept, next", Material2AL.CHECK, "review-accept", "T", UiButton.Kind.LIME_OUTLINE) { acceptAndNext() }
    private val deleteButton = button("Delete", Material2AL.DELETE_OUTLINE, "review-delete", "Delete", UiButton.Kind.DANGER) { deleteSelected() }
    private val addButton = button("Add point", Material2AL.ADD, "review-add", "Adds a point of 5 s at the playhead (N)") { addAtPlayhead() }
    private val swapButton = button(
        "Swap players",
        Material2MZ.SWAP_HORIZ,
        "review-swap",
        "For a wrong name at \"Near end\": gives the other player and the other winner to one point, to the next points, or to all",
    ) { showSwapMenu() }
    /** What the detection found in the selected point, and why it gave the winner (decision 29). */
    private val evidence = javax.swing.JTextArea().apply {
        name = "review-evidence"
        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = UiKit.font(11.5f)
        foreground = Palette.FG_2
        border = BorderFactory.createEmptyBorder()
    }

    /** The evidence text can have a line for each shot. The box has a height limit, so the list of points stays in view. */
    private val evidenceBox = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            val limit = EVIDENCE_ROWS * evidence.getFontMetrics(evidence.font).height
            return Dimension(size.width, minOf(size.height, limit))
        }
    }.apply {
        name = "review-evidence-box"
        isOpaque = false
        add(JScrollPane(evidence).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
    }

    /** The note of the user about the detection of the selected point. [noteForId] is the point of the text. */
    private val noteArea = javax.swing.JTextArea(NOTE_ROWS, 20).apply {
        name = "review-note"
        lineWrap = true
        wrapStyleWord = true
        font = UiKit.font(12f)
        foreground = Palette.FG
        caretColor = Palette.FG
        background = Palette.PANEL_DIM
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
        toolTipText = "What the detection did not do correctly in this point. The lab reads these notes. Escape goes back to the keys."
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) = saveNote()
        })
    }
    private var noteForId: String? = null
    private val message = JLabel(" ").apply {
        name = "review-message"
        font = UiKit.font(12f)
        foreground = Palette.YELLOW
    }
    private val acceptAllButton =
        button("Accept all points and close", Material2AL.DONE_ALL, "review-accept-all", "Completes the review", UiButton.Kind.LIME) { acceptAll() }
    private val laterButton = button("Continue later", null, "review-later", "Closes the window. The review stays open. (Esc)") { close() }

    init {
        name = "detect-review"
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = close()
            override fun windowOpened(e: WindowEvent?) = focusVideo()
        })
        contentPane = JPanel(BorderLayout()).apply {
            isFocusable = true
            background = Palette.PANEL
            add(buildVideoColumn(), BorderLayout.CENTER)
            add(buildSide(), BorderLayout.EAST)
        }
        installKeys()
        list.addListSelectionListener { event ->
            if (!listSync && !event.valueIsAdjusting) list.selectedValue?.let { select(it.point.id, play = true) }
        }
        winner.onChange { outcome -> setWinner(outcome) }
        (overlaySwitches.values + overlayMaster).forEach {
            it.addActionListener {
                val names = overlaySwitches.filterValues { switch -> switch.isSelected }.keys.map { layer -> layer.name }.toSet()
                host.setOverlayLayers(if (overlayMaster.isSelected) names else names + OVERLAY_OFF)
                applyOverlay()
            }
        }
        applyOverlay()

        player.onTimeChanged = { t -> EventQueue.invokeLater { onTime(t) } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { refreshPlayButton() } }

        owner?.iconImages?.takeIf { it.isNotEmpty() }?.let { iconImages = it }
        placeOnScreenOf(owner)

        player.load(videoFile)
        player.pause()
        player.applyPreviewAdjustments(adjustments)
        player.setRate(SessionSettings.toRate(SessionSettings.clampIndex(SessionSettings.playbackSpeedIndex)))
        reload()
        val first = points.firstOrNull { it.id == selectId }
            ?: points.firstOrNull { it.auto?.checked == false }
            ?: points.firstOrNull()
        first?.let { select(it.id, play = false) }
    }

    // ---- Layout --------------------------------------------------------------------------------------

    private fun button(
        text: String,
        ikon: org.kordamp.ikonli.Ikon?,
        componentName: String,
        tooltip: String,
        kind: UiButton.Kind = UiButton.Kind.SECONDARY,
        action: () -> Unit,
    ) = UiButton(text, ikon, kind).apply {
        name = componentName
        toolTipText = tooltip
        // Space plays the video, so a button does not take the focus.
        isFocusable = false
        addActionListener { action(); focusVideo() }
    }

    private fun placeOnScreenOf(owner: Window?) {
        val config = owner?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val screen = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val usableWidth = screen.width - insets.left - insets.right
        val usableHeight = screen.height - insets.top - insets.bottom
        minimumSize = Dimension(MIN_WIDTH.coerceAtMost(usableWidth), MIN_HEIGHT.coerceAtMost(usableHeight))
        val width = (usableWidth * SCREEN_SHARE).toInt().coerceAtLeast(minimumSize.width)
        val height = (usableHeight * SCREEN_SHARE).toInt().coerceAtLeast(minimumSize.height)
        setBounds(screen.x + insets.left + (usableWidth - width) / 2, screen.y + insets.top + (usableHeight - height) / 2, width, height)
    }

    private fun buildVideoColumn(): JComponent {
        val video = JPanel(BorderLayout()).apply {
            background = Palette.VIDEO_BG
            add(player.component, BorderLayout.CENTER)
        }
        val transport = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
            isOpaque = false
            add(button("−5 s", null, "review-back-long", "Shift+Left") { nudge(-LONG_STEP_MS) })
            add(button("−1 s", null, "review-back", "Left") { nudge(-STEP_MS) })
            add(button("Start here", null, "review-start-here", "Puts the start of the point at the playhead (I)") { setStartHere() })
            add(playButton)
            add(button("End here", null, "review-end-here", "Puts the end of the point at the playhead (O)") { setEndHere() })
            add(button("+1 s", null, "review-forward", "Right") { nudge(STEP_MS) })
            add(button("+5 s", null, "review-forward-long", "Shift+Right") { nudge(LONG_STEP_MS) })
        }
        val hint = JLabel("Drag an edge of the point to move its start or its end. Click the bar to move the playhead.").apply {
            font = UiKit.font(11.5f)
            foreground = Palette.FG_3
        }
        val controls = JPanel(BorderLayout(0, 8)).apply {
            background = Palette.OVERLAY
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
                BorderFactory.createEmptyBorder(10, 16, 12, 16),
            )
            // The overlay switches have their own line: there are many of them.
            add(JPanel(BorderLayout(0, 6)).apply {
                isOpaque = false
                add(overlayRow, BorderLayout.NORTH)
                add(hint, BorderLayout.CENTER)
                // The frame step toggle is at the right of the hint line: the transport row has no space for it.
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(speedCaption)
                    add(speed)
                    add(colorsHelp)
                    add(frameStep)
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(bar, BorderLayout.CENTER)
            add(JPanel(BorderLayout(16, 0)).apply {
                isOpaque = false
                add(clock, BorderLayout.WEST)
                add(transport, BorderLayout.CENTER)
                // The same width as the clock, so the transport is in the center.
                add(JLabel().apply { preferredSize = Dimension(CLOCK_WIDTH, 1) }, BorderLayout.EAST)
            }, BorderLayout.SOUTH)
        }
        clock.preferredSize = Dimension(CLOCK_WIDTH, 22)
        return JPanel(BorderLayout()).apply {
            add(video, BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }
    }

    private fun buildSide(): JComponent {
        val head = Stack(pad = Insets(14, 16, 10, 16), gap = 2).apply {
            isOpaque = false
            add(JLabel("Detected points").apply {
                font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
                foreground = Palette.FG
            })
            add(sub)
        }
        val scroll = JScrollPane(list).apply {
            border = BorderFactory.createMatteBorder(1, 0, 1, 0, Palette.LINE)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = ROW_HEIGHT
            viewport.background = Palette.PANEL
        }
        fun pair(left: JComponent, right: JComponent) = JPanel(GridLayout(1, 2, 8, 0)).apply {
            isOpaque = false
            add(left)
            add(right)
        }
        val selected = Stack(pad = Insets(12, 16, 12, 16), gap = 8).apply {
            isOpaque = false
            add(pointTitle)
            add(pointNote)
            add(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(nearLabel, BorderLayout.CENTER)
                add(swapButton, BorderLayout.EAST)
            })
            add(winner)
            add(evidenceBox)
            add(JLabel("Detection note").apply {
                font = UiKit.font(11f)
                foreground = Palette.FG_3
            })
            // A Stack gives a scroll pane the height of its text. The panel around it keeps the height of the rows.
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                name = "review-note-box"
                val insets = noteArea.insets
                val rowsHeight = NOTE_ROWS * noteArea.getFontMetrics(noteArea.font).height + insets.top + insets.bottom + 2
                preferredSize = java.awt.Dimension(100, rowsHeight)
                add(JScrollPane(noteArea).apply {
                    border = BorderFactory.createLineBorder(Palette.LINE)
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                }, BorderLayout.CENTER)
            })
            add(pair(acceptButton, deleteButton))
            add(addButton)
            add(message)
        }
        val footer = Stack(pad = Insets(12, 16, 14, 16), gap = 8).apply {
            isOpaque = true
            background = Palette.OVERLAY
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE)
            add(acceptAllButton)
            add(laterButton)
        }
        return JPanel(BorderLayout()).apply {
            name = "review-side"
            background = Palette.PANEL
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            preferredSize = Dimension(SIDE_WIDTH, 100)
            add(head, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(selected, BorderLayout.CENTER)
                add(footer, BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)
        }
    }

    private fun installKeys() {
        val root = rootPane
        fun bind(key: String, action: () -> Unit) {
            val id = "review.$key"
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), id)
            root.actionMap.put(id, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    // The note field gets the keys. Escape goes back to the keys of the window.
                    if (java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner === noteArea) {
                        if (key == "ESCAPE") focusVideo()
                        return
                    }
                    // An open menu gets the keys. Escape closes the menu, and not the window.
                    val menus = javax.swing.MenuSelectionManager.defaultManager()
                    if (menus.selectedPath.isNotEmpty()) {
                        if (key == "ESCAPE") menus.clearSelectedPath()
                        return
                    }
                    action()
                }
            })
        }
        bind("SPACE") { togglePlay() }
        bind("UP") { step(-1) }
        bind("DOWN") { step(1) }
        bind("LEFT") { nudge(-STEP_MS) }
        bind("RIGHT") { nudge(STEP_MS) }
        bind("shift LEFT") { nudge(-LONG_STEP_MS) }
        bind("shift RIGHT") { nudge(LONG_STEP_MS) }
        bind("F") { toggleFrameStep() }
        bind("V") { if (overlayRow.isVisible) overlayMaster.doClick() }
        bind("I") { setStartHere() }
        bind("O") { setEndHere() }
        bind("Q") { setWinner(Outcome.P1) }
        bind("W") { setWinner(Outcome.NONE) }
        bind("E") { setWinner(Outcome.P2) }
        bind("T") { acceptAndNext() }
        bind("N") { addAtPlayhead() }
        bind("DELETE") { deleteSelected() }
        bind("ESCAPE") { close() }
    }

    private fun focusVideo() {
        contentPane.requestFocusInWindow()
    }

    // ---- State ---------------------------------------------------------------------------------------

    private fun selectedPoint(): PointV1? = points.firstOrNull { it.id == selectedId }

    /** The Points tab calls it after the clips of the project changed. */
    fun pointsChanged() {
        if (!closed) reload()
    }

    /** Reads the clips of the project again and shows them. The selection stays, or goes to the nearest clip. */
    private fun reload() {
        val before = selectedPoint()
        points = host.points()
        if (points.none { it.id == selectedId }) {
            selectedId = before?.let { old -> points.minByOrNull { abs(it.startMs - old.startMs) }?.id }
        }
        listSync = true
        try {
            val rows = points.mapIndexed { index, point -> Row(index + 1, point, host.winnerOf(point.id)) }
            if (model.size != rows.size) {
                model.clear()
                rows.forEach(model::addElement)
            } else {
                rows.forEachIndexed { index, row -> if (model[index] != row) model[index] = row }
            }
            val index = points.indexOfFirst { it.id == selectedId }
            if (index >= 0) list.selectedIndex = index else list.clearSelection()
        } finally {
            listSync = false
        }
        refresh()
    }

    private fun refresh() {
        val point = selectedPoint()
        val total = player.totalDurationMs().takeIf { it > 0 } ?: points.lastOrNull()?.endMs?.toLong()?.plus(WINDOW_PAD_MS) ?: 1L
        if (point == null) {
            bar.setState(0, 1, null, null, 0, 1, emptyList())
        } else {
            val index = points.indexOf(point)
            val minStart = points.getOrNull(index - 1)?.endMs?.toLong() ?: 0L
            val maxEnd = points.getOrNull(index + 1)?.startMs?.toLong() ?: total
            bar.setState(
                (point.startMs - WINDOW_PAD_MS).coerceAtLeast(0),
                (point.endMs + WINDOW_PAD_MS).coerceAtMost(total.coerceAtLeast(point.endMs.toLong())),
                point.startMs.toLong(),
                point.endMs.toLong(),
                minStart,
                maxEnd,
                points.filter { it.id != point.id }.map { it.startMs.toLong()..it.endMs.toLong() },
            )
        }
        val auto = points.filter { it.auto != null }
        val open = auto.count { it.auto?.checked == false }
        sub.text = if (open > 0) "To review: $open of ${points.size} points" else "All ${points.size} points are reviewed"
        pointTitle.text = point?.let {
            "Point ${points.indexOf(it) + 1} · ${Timecode.format(it.startMs.toLong())} · ${seconds(it.endMs - it.startMs)}"
        } ?: "No point selected"
        pointNote.text = when {
            point == null -> " "
            point.auto == null -> "Added by you"
            point.auto.checked -> DetectTexts.rowNote(point.auto) + " · reviewed"
            else -> DetectTexts.rowNote(point.auto)
        }
        val near = point?.let { host.nearPlayerOf(it.id) }
        val rally = point?.let { host.detectionOf(it.id) }
        shotViews = rally?.let { ShotViews.of(it, near, host.players) }.orEmpty()
        refreshShots()
        evidence.text = when {
            rally != null -> EvidenceTexts.lines(rally, near, host.players).joinToString("\n")
            point?.auto != null -> "No detection data for this point."
            else -> ""
        }
        evidence.caretPosition = 0
        evidenceBox.isVisible = evidence.text.isNotEmpty()
        if (noteForId != point?.id) {
            saveNote()
            noteForId = point?.id
            noteArea.text = point?.let { host.noteOf(it.id) }.orEmpty()
            noteArea.caretPosition = 0
        }
        noteArea.isEnabled = point != null
        nearLabel.text = when {
            near != null -> "Near end: ${host.players.name(near)}"
            point?.auto != null -> "Near end: not known"
            else -> " "
        }
        nearLabel.icon = near?.let { PlayerDot(host.players.color(it)) }
        val canScore = point != null && point.auto?.kind != AutoKind.FAULT && point.endMs > point.startMs
        winner.isEnabled = canScore
        winner.selected = point?.takeIf { canScore }?.let { host.winnerOf(it.id) }
        acceptButton.isEnabled = point != null
        deleteButton.isEnabled = point != null
        swapButton.isEnabled = auto.isNotEmpty()
        acceptAllButton.isEnabled = points.isNotEmpty()
        refreshPlayButton()
    }

    private fun refreshPlayButton() {
        playButton.text = if (player.status() == PlayerStatus.PLAYING) "Pause" else "Play point"
    }

    /** Saves the text of the note field for its point, when the text is new. */
    private fun saveNote() {
        val id = noteForId ?: return
        val text = noteArea.text.trim()
        if (text != host.noteOf(id)) host.setNote(id, text)
    }

    private fun say(text: String) {
        message.text = text.ifEmpty { " " }
    }

    private fun seconds(ms: Int) = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)

    private fun showTime(ms: Long) {
        bar.setPlayhead(ms)
        clock.text = Timecode.format(ms)
    }

    // ---- Actions -------------------------------------------------------------------------------------

    /** Selects the clip with [id], brings its row into view, and shows its start. With [play], it plays the clip. */
    private fun select(id: String, play: Boolean) {
        val point = points.firstOrNull { it.id == id } ?: return
        selectedId = id
        say("")
        listSync = true
        try {
            val index = points.indexOf(point)
            list.selectedIndex = index
            list.ensureIndexIsVisible(index)
        } finally {
            listSync = false
        }
        refresh()
        player.seek(point.startMs.toLong())
        showTime(point.startMs.toLong())
        if (play) {
            stopAtMs = point.endMs.toLong()
            player.play()
        } else {
            stopAtMs = null
            player.pause()
        }
        refreshPlayButton()
    }

    private fun step(delta: Int) {
        val index = points.indexOfFirst { it.id == selectedId }
        points.getOrNull(if (index < 0) 0 else index + delta)?.let { select(it.id, play = true) }
    }

    /** Space: pauses, or plays. Inside the selected clip, the playback pauses at the end of the clip. */
    private fun togglePlay() {
        if (player.status() == PlayerStatus.PLAYING) {
            stopAtMs = null
            player.pause()
        } else {
            val point = selectedPoint()
            val now = player.currentTimeMs()
            if (point != null && now >= point.endMs - END_NEAR_MS && now <= point.endMs + END_NEAR_MS) {
                // At the end of the clip, Play shows the clip again.
                player.seek(point.startMs.toLong())
                stopAtMs = point.endMs.toLong()
            } else {
                stopAtMs = point?.endMs?.toLong()?.takeIf { now >= point.startMs && now < it }
            }
            player.play()
        }
        refreshPlayButton()
    }

    /** Gives the speed preset [index] to the player and keeps it for the session. */
    private fun setSpeedIndex(index: Int) {
        val clamped = SessionSettings.clampIndex(index)
        SessionSettings.playbackSpeedIndex = clamped
        player.setRate(SessionSettings.toRate(clamped))
        speed.setSpeedIndex(clamped)
    }

    private fun toggleFrameStep() {
        SessionSettings.frameStepWhenPaused = !SessionSettings.frameStepWhenPaused
        frameStep.on = SessionSettings.frameStepWhenPaused
    }

    private fun nudge(deltaMs: Long) {
        stopAtMs = null
        // With Frame step on, a step of 1 s is a step of one frame while the video is paused (as in the Points tab).
        if (abs(deltaMs) == STEP_MS && SessionSettings.frameStepWhenPaused && player.status() != PlayerStatus.PLAYING) {
            showTime(if (deltaMs > 0) player.stepFrameForward() else player.stepFrameBackward())
            return
        }
        val target = (player.currentTimeMs() + deltaMs).coerceAtLeast(0)
        player.seek(target)
        showTime(target)
    }

    private fun onTime(sourceMs: Long) {
        if (closed) return
        showTime(sourceMs)
        val stop = stopAtMs
        if (stop != null && sourceMs >= stop) {
            stopAtMs = null
            player.pause()
            refreshPlayButton()
        }
    }

    private fun setStartHere() {
        val point = selectedPoint() ?: return
        commitBounds(player.currentTimeMs(), point.endMs.toLong())
    }

    private fun setEndHere() {
        val point = selectedPoint() ?: return
        commitBounds(point.startMs.toLong(), player.currentTimeMs())
    }

    private fun commitBounds(startMs: Long, endMs: Long) {
        val point = selectedPoint() ?: return
        if (startMs == point.startMs.toLong() && endMs == point.endMs.toLong()) return
        stopAtMs = null
        if (host.setBounds(point.id, startMs, endMs)) {
            say("")
            host.markChecked(listOf(point.id))
        } else {
            say("The point cannot have these times: it is too short, or it is over another point.")
        }
        reload()
    }

    private fun showSwapMenu() {
        val menu = javax.swing.JPopupMenu().apply { name = "review-swap-menu" }
        SwapScope.entries.forEach { scope ->
            menu.add(javax.swing.JMenuItem(scope.text).apply {
                name = "review-swap-${scope.name.lowercase(Locale.ROOT)}"
                isEnabled = scope == SwapScope.ALL || selectedPoint()?.auto != null
                addActionListener { swapPlayers(scope); focusVideo() }
            })
        }
        menu.show(swapButton, 0, swapButton.height)
    }

    /**
     * Gives the automatic points of [scope] the other player at the near end and the other winner (decision 27). It
     * is not a review of the points: their dots stay.
     */
    internal fun swapPlayers(scope: SwapScope) {
        val current = selectedPoint()
        val auto = points.filter { it.auto != null }
        val ids = when (scope) {
            SwapScope.POINT -> listOfNotNull(current?.takeIf { it.auto != null })
            SwapScope.FROM_POINT -> if (current == null) emptyList() else auto.filter { it.startMs >= current.startMs }
            SwapScope.ALL -> auto
        }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        host.swapWinners(ids)
        reload()
        say(if (ids.size == 1) "Swapped the players of 1 point." else "Swapped the players of ${ids.size} points.")
    }

    private fun setWinner(outcome: Outcome) {
        val point = selectedPoint() ?: return
        if (!winner.isEnabled) return
        host.setWinner(point.id, outcome)
        host.markChecked(listOf(point.id))
        reload()
    }

    /** Accepts the selected clip, and plays the next clip to review after it. After the last, it goes to the first. */
    private fun acceptAndNext() {
        val current = selectedPoint() ?: return
        host.markChecked(listOf(current.id))
        reload()
        val open = points.filter { it.auto?.checked == false }
        val next = open.firstOrNull { it.startMs > current.startMs } ?: open.firstOrNull()
        if (next != null) select(next.id, play = true) else say("All points are reviewed.")
    }

    private fun deleteSelected() {
        val point = selectedPoint() ?: return
        player.pause()
        val name = Timecode.format(point.startMs.toLong()) + " - " + Timecode.format(point.endMs.toLong())
        val confirmed = MessageDialog.confirm(
            this,
            "Delete the point?",
            "Point $name",
            confirmLabel = "Delete",
            destructive = true,
            confirmKeys = true,
        )
        if (!confirmed) return
        val index = points.indexOf(point)
        val next = points.getOrNull(index + 1) ?: points.getOrNull(index - 1)
        host.deletePoint(point.id)
        selectedId = next?.id
        reload()
        next?.let { select(it.id, play = false) }
    }

    private fun addAtPlayhead() {
        val now = player.currentTimeMs()
        val inside = points.any { now >= it.startMs && now < it.endMs }
        val next = points.firstOrNull { it.startMs > now }?.startMs?.toLong()
        val total = player.totalDurationMs().takeIf { it > 0 } ?: Long.MAX_VALUE
        val end = minOf(now + NEW_POINT_MS, next ?: Long.MAX_VALUE, total)
        val id = if (inside || end - now < MIN_NEW_POINT_MS) null else host.addPoint(now, end)
        if (id == null) {
            say("No room for a new point at the playhead.")
            return
        }
        reload()
        select(id, play = false)
        say("New point: drag its edges on the bar.")
    }

    private fun acceptAll() {
        host.markChecked(points.filter { it.auto != null }.map { it.id })
        close()
    }

    // ---- Overlay -------------------------------------------------------------------------------------

    /** The rows of the color help: a color (null for a title row) and its meaning. */
    internal fun colorRows(): List<Pair<Color?, String>> = listOf(
        null to "Bar",
        Palette.LIME to "The selected point. Drag an edge to move its start or its end.",
        Palette.LINE_3 to "Other points",
        null to "Upper shot track: the hitter",
        host.players.p1Color to host.players.p1Name,
        host.players.p2Color to host.players.p2Name,
        null to "Lower shot track: the time with a ball track",
        Palette.FG to "The hit was seen (also the first shot)",
        Palette.BLUE to "The ball came back, the hit was not seen",
        Palette.FG_3 to "The hit was not seen, and the ball did not come back. Also: a shot after the point (the two tracks are gray)",
        null to "Line at the end of a shot",
        Palette.GREEN to "In",
        Palette.RED to "Out, or a bounce on the side of the hitter",
        Palette.YELLOW to "Net",
        null to "Broken line: the end was not seen. The flight of the ball predicts it (gray: too near to a line).",
        null to "No line: the end of the shot is not known. The shot tracks need the \"Shots\" switch.",
    )

    private fun showColorsHelp() {
        val panel = JPanel(GridLayout(0, 1, 0, 4)).apply {
            name = "review-colors-panel"
            background = Palette.OVERLAY
            border = BorderFactory.createEmptyBorder(10, 14, 10, 14)
            colorRows().forEach { (color, text) ->
                add(JLabel(text).apply {
                    font = UiKit.font(12f)
                    foreground = if (color == null) Palette.FG_2 else Palette.FG
                    if (color != null) {
                        icon = PlayerDot(color)
                        iconTextGap = 8
                    }
                })
            }
        }
        javax.swing.JPopupMenu().apply {
            border = BorderFactory.createLineBorder(Palette.LINE)
            add(panel)
            show(colorsHelp, 0, -preferredSize.height - 4)
        }
    }

    private fun showOverlaySettings() {
        val dialog = overlayDialog ?: javax.swing.JDialog(this, "Overlay settings", false).apply {
            name = "review-overlay-dialog"
            // The keys of the review window (play, step, V) continue to operate while this window is open.
            focusableWindowState = false
            isResizable = false
            contentPane = JPanel(GridLayout(0, 1, 0, 8)).apply {
                background = Palette.BG
                border = BorderFactory.createEmptyBorder(14, 18, 14, 24)
                overlaySwitches.values.forEach { add(it) }
            }
            pack()
            // Above the button, so the video stays in view.
            val at = runCatching { overlaySettings.locationOnScreen }.getOrNull()
            if (at != null) setLocation(at.x, maxOf(0, at.y - height - 8)) else setLocationRelativeTo(this@ReviewWindow)
        }.also { overlayDialog = it }
        dialog.isVisible = true
        dialog.toFront()
    }

    /** The shots are on the timeline when the "Shots" switch is on. */
    private fun refreshShots() {
        val on = overlayFile != null && overlayMaster.isSelected && overlaySwitches[OverlayLayer.SHOTS]?.isSelected == true
        bar.setShots(if (on) shotViews else emptyList())
    }

    private fun applyOverlay() {
        val file = overlayFile
        val layers = if (overlayMaster.isSelected) overlaySwitches.filterValues { it.isSelected }.keys else emptySet()
        overlaySwitches.values.forEach { it.isEnabled = overlayMaster.isSelected }
        refreshShots()
        if (layers.isEmpty() || file == null) {
            overlayTicker.stop()
            player.setPreviewOverlay(null)
            return
        }
        // The window reads the data one time, at the first switch.
        val current = overlay ?: runCatching { DetectOverlayData.read(file) }
            .map { data ->
                DetectOverlay(data) {
                    player.videoSize()?.let { size -> GeometryTimeline.planAt(adjustments, player.currentTimeMs(), size.width, size.height) }
                }
            }
            .onFailure {
                overlaySwitches.values.forEach { switch -> switch.isSelected = false }
                say("The overlay data does not open: ${it.message}")
            }
            .getOrNull() ?: return
        overlay = current
        current.layers = layers
        // The shot panel shows during the selected point, with the same size for each shot.
        current.shotStarts = { shotViews }
        current.shotPanel = { t ->
            val ms = (t * 1000).toLong()
            selectedPoint()?.takeIf { shotViews.isNotEmpty() && ms >= it.startMs && ms <= it.endMs + SHOT_PANEL_AFTER_MS }
                ?.let { ShotViews.panelAt(shotViews, ms) }
        }
        // The outcome texts are for the selected point: from 1 s before its last shot to 3 s after the rally.
        current.outcome = { t ->
            selectedPoint()?.let { point ->
                host.detectionOf(point.id)?.takeIf { it.kind != org.litvin.autodetect.DetectImport.FAULT_KIND }?.let { rally ->
                    val from = (rally.lastShot?.t ?: rally.end) - 1.0
                    if (t >= from && t <= rally.end + 3.0) {
                        OutcomeMark(EvidenceTexts.outcomeLines(rally, host.nearPlayerOf(point.id), host.players), rally.lastShot?.t)
                    } else {
                        null
                    }
                }
            }
        }
        overlayTicker.start()
        drawOverlay(player.currentTimeMs())
    }

    private fun drawOverlay(nowMs: Long) {
        val current = overlay ?: return
        overlayShownMs = nowMs
        player.setPreviewOverlay(VideoOverlay { area -> current.events(nowMs, area) })
    }

    // ---- Close ---------------------------------------------------------------------------------------

    /** Closes the window. The review stays open for the clips that are not accepted (decision 25). */
    fun close() {
        if (closed) return
        closed = true
        saveNote()
        overlayTicker.stop()
        overlayDialog?.dispose()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.setPreviewOverlay(null)
        player.pause()
        player.close()
        dispose()
        onClosed()
    }

    // ---- List ----------------------------------------------------------------------------------------

    private data class Row(val number: Int, val point: PointV1, val winner: Outcome)

    /** The color of a player as a round icon. */
    private class PlayerDot(private val color: Color) : javax.swing.Icon {
        override fun getIconWidth() = 12
        override fun getIconHeight() = 12
        override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = UiKit.smooth(g)
            try {
                g2.color = color
                g2.fillOval(x + 1, y + 1, 10, 10)
            } finally {
                g2.dispose()
            }
        }
    }

    private inner class RowRenderer : JComponent(), ListCellRenderer<Row> {
        private var row: Row? = null
        private var selected = false

        override fun getListCellRendererComponent(list: JList<out Row>, value: Row, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            row = value
            selected = isSelected
            val mark = value.point.auto
            toolTipText = mark?.let(DetectTexts::rowNote)
            return this
        }

        override fun paintComponent(g: Graphics) {
            val current = row ?: return
            val point = current.point
            val g2 = UiKit.smooth(g)
            try {
                g2.color = if (selected) Palette.LIME_ROW else Palette.PANEL
                g2.fillRect(0, 0, width, height)
                g2.color = Palette.LINE
                g2.drawLine(0, height - 1, width, height - 1)
                // The winner: a strip in the color of the player.
                g2.color = when (current.winner) {
                    Outcome.P1 -> host.players.color(1)
                    Outcome.P2 -> host.players.color(2)
                    else -> Palette.LINE_3
                }
                g2.fillRect(0, 0, 4, height - 1)
                val baseline = (height + g2.getFontMetrics(UiKit.font(13f)).ascent) / 2 - 2
                g2.font = UiKit.font(12f)
                g2.color = Palette.FG_3
                g2.drawString(current.number.toString(), 14, baseline)
                g2.font = UiKit.font(13f)
                g2.color = Palette.FG
                g2.drawString(Timecode.format(point.startMs.toLong()), 48, baseline)
                g2.font = UiKit.font(12f)
                g2.color = Palette.FG_2
                g2.drawString(seconds(point.endMs - point.startMs), 132, baseline)
                val who = when {
                    point.auto?.kind == AutoKind.FAULT -> "serve fault"
                    current.winner == Outcome.P1 -> host.players.name(1)
                    current.winner == Outcome.P2 -> host.players.name(2)
                    else -> "no winner"
                }
                g2.color = Palette.FG_3
                g2.drawString(who, 186, baseline)
                // The state: a dot for a clip to review, a check mark for a reviewed automatic clip.
                val mark = point.auto
                val x = width - 26
                val y = height / 2
                if (mark != null && !mark.checked) {
                    g2.color = if (mark.isWeak()) Palette.YELLOW else Palette.BLUE
                    g2.fillOval(x, y - 4, 8, 8)
                } else if (mark != null) {
                    g2.color = Palette.GREEN
                    g2.stroke = java.awt.BasicStroke(1.8f)
                    g2.drawPolyline(intArrayOf(x - 1, x + 3, x + 9), intArrayOf(y, y + 4, y - 4), 3)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val SCREEN_SHARE = 0.86
        const val MIN_WIDTH = 1000
        const val MIN_HEIGHT = 620
        const val SIDE_WIDTH = 440

        /** The shot panel stays this long after the end of the point. */
        const val SHOT_PANEL_AFTER_MS = 1_000L

        /** In the saved overlay switches: the master switch is off. */
        const val OVERLAY_OFF = "OFF"

        /** The height of the detection note field, in text rows. */
        const val NOTE_ROWS = 4

        /** The gap between two overlay switches. */
        const val OVERLAY_ROW_GAP = 14

        /** The evidence text shows this many rows. The other rows need a scroll. */
        const val EVIDENCE_ROWS = 8
        const val ROW_HEIGHT = 32
        const val CLOCK_WIDTH = 110
        const val STEP_MS = 1_000L
        const val LONG_STEP_MS = 5_000L

        /** The bounds bar shows this time before and after the selected clip. */
        const val WINDOW_PAD_MS = 8_000L
        const val NEW_POINT_MS = 5_000L
        const val MIN_NEW_POINT_MS = 500L
        const val END_NEAR_MS = 250L
        const val OVERLAY_TICK_MS = 33
    }
}
