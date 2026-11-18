package org.litvin.ui.tabs.export

import org.kordamp.ikonli.feather.Feather
import org.litvin.RenderJob
import org.litvin.export.preview.ExportPreviewDirector
import org.litvin.export.preview.ExportPreviewOverlay
import org.litvin.export.preview.ExportPreviewTimeline
import org.litvin.i18n.tr
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.media.mpv.MpvFrameReader
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.VideoPlaybackBar
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Rectangle2D
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.roundToLong

/**
 * The export preview (B-75): a modal window that plays the result of an export before the encode, with its own
 * libmpv player. The main window gets no input while it is open (decision 6). The window plays [job] as the export
 * makes it: the content, the replays, the cards, the scoreboard, the comments, and the colors, crop and rotation.
 *
 * The window owns [player] and closes it. The window has no Export button (decision 7).
 */
internal class ExportPreviewWindow(
    owner: Window?,
    private val player: SwingMediaPlayer,
    private val job: RenderJob,
    sourceFile: File,
    /** The duration of the source video, or null when it is not known yet. The player then gives it. */
    sourceDurationMs: Long?,
    /** The number of each point in the Points list. */
    private val pointNumbers: Map<String, Int>,
    title: String,
) : JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL) {
    private var director: ExportPreviewDirector? = null
    private var strip: ExportPreviewStrip? = null
    private var closed = false

    /** The overlay events that the video shows now, for a reference frame. A change sends a new overlay. */
    private var overlayShown: List<String>? = null

    private val playbackBar = VideoPlaybackBar(
        namePrefix = "export-preview",
        barName = "export-preview-transport",
        onTogglePlay = { togglePlay() },
        onSeek = { target -> drive { it.seek(target) } },
        onNudge = { delta -> nudge(delta) },
    )
    private val stripSlot = JPanel(BorderLayout()).apply { isOpaque = false }
    private val note = JLabel(tr("export.preview.note")).apply {
        name = "export-preview-note"
        font = UiKit.font(12.5f)
        foreground = Palette.FG_2
        icon = ExportUi.icon(Feather.INFO, 14, Palette.FG_2)
        iconTextGap = 8
        toolTipText = text
        isVisible = false
    }
    private val previousButton = UiButton(tr("export.preview.previous"), Feather.SKIP_BACK).apply {
        name = "export-preview-previous"
        toolTipText = tr("export.preview.previous.tooltip", "key" to tr("key.pageUp"))
        addActionListener { drive { it.previousItem() } }
    }
    private val nextButton = UiButton(tr("export.preview.next"), Feather.SKIP_FORWARD).apply {
        name = "export-preview-next"
        toolTipText = tr("export.preview.next.tooltip", "key" to tr("key.pageDown"))
        addActionListener { drive { it.nextItem() } }
    }
    private val closeButton = UiButton(tr("common.close")).apply {
        name = "export-preview-close"
        addActionListener { close() }
    }

    /** The clock of the cards and the still frames, and the time of the overlay animations. */
    private val ticker = Timer(TICK_MS) { onTick() }

    init {
        name = "export-preview"
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = close()
        })
        contentPane = JPanel(BorderLayout()).apply {
            background = Palette.PANEL
            add(JPanel(BorderLayout()).apply {
                background = Palette.VIDEO_BG
                add(player.component, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            add(buildControls(), BorderLayout.SOUTH)
        }
        installKeys()
        owner?.iconImages?.takeIf { it.isNotEmpty() }?.let { iconImages = it }
        placeOnScreenOf(owner)

        player.onTimeChanged = { t -> EventQueue.invokeLater { onTime(t) } }
        player.onPlaybackRestart = { EventQueue.invokeLater { director?.onRestart() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { refreshPlayButton() } }
        player.load(sourceFile)
        player.pause()
        player.applyPreviewAdjustments(job.adjustments)
        if (sourceDurationMs != null && sourceDurationMs > 0) {
            start(sourceDurationMs)
        } else {
            // The timeline of a full video needs the duration of the source. The player gives it after the load.
            player.onReady = { EventQueue.invokeLater { if (director == null) start(player.totalDurationMs()) } }
        }
    }

    /** Opens the window. It returns after the window closes. */
    fun showModal() {
        isVisible = true
    }

    private fun start(sourceDurationMs: Long) {
        if (closed) return
        val timeline = ExportPreviewTimeline.of(job, sourceDurationMs)
        val created = ExportPreviewDirector(
            timeline,
            sourceDurationMs,
            frameMs = { 1000.0 / (player.frameRate() ?: FALLBACK_FPS) },
        )
        director = created
        val itemStrip = ExportPreviewStrip(timeline, pointNumbers) { index ->
            drive { it.seek(timeline.items[index].outStartMs) }
        }
        strip = itemStrip
        stripSlot.add(itemStrip, BorderLayout.CENTER)
        stripSlot.revalidate()
        note.isVisible = timeline.hasCuts
        apply(created.open())
        ticker.start()
        refresh()
    }

    private fun buildControls(): JComponent {
        val items = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(10, 16, 6, 16)
            add(previousButton, BorderLayout.WEST)
            add(stripSlot, BorderLayout.CENTER)
            add(nextButton, BorderLayout.EAST)
        }
        val footer = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(8, 16, 12, 16)
            add(note, BorderLayout.CENTER)
            add(closeButton, BorderLayout.EAST)
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Palette.PANEL
            add(items)
            add(playbackBar)
            add(footer)
            add(Box.createVerticalStrut(0))
        }
    }

    /** 80% of the width and the height of the usable area of the screen of [owner], in the center of that area. */
    private fun placeOnScreenOf(owner: Window?) {
        val config = owner?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val screen = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val usableWidth = screen.width - insets.left - insets.right
        val usableHeight = screen.height - insets.top - insets.bottom
        val width = (usableWidth * SCREEN_SHARE).toInt()
        val height = (usableHeight * SCREEN_SHARE).toInt()
        minimumSize = Dimension(MIN_WIDTH.coerceAtMost(usableWidth), MIN_HEIGHT.coerceAtMost(usableHeight))
        setBounds(
            screen.x + insets.left + (usableWidth - width) / 2,
            screen.y + insets.top + (usableHeight - height) / 2,
            width.coerceAtLeast(minimumSize.width),
            height.coerceAtLeast(minimumSize.height),
        )
    }

    private fun installKeys() {
        val root = rootPane
        fun bind(key: Int, modifiers: Int, id: String, action: () -> Unit) {
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, modifiers), id)
            root.actionMap.put(id, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) = action()
            })
        }
        bind(KeyEvent.VK_SPACE, 0, "export-preview-play") { togglePlay() }
        bind(KeyEvent.VK_LEFT, 0, "export-preview-back-1s") { nudge(-1_000) }
        bind(KeyEvent.VK_RIGHT, 0, "export-preview-forward-1s") { nudge(1_000) }
        bind(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, "export-preview-back-5s") { nudge(-5_000) }
        bind(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, "export-preview-forward-5s") { nudge(5_000) }
        bind(KeyEvent.VK_PAGE_UP, 0, "export-preview-previous") { drive { it.previousItem() } }
        bind(KeyEvent.VK_PAGE_DOWN, 0, "export-preview-next") { drive { it.nextItem() } }
        bind(KeyEvent.VK_UP, 0, "export-preview-previous-up") { drive { it.previousItem() } }
        bind(KeyEvent.VK_DOWN, 0, "export-preview-next-down") { drive { it.nextItem() } }
        bind(KeyEvent.VK_ESCAPE, 0, "export-preview-close") { close() }
    }

    private fun togglePlay() {
        drive { if (it.playing) it.pause() else it.play() }
    }

    private fun nudge(deltaMs: Long) {
        playbackBar.flashSeek(deltaMs)
        drive { it.seek(it.outMs + deltaMs) }
    }

    /** Runs [step] on the director and gives its commands to the player. */
    private fun drive(step: (ExportPreviewDirector) -> List<ExportPreviewDirector.Command>) {
        val current = director ?: return
        if (closed) return
        apply(step(current))
        refresh()
    }

    private fun onTime(sourceMs: Long) {
        if (closed) return
        drive { it.onTime(sourceMs, player.status() == PlayerStatus.PLAYING) }
    }

    private fun onTick() {
        if (closed) return
        drive { it.tick() }
    }

    private fun apply(commands: List<ExportPreviewDirector.Command>) {
        for (command in commands) {
            when (command) {
                is ExportPreviewDirector.Command.Seek -> player.seek(command.sourceMs)
                ExportPreviewDirector.Command.Play -> player.play()
                ExportPreviewDirector.Command.Pause -> player.pause()
                is ExportPreviewDirector.Command.Look -> player.setReplayView(command.view)
                is ExportPreviewDirector.Command.Sound -> player.setReplaySound(command.sound)
                is ExportPreviewDirector.Command.ShowFrame -> {
                    player.pause()
                    // An exact seek of mpv also shows a frame that starts up to 5 ms before its target.
                    player.seek(command.frameFromMs.coerceAtLeast(0.0).roundToLong() + MpvFrameReader.HR_SEEK_TOLERANCE_MS)
                }
            }
        }
    }

    /** Shows the time, the current item, the play button, and the overlay of the director. */
    private fun refresh() {
        val current = director ?: return
        playbackBar.setTime(current.outMs, current.timeline.durationMs)
        strip?.setPosition(current.index, current.outMs)
        refreshPlayButton()
        refreshOverlay(current)
    }

    private fun refreshPlayButton() {
        playbackBar.setPlaying(director?.playing ?: false)
    }

    private fun refreshOverlay(current: ExportPreviewDirector) {
        val overlay = current.overlay
        val shown = ExportPreviewOverlay.events(job, overlay, REFERENCE_AREA)
        if (shown == overlayShown) return
        overlayShown = shown
        player.setPreviewOverlay(
            if (shown.isEmpty()) null else VideoOverlay { area -> ExportPreviewOverlay.events(job, overlay, area) },
        )
    }

    private fun close() {
        if (closed) return
        closed = true
        ticker.stop()
        player.onTimeChanged = null
        player.onPlaybackRestart = null
        player.onStatusChanged = null
        player.onReady = null
        player.setPreviewOverlay(null)
        player.setReplayView(null)
        player.setReplaySound(null)
        player.pause()
        player.close()
        dispose()
    }

    private companion object {
        const val SCREEN_SHARE = 0.8
        const val MIN_WIDTH = 860
        const val MIN_HEIGHT = 600
        const val TICK_MS = 40
        const val FALLBACK_FPS = 30.0

        /** The frame that finds a change of the overlay. The real frame is the video area of the player. */
        val REFERENCE_AREA = Rectangle2D.Double(0.0, 0.0, 1920.0, 1080.0)
    }
}
