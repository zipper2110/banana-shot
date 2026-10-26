package org.litvin.ui.tabs.points.replay

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ReplayBadgeAss
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.tr
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import org.litvin.ui.tabs.points.ReplayDirector
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * The replay editor (B-50 T4): a window that is not modal, with its own player, as in
 * `design/instant-replay/index.html`. The user sets the part, the zoom frame, the speed, the sound, the repeats and
 * the cut effect of each replay of one point. Preview plays the end of the point with its replay, as the Points tab
 * and the export play it. Save gives all replays of the point to [onSave].
 *
 * The window owns [player] and closes it. [onClosed] runs after the window closes, with Save or without it.
 */
internal class ReplayEditorWindow(
    owner: Window?,
    private val player: SwingMediaPlayer,
    videoFile: File,
    adjustments: AdjustmentsV1,
    private val point: PointV1,
    pointTitle: String,
    playheadMs: Long,
    /** The replay to select, or null to add a new replay at [playheadMs]. */
    selectIndex: Int?,
    private val onSave: (List<ReplayV1>) -> Unit,
    private val onClosed: () -> Unit,
) : JDialog(owner, tr("replay.editor.title", "point" to pointTitle), ModalityType.MODELESS) {
    private val edits = ReplayEditorModel(point, selectIndex ?: 0)
    private val director = ReplayDirector()
    private var previewing = false
    private var closed = false

    private val partBar = ReplayPartBar(
        onStart = { ms -> edits.setStart(ms); seek(ms); refresh() },
        onEnd = { ms -> edits.setEnd(ms); seek(ms); refresh() },
        onSeek = { ms -> stopPreview(); seek(ms) },
    )
    private val zoomOverlay = ReplayZoomOverlay(player) { zoom -> edits.setZoom(zoom); refreshSide() }

    private val clockNow = JLabel().apply {
        name = "replay-clock"
        font = UiKit.font(15f)
        foreground = Palette.FG
    }
    private val clockAbs = JLabel().apply {
        font = UiKit.font(12f)
        foreground = Palette.FG_3
    }
    private val previewButton = UiButton(tr("replay.editor.preview"), Material2MZ.PLAY_ARROW, UiButton.Kind.LIME).apply {
        name = "replay-preview"
        isFocusable = false
    }

    private val sub = JLabel().apply {
        font = UiKit.font(12f)
        foreground = Palette.FG_3
    }
    private val tabs = SegmentedChoice<Int>("replay-tabs", emptyList())
    private val addButton = UiButton(tr("replay.editor.add"), Material2AL.ADD, buttonHeight = 26).apply {
        name = "replay-add"
        toolTipText = tr("replay.editor.add.tooltip")
        isFocusable = false
    }
    private val startValue = valueLabel("replay-start")
    private val endValue = valueLabel("replay-end")
    private val partGroup = DialogGroup(tr("replay.editor.part"), Material2AL.CONTENT_CUT, "replay-part")
    private val zoomGroup = DialogGroup(tr("replay.editor.zoom"), Material2MZ.ZOOM_IN, "replay-zoom")
    private val speedGroup = DialogGroup(tr("replay.editor.speed"), Material2MZ.SPEED, "replay-speed")
    private val soundGroup = DialogGroup(tr("replay.editor.sound"), Material2MZ.VOLUME_UP, "replay-sound")
    private val repeatGroup = DialogGroup(tr("replay.editor.repeat"), Material2MZ.REPEAT, "replay-repeat")
    private val effectGroup = DialogGroup(tr("replay.editor.effect"), Material2MZ.SLOW_MOTION_VIDEO, "replay-effect")
    private val zoomChoice = SegmentedChoice(
        "replay-zoom-choice",
        listOf(1.0 to tr("replay.editor.zoom.off"), 1.5 to "1.5×", 2.0 to "2×", 3.0 to "3×")
            .map { (value, label) -> SegmentedChoice.Option(value, label) },
    )
    private val zoomNote = WrapText("", UiKit.font(12f), Palette.FG_3, wrapWidth = NOTE_WIDTH)
    private val speedChoice = SegmentedChoice(
        "replay-speed-choice",
        listOf(0.1, 0.25, 0.5, 0.75, 1.0).map { SegmentedChoice.Option(it, "${DisplayFormats.decimal(it, 0, 2)}×") },
    )
    private val soundChoice = SegmentedChoice(
        "replay-sound-choice",
        listOf(
            SegmentedChoice.Option(false, tr("replay.editor.sound.slow")),
            SegmentedChoice.Option(true, tr("replay.editor.sound.muted")),
        ),
    )
    private val repeatChoice = SegmentedChoice(
        "replay-repeat-choice",
        (Replays.MIN_REPEATS..Replays.MAX_REPEATS).map { SegmentedChoice.Option(it, it.toString()) },
    )
    private val effectChoice = SegmentedChoice(
        "replay-effect-choice",
        listOf(
            SegmentedChoice.Option(ReplayEffect.FADE, tr("replay.editor.effect.fade")),
            SegmentedChoice.Option(ReplayEffect.FLASH, tr("replay.editor.effect.flash")),
            SegmentedChoice.Option(ReplayEffect.CUT, tr("replay.editor.effect.cut")),
        ),
    )
    private val badgeSwitch = SwitchBox(tr("replay.editor.badge")).apply { name = "replay-badge" }
    private val summary = WrapText("", UiKit.font(12.5f), Palette.FG_2, wrapWidth = NOTE_WIDTH)
    private val emptyNote = WrapText(tr("replay.editor.empty"), UiKit.font(12.5f), Palette.FG_2, wrapWidth = NOTE_WIDTH)
    private val deleteButton = UiButton(tr("common.delete"), Material2AL.DELETE, UiButton.Kind.DANGER).apply { name = "replay-delete" }
    private val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply { name = "replay-save" }
    private val cancelButton = UiButton(tr("common.cancel")).apply { name = "replay-cancel" }
    private val editors = JPanel()

    init {
        name = "replay-editor"
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = close(save = false)
        })
        if (selectIndex == null) edits.add(playheadMs)

        contentPane = JPanel(BorderLayout()).apply {
            background = Palette.PANEL
            add(buildVideoColumn(), BorderLayout.CENTER)
            add(buildSide(), BorderLayout.EAST)
        }
        installKeys()
        wireControls()

        player.onTimeChanged = { t -> EventQueue.invokeLater { onTime(t) } }
        player.onPlaybackRestart = { EventQueue.invokeLater { director.onRestart() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { refreshPreviewButton() } }

        size = Dimension(1240, 760)
        minimumSize = Dimension(900, 560)
        setLocationRelativeTo(owner)
        refresh()

        player.load(videoFile)
        player.pause()
        player.applyPreviewAdjustments(adjustments)
        seek(edits.selected?.startMs?.toLong() ?: playheadMs)
    }

    private fun valueLabel(componentName: String) = JLabel().apply {
        name = componentName
        font = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }

    private fun buildVideoColumn(): JComponent {
        val video = JPanel(BorderLayout()).apply {
            background = Palette.VIDEO_BG
            add(player.component, BorderLayout.CENTER)
        }
        val swatches = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            isOpaque = false
            add(caption(tr("replay.editor.legend.point")).apply { icon = Swatch(Palette.withAlpha(Palette.GREEN, 128), null) })
            add(caption(tr("replay.editor.legend.replay")).apply { icon = Swatch(Palette.LIME_GLOW, Palette.LIME) })
            add(caption(tr("replay.editor.legend.other")).apply { icon = Swatch(null, Palette.LIME_LINE, dashed = true) })
        }
        val legend = JPanel(BorderLayout(16, 0)).apply {
            isOpaque = false
            add(swatches, BorderLayout.WEST)
            add(caption(tr("replay.editor.window.note")).apply {
                horizontalAlignment = JLabel.RIGHT
                toolTipText = text
            }, BorderLayout.CENTER)
        }
        val clock = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(clockNow)
            add(clockAbs)
        }
        fun button(text: String, ikon: org.kordamp.ikonli.Ikon?, componentName: String, tooltip: String, action: () -> Unit) =
            UiButton(text, ikon).apply {
                name = componentName
                toolTipText = tooltip
                isFocusable = false
                addActionListener { action() }
            }
        val transport = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
            isOpaque = false
            add(button(tr("replay.editor.back"), Feather.CHEVRON_LEFT, "replay-back", tr("key.left")) { nudge(-STEP_MS) })
            add(button(tr("replay.editor.startHere"), null, "replay-start-here", "I") { setStartHere() })
            add(previewButton)
            add(button(tr("replay.editor.endHere"), null, "replay-end-here", "O") { setEndHere() })
            add(button(tr("replay.editor.forward"), Feather.CHEVRON_RIGHT, "replay-forward", tr("key.right")) { nudge(STEP_MS) })
        }
        val bar = JPanel(BorderLayout(0, 8)).apply {
            background = Palette.OVERLAY
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
                BorderFactory.createEmptyBorder(10, 16, 12, 16),
            )
            add(legend, BorderLayout.NORTH)
            add(partBar, BorderLayout.CENTER)
            add(JPanel(BorderLayout(16, 0)).apply {
                isOpaque = false
                add(clock, BorderLayout.WEST)
                add(transport, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            add(video, BorderLayout.CENTER)
            add(bar, BorderLayout.SOUTH)
        }
    }

    private fun caption(text: String) = object : JLabel(text) {
        // Some slack, because the painted text can be a little wider than the measured text.
        override fun getPreferredSize(): Dimension = super.getPreferredSize().let { Dimension(it.width + 6, it.height) }
    }.apply {
        font = UiKit.font(11.5f)
        foreground = Palette.FG_3
        iconTextGap = 6
        maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
    }

    /** A small color box of the legend under the video. */
    private class Swatch(private val fill: Color?, private val line: Color?, private val dashed: Boolean = false) : Icon {
        override fun getIconWidth() = 12
        override fun getIconHeight() = 10

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                fill?.let { g2.color = it; g2.fillRect(x, y, 12, 10) }
                line?.let {
                    g2.color = it
                    g2.stroke = if (dashed) BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f, 2f), 0f) else BasicStroke(1f)
                    g2.drawRect(x, y, 11, 9)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private fun buildSide(): JComponent {
        val head = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(14, 16, 6, 16)
            add(JLabel(tr("replay.editor.heading"), UiKit.icon(Material2MZ.SLOW_MOTION_VIDEO, 18, Palette.LIME), JLabel.LEFT).apply {
                font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
                foreground = Palette.FG
                alignmentX = LEFT_ALIGNMENT
            })
            add(sub.apply { alignmentX = LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(10))
            add(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                add(tabs, BorderLayout.CENTER)
                add(addButton, BorderLayout.EAST)
            })
        }
        partGroup.wide(JPanel(FlowLayout(FlowLayout.LEFT, 14, 0)).apply {
            isOpaque = false
            add(timeBox(tr("replay.editor.start"), startValue))
            add(timeBox(tr("replay.editor.end"), endValue))
            add(UiButton(tr("replay.editor.whole"), buttonHeight = 30).apply {
                name = "replay-whole"
                isFocusable = false
                addActionListener { edits.setWhole(); refresh() }
            })
        })
        zoomGroup.wide(zoomChoice)
        zoomGroup.text(zoomNote)
        speedGroup.wide(speedChoice)
        soundGroup.wide(soundChoice)
        repeatGroup.wide(repeatChoice)
        effectGroup.wide(effectChoice)
        editors.apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(6, 12, 12, 12)
            listOf(partGroup, zoomGroup, speedGroup, soundGroup, repeatGroup, effectGroup).forEach { group ->
                group.alignmentX = LEFT_ALIGNMENT
                add(group)
                add(Box.createVerticalStrut(10))
            }
            add(summary.apply { alignmentX = LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(10))
            add(badgeSwitch.apply { alignmentX = LEFT_ALIGNMENT })
        }
        val body = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(editors, BorderLayout.NORTH)
            add(emptyNote.apply { border = BorderFactory.createEmptyBorder(16, 16, 16, 16) }, BorderLayout.CENTER)
        }
        val footer = DialogKit.footer(left = listOf(deleteButton), right = listOf(cancelButton, saveButton))
        return JPanel(BorderLayout()).apply {
            name = "replay-side"
            background = Palette.BG
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            preferredSize = Dimension(SIDE_WIDTH, 0)
            add(head, BorderLayout.NORTH)
            add(DialogKit.scrollBody(body, Palette.BG), BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
    }

    private fun timeBox(caption: String, value: JLabel) = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(caption(caption.uppercase()))
        add(value)
    }

    private fun wireControls() {
        tabs.onChange { index -> stopPreview(); edits.select(index); seekToSelected(); refresh() }
        addButton.addActionListener {
            stopPreview()
            if (edits.add(player.currentTimeMs())) seekToSelected()
            refresh()
        }
        zoomChoice.onChange { factor -> edits.setZoomFactor(factor); refreshSide() }
        speedChoice.onChange { speed -> edits.setSpeed(speed); refreshSide() }
        soundChoice.onChange { muted -> edits.setMuted(muted); refreshSide() }
        repeatChoice.onChange { repeats -> edits.setRepeats(repeats); refreshSide() }
        effectChoice.onChange { effect -> edits.setEffect(effect); refreshSide() }
        badgeSwitch.addActionListener { edits.setShowBadge(badgeSwitch.isSelected); refreshSide() }
        previewButton.addActionListener { togglePreview() }
        deleteButton.addActionListener { stopPreview(); edits.deleteSelected(); seekToSelected(); refresh() }
        cancelButton.addActionListener { close(save = false) }
        saveButton.addActionListener { close(save = true) }
    }

    private fun installKeys() {
        val root = rootPane
        fun bind(key: String, id: String, action: () -> Unit) {
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), id)
            root.actionMap.put(id, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) = action()
            })
        }
        bind("SPACE", "replay.preview") { togglePreview() }
        bind("I", "replay.startHere") { setStartHere() }
        bind("O", "replay.endHere") { setEndHere() }
        bind("LEFT", "replay.back") { nudge(-STEP_MS) }
        bind("RIGHT", "replay.forward") { nudge(STEP_MS) }
        bind("shift LEFT", "replay.backLong") { nudge(-LONG_STEP_MS) }
        bind("shift RIGHT", "replay.forwardLong") { nudge(LONG_STEP_MS) }
        bind("ESCAPE", "replay.cancel") { close(save = false) }
        bind("ENTER", "replay.save") { close(save = true) }
    }

    // ---- Actions -------------------------------------------------------------------------------------

    private fun seek(ms: Long) {
        val target = ms.coerceIn(edits.windowStartMs.toLong(), edits.windowEndMs.toLong())
        player.seek(target)
        partBar.setPlayhead(target)
        refreshClock(target)
    }

    private fun seekToSelected() {
        edits.selected?.let { seek(it.startMs.toLong()) }
    }

    private fun nudge(deltaMs: Long) {
        stopPreview()
        seek(player.currentTimeMs() + deltaMs)
    }

    private fun setStartHere() {
        stopPreview()
        edits.setStart(player.currentTimeMs())
        refresh()
    }

    private fun setEndHere() {
        stopPreview()
        edits.setEnd(player.currentTimeMs())
        refresh()
    }

    /**
     * Preview plays the last second of the point, the selected replay with its repeats, and one second after the
     * point. The director plays the replay as the Points tab does.
     */
    private fun togglePreview() {
        if (previewing) {
            stopPreview()
            return
        }
        val replay = edits.selected ?: return
        previewing = true
        director.stop()
        director.setPoints(listOf(point.copy(replays = listOf(replay))))
        zoomOverlay.visible = false
        partBar.locked = true
        refreshOverlay()
        val from = (point.endMs - PREVIEW_LEAD_MS).coerceAtLeast(point.startMs).toLong()
        player.seek(from)
        player.play()
        refreshPreviewButton()
    }

    private fun stopPreview() {
        if (!previewing) return
        previewing = false
        player.pause()
        apply(director.stop())
        player.setReplayView(null)
        player.setReplaySound(null)
        zoomOverlay.visible = true
        partBar.locked = false
        refreshOverlay()
        refreshPreviewButton()
    }

    private fun onTime(sourceMs: Long) {
        if (closed) return
        if (previewing) {
            apply(director.onTime(sourceMs, player.status() == PlayerStatus.PLAYING))
            refreshOverlay()
            if (!director.isActive && sourceMs >= point.endMs + PREVIEW_LEAD_MS) {
                stopPreview()
                seekToSelected()
                return
            }
        }
        partBar.setPlayhead(sourceMs)
        refreshClock(sourceMs)
    }

    private fun apply(commands: List<ReplayDirector.Command>) {
        for (command in commands) {
            when (command) {
                is ReplayDirector.Command.Seek -> player.seek(command.sourceMs)
                is ReplayDirector.Command.Look -> player.setReplayView(command.view)
                is ReplayDirector.Command.Sound -> player.setReplaySound(command.sound)
            }
        }
    }

    private var badgeShown = false

    /** The badge shows over the video while the preview plays a replay with the badge on. */
    private fun refreshOverlay() {
        val badge = previewing && director.activePlay?.replay?.showBadge == true
        if (badge == badgeShown) return
        badgeShown = badge
        val text = tr("replay.badge")
        player.setPreviewOverlay(
            if (badge) VideoOverlay { area -> ReplayBadgeAss.events(text, area.width, area.height, area.x, area.y) } else null,
        )
    }

    /** Closes the window without Save. */
    fun cancel() = close(save = false)

    private fun close(save: Boolean) {
        if (closed) return
        closed = true
        stopPreview()
        if (save) onSave(edits.replays)
        player.onTimeChanged = null
        player.onPlaybackRestart = null
        player.onStatusChanged = null
        player.setEditorOverlay(null)
        player.setPreviewOverlay(null)
        player.pause()
        player.close()
        dispose()
        onClosed()
    }

    // ---- View ----------------------------------------------------------------------------------------

    private fun refresh() {
        partBar.setState(
            edits.windowStartMs.toLong(),
            edits.windowEndMs.toLong(),
            point.startMs.toLong(),
            point.endMs.toLong(),
            edits.replays,
            edits.selectedIndex,
        )
        tabs.setOptions(edits.replays.indices.map { SegmentedChoice.Option(it, tr("replay.editor.tab", "number" to it + 1)) })
        tabs.selected = edits.selectedIndex.takeIf { it >= 0 }
        addButton.isEnabled = edits.replays.size < MAX_REPLAYS
        sub.text = tr(
            "replay.editor.sub",
            "start" to Timecode.format(point.startMs.toLong()),
            "end" to Timecode.format(point.endMs.toLong()),
        )
        refreshSide()
    }

    private fun refreshSide() {
        val replay = edits.selected
        editors.isVisible = replay != null
        emptyNote.isVisible = replay == null
        deleteButton.isEnabled = replay != null
        previewButton.isEnabled = replay != null
        zoomOverlay.zoom = replay?.zoom
        if (replay == null) return
        startValue.text = relative(replay.startMs.toLong())
        endValue.text = relative(replay.endMs.toLong())
        partGroup.showNote(seconds(replay.endMs - replay.startMs.toLong()))
        val factor = edits.zoomFactor
        zoomChoice.selected = listOf(1.0, 1.5, 2.0, 3.0).firstOrNull { kotlin.math.abs(it - factor) < 0.01 }
        zoomGroup.showNote(if (replay.zoom == null) "" else "${DisplayFormats.decimal(factor, 0, 1)}×")
        zoomNote.show(when {
            replay.zoom == null -> ""
            isSoft(replay) -> tr("replay.editor.zoom.soft")
            else -> tr("replay.editor.zoom.hint")
        })
        speedChoice.selected = listOf(0.1, 0.25, 0.5, 0.75, 1.0).firstOrNull { kotlin.math.abs(it - replay.speed) < 0.001 }
        speedGroup.showNote("${DisplayFormats.decimal(replay.speed, 0, 2)}×")
        soundChoice.selected = replay.muted
        repeatChoice.selected = replay.repeats
        repeatGroup.showNote("×${replay.repeats}")
        effectChoice.selected = replay.effect
        badgeSwitch.isSelected = replay.showBadge
        summary.show(tr(
            "replay.editor.summary",
            "seconds" to DisplayFormats.decimal(Replays.addedMs(replay) / 1000.0, 1),
            "count" to replay.repeats,
            "total" to DisplayFormats.decimal(edits.addedMs / 1000.0, 1),
        ))
        editors.revalidate()
        editors.repaint()
    }

    /** A strong zoom on a small video shows few source pixels: fewer lines than a 540p video. */
    private fun isSoft(replay: ReplayV1): Boolean {
        val zoom = replay.zoom ?: return false
        val height = player.videoSize()?.height ?: return false
        return height * zoom.width < SOFT_LINES
    }

    private fun refreshClock(sourceMs: Long) {
        clockNow.text = relative(sourceMs)
        clockAbs.text = Timecode.format(sourceMs)
    }

    private fun refreshPreviewButton() {
        val playing = previewing && player.status() == PlayerStatus.PLAYING
        previewButton.text = tr(if (playing) "replay.editor.stopPreview" else "replay.editor.preview")
        previewButton.ikon = if (playing) Material2MZ.STOP else Material2MZ.PLAY_ARROW
    }

    /** A time from the point start, for example "+1.2 s" or "−0.8 s". */
    private fun relative(sourceMs: Long): String {
        val delta = sourceMs - point.startMs
        val sign = if (delta < 0) "−" else "+"
        return sign + seconds(kotlin.math.abs(delta))
    }

    private fun seconds(ms: Long) = tr("replay.editor.seconds", "value" to DisplayFormats.decimal(ms / 1000.0, 1))

    /** Replaces the text and keeps the font and the color. */
    private fun WrapText.show(text: String) {
        val run = runs.firstOrNull() ?: return
        runs = listOf(run.copy(text = text))
        revalidate()
    }

    private companion object {
        const val SIDE_WIDTH = 340
        const val NOTE_WIDTH = 290
        const val STEP_MS = 100L
        const val LONG_STEP_MS = 1_000L
        const val PREVIEW_LEAD_MS = 1_000
        const val MAX_REPLAYS = 5
        const val SOFT_LINES = 540.0
    }
}
