package org.litvin.ui.tabs.points.replay

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ReplayBadgeAss
import org.litvin.export.comments.CommentAss
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.tr
import org.litvin.media.PlayerStatus
import org.litvin.media.ReplaySound
import org.litvin.media.ReplayView
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.points.CommentPosition
import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import org.litvin.points.ReplayEffect
import org.litvin.points.ReplayV1
import org.litvin.points.Replays
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.CommentDialog
import org.litvin.ui.commons.CommentInput
import org.litvin.ui.commons.CommentLook
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import org.litvin.ui.tabs.points.CommentDragController
import org.litvin.ui.tabs.points.ReplayCommentClock
import org.litvin.ui.tabs.points.ReplayDirector
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/**
 * The replay editor (B-50 T4): a window that is not modal, with its own player, as in
 * `design/instant-replay/index.html`. The user sets the part, the still frame, the zoom frame, the speed, the sound,
 * and the cut effect of each replay of one point. Preview plays the end of the point with its replay, as the Points tab
 * and the export play it. Save gives all replays of the point to [onSave].
 *
 * The window owns [player] and closes it. [onClosed] runs after the window closes, with Save or without it.
 *
 * The window is a frame, not a dialog, because only a frame has the Maximize button (decision 20). It opens at 80% of
 * the width and the height of the screen of [owner], in the center of that screen.
 *
 * Pick frame starts pick mode (decision 23): the video shows the zoom of the replay, the bar shows only the part, and
 * the user steps one frame at a time to pick the still frame. The side panel is locked during pick mode.
 *
 * The Comments group holds the comments of the selected replay (decision 24). A selected comment turns on the comment
 * view: the video shows the zoom of the replay, as the export shows it, and the user drags the comment on the video.
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
    /** The comments of the project. The preview and the comment view show the point comments (decision 24). */
    private val pointComments: List<CommentV1> = emptyList(),
    /** The color and the look of a new comment. */
    private val newCommentLook: CommentInput = CommentInput(0, 0, "", "#FFFFFF"),
    /** Remembers the look of a saved comment for the next new comment. */
    private val onCommentLook: (CommentLook) -> Unit = {},
) : JFrame(tr("replay.editor.title", "point" to pointTitle)) {
    private val edits = ReplayEditorModel(point, selectIndex ?: 0)
    private val director = ReplayDirector()
    private var previewing = false

    /** Ends the still frame of the preview (decision 22) after its time. */
    private val holdTimer = javax.swing.Timer(0) { endHold() }.apply { isRepeats = false }
    private var closed = false

    /** True during pick mode (decision 23). */
    private var picking = false

    /** The time of the frame that pick mode shows. */
    private var pickMs = 0L

    private val partBar = ReplayPartBar(
        onStart = { ms -> edits.setStart(ms); seek(ms); refresh() },
        onEnd = { ms -> edits.setEnd(ms); seek(ms); refresh() },
        onSeek = { ms -> stopPreview(); seek(ms) },
        onFreeze = { ms -> edits.setFreezeAt(ms); seek(ms); refresh() },
        onCommentSelect = { id -> stopPreview(); selectComment(id, seekToStart = false) },
        onCommentMove = { id, ms ->
            edits.moveComment(id, ms)
            edits.comment(id)?.let { seek(it.startMs.toLong()) }
            refresh()
        },
    )
    private val zoomOverlay = ReplayZoomOverlay(player) { zoom -> edits.setZoom(zoom); refreshSide() }

    // ---- Replay comments (decision 24) ----

    /** The selected comment of the selected replay, or null. */
    private var selectedCommentId: Int? = null

    /** The comment of the open comment dialog, before Save, or null. */
    private var dialogComment: CommentV1? = null

    /** True while the video shows the comment view: the zoom of the replay and the comments. */
    private var commentViewOn = false

    /** The source time that the video shows now. */
    private var shownMs = playheadMs
    private val commentClock = ReplayCommentClock()

    /** Shows the changes of the replay comments during the still frame of the preview. */
    private val commentTicker = javax.swing.Timer(COMMENT_TICK_MS) {
        if (!director.isHolding) (it.source as javax.swing.Timer).stop()
        refreshOverlay()
    }
    private val commentDrag = CommentDragController(
        player = player,
        enabled = { commentViewOn },
        shownComments = { commentViewComments() },
        onPreview = { refreshOverlay() },
        onChanged = { id, position, sizePercent, rotationDeg ->
            edits.placeComment(id, position, sizePercent, rotationDeg)
            refreshSide()
            refreshOverlay()
        },
        onClicked = { id -> selectComment(id, seekToStart = false) },
    )

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
    private val list = ReplayList(
        onSelect = { index -> stopPreview(); clearCommentSelection(); edits.select(index); seekToSelected(); refresh() },
        onDelete = { index ->
            stopPreview()
            clearCommentSelection()
            edits.select(index)
            edits.deleteSelected()
            seekToSelected()
            refresh()
        },
        onMove = { index, delta -> stopPreview(); clearCommentSelection(); edits.select(index); edits.moveSelected(delta); refresh() },
    )
    private val addButton = UiButton(tr("replay.editor.add"), Material2AL.ADD, buttonHeight = 26).apply {
        name = "replay-add"
        toolTipText = tr("replay.editor.add.tooltip")
        isFocusable = false
    }
    private val startValue = valueLabel("replay-start")
    private val endValue = valueLabel("replay-end")
    private val partGroup = DialogGroup(tr("replay.editor.part"), Material2AL.CONTENT_CUT, "replay-part")
    private val freezeGroup = DialogGroup(tr("replay.editor.freeze"), Material2MZ.PAUSE_CIRCLE_OUTLINE, "replay-freeze")
    private val freezeSwitch = SwitchBox(tr("replay.editor.freeze.on")).apply {
        name = "replay-freeze-on"
        // Space plays the preview, so the switch does not take the focus.
        isFocusable = false
    }
    private val freezeChoice = SegmentedChoice(
        "replay-freeze-choice",
        Replays.FREEZE_DURATIONS_MS.map { SegmentedChoice.Option(it, seconds(it.toLong(), decimals = 0)) },
    )
    private val freezeNote = WrapText("", UiKit.font(12f), Palette.FG_3, wrapWidth = NOTE_WIDTH)
    private val pickButton = UiButton(tr("replay.editor.freeze.pick"), Feather.CROSSHAIR, buttonHeight = 30).apply {
        name = "replay-freeze-pick"
        toolTipText = tr("replay.editor.freeze.pick.tooltip")
        isFocusable = false
    }

    private val commentGroup = DialogGroup(tr("replay.editor.comments"), Material2AL.CHAT_BUBBLE_OUTLINE, "replay-comments")
    private val commentList = ReplayCommentList(
        onSelect = { id -> stopPreview(); selectComment(id) },
        onEdit = { id -> editComment(id) },
        onDelete = { id -> deleteComment(id) },
        timeText = { comment -> "${relative(comment.startMs.toLong())} · ${seconds(comment.durationMs.toLong(), decimals = 0)}" },
    )
    private val commentAddButton = UiButton(tr("replay.editor.comments.add"), Material2AL.ADD, buttonHeight = 30).apply {
        name = "replay-comment-add"
        toolTipText = tr("replay.editor.comments.add.tooltip")
        isFocusable = false
    }
    private val pointCommentsSwitch = SwitchBox(tr("replay.editor.comments.point")).apply {
        name = "replay-point-comments"
        isFocusable = false
    }
    private val commentNote = WrapText("", UiKit.font(12f), Palette.FG_3, wrapWidth = NOTE_WIDTH)

    // ---- Pick mode ----
    private val legendCards = JPanel(CardLayout()).apply { isOpaque = false }
    private val transportCards = JPanel(CardLayout()).apply { isOpaque = false }
    private val pickInfo = JLabel().apply {
        name = "replay-pick-frame"
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    private val pickPlayButton = UiButton(tr("replay.editor.pick.play"), Material2MZ.PLAY_ARROW).apply {
        name = "replay-pick-play"
        toolTipText = tr("key.space")
        isFocusable = false
    }
    private val pickUseButton = UiButton(tr("replay.editor.pick.use"), Material2AL.CHECK, UiButton.Kind.LIME).apply {
        name = "replay-pick-use"
        toolTipText = tr("key.enter")
        isFocusable = false
    }
    private val pickCancelButton = UiButton(tr("common.cancel")).apply {
        name = "replay-pick-cancel"
        toolTipText = tr("key.escape")
        isFocusable = false
    }
    private lateinit var side: JComponent
    private val sideLock = SideLock()
    private val zoomGroup = DialogGroup(tr("replay.editor.zoom"), Material2MZ.ZOOM_IN, "replay-zoom")
    private val speedGroup = DialogGroup(tr("replay.editor.speed"), Material2MZ.SPEED, "replay-speed")
    private val soundGroup = DialogGroup(tr("replay.editor.sound"), Material2MZ.VOLUME_UP, "replay-sound")
    private val effectGroup = DialogGroup(tr("replay.editor.effect"), Material2MZ.SLOW_MOTION_VIDEO, "replay-effect")
    private val zoomChoice = SegmentedChoice(
        "replay-zoom-choice",
        listOf(1.0 to tr("replay.editor.zoom.off"), 1.5 to "1.5×", 2.0 to "2×", 3.0 to "3×")
            .map { (value, label) -> SegmentedChoice.Option(value, label) },
    )
    private val zoomNote = WrapText("", UiKit.font(12f), Palette.FG_3, wrapWidth = NOTE_WIDTH)
    private val speedChoice = SegmentedChoice(
        "replay-speed-choice",
        Replays.SPEEDS.map { SegmentedChoice.Option(it, "${DisplayFormats.decimal(it, 0, 2)}×") },
    )
    private val soundChoice = SegmentedChoice(
        "replay-sound-choice",
        listOf(
            SegmentedChoice.Option(false, tr("replay.editor.sound.slow")),
            SegmentedChoice.Option(true, tr("replay.editor.sound.muted")),
        ),
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
    private val labelField = JTextField().apply {
        name = "replay-label"
        (document as AbstractDocument).documentFilter = LengthFilter(Replays.MAX_LABEL_LENGTH)
    }

    /** True while the code sets the label text, so that the change is not a user edit. */
    private var labelSync = false

    /** The replay index that the label field shows. */
    private var labelShownFor = -1

    /** A click outside the label field ends the typing, so that the keys of the editor work again. */
    private val clickOutsideLabel = AWTEventListener { event ->
        val component = (event as? MouseEvent)?.component ?: return@AWTEventListener
        if (event.id == MouseEvent.MOUSE_PRESSED && labelField.isFocusOwner && component !== labelField &&
            SwingUtilities.getWindowAncestor(component) === this
        ) {
            focusVideo()
        }
    }
    private val summary = WrapText("", UiKit.font(12.5f), Palette.FG_2, wrapWidth = NOTE_WIDTH)
    private val emptyNote = WrapText(tr("replay.editor.empty"), UiKit.font(12.5f), Palette.FG_2, wrapWidth = NOTE_WIDTH)
    private val saveButton = UiButton(tr("common.save"), kind = UiButton.Kind.LIME).apply { name = "replay-save" }
    private val cancelButton = UiButton(tr("common.cancel")).apply { name = "replay-cancel" }
    private val editors = JPanel()

    init {
        name = "replay-editor"
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = closeAsked()
        })
        if (selectIndex == null) edits.add(playheadMs)

        contentPane = JPanel(BorderLayout()).apply {
            // The content takes the focus when the user ends the typing of the label.
            isFocusable = true
            background = Palette.PANEL
            add(buildVideoColumn(), BorderLayout.CENTER)
            side = buildSide()
            add(side, BorderLayout.EAST)
        }
        glassPane = sideLock
        // The zoom frame and the comment outline both draw with the editor overlay of the player. One of them shows.
        player.onVideoBoundsChanged = { if (commentViewOn) commentDrag.refresh() else zoomOverlay.redraw() }
        installKeys()
        wireControls()
        Toolkit.getDefaultToolkit().addAWTEventListener(clickOutsideLabel, java.awt.AWTEvent.MOUSE_EVENT_MASK)
        addWindowListener(object : WindowAdapter() {
            // The label field must not take the first focus, because the keys of the editor do not work in it.
            override fun windowOpened(e: WindowEvent?) = focusVideo()
        })

        player.onTimeChanged = { t -> EventQueue.invokeLater { onTime(t) } }
        player.onPlaybackRestart = { EventQueue.invokeLater { director.onRestart() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { refreshPreviewButton(); refreshPickPlay() } }

        owner?.iconImages?.takeIf { it.isNotEmpty() }?.let { iconImages = it }
        placeOnScreenOf(owner)
        refresh()

        player.load(videoFile)
        player.pause()
        player.applyPreviewAdjustments(adjustments)
        seek(edits.selected?.startMs?.toLong() ?: playheadMs)
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
        legendCards.add(legend, CARD_EDIT)
        legendCards.add(caption(tr("replay.editor.pick.hint")).apply { name = "replay-pick-hint" }, CARD_PICK)
        val pickSteps = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
            isOpaque = false
            add(button(tr("replay.editor.pick.back"), Feather.CHEVRON_LEFT, "replay-pick-back", tr("key.left")) { stepFrame(-1) })
            add(pickPlayButton)
            add(button(tr("replay.editor.pick.forward"), Feather.CHEVRON_RIGHT, "replay-pick-forward", tr("key.right")) { stepFrame(1) })
        }
        val pickActions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(pickCancelButton)
            add(pickUseButton)
        }
        val pickTransport = JPanel(BorderLayout(16, 0)).apply {
            isOpaque = false
            add(pickInfo, BorderLayout.WEST)
            add(pickSteps, BorderLayout.CENTER)
            add(pickActions, BorderLayout.EAST)
        }
        transportCards.add(transport, CARD_EDIT)
        transportCards.add(pickTransport, CARD_PICK)
        val bar = JPanel(BorderLayout(0, 8)).apply {
            background = Palette.OVERLAY
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.LINE),
                BorderFactory.createEmptyBorder(10, 16, 12, 16),
            )
            add(legendCards, BorderLayout.NORTH)
            add(partBar, BorderLayout.CENTER)
            add(JPanel(BorderLayout(16, 0)).apply {
                isOpaque = false
                add(clock, BorderLayout.WEST)
                add(transportCards, BorderLayout.CENTER)
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
            // The heading row: the title and the point time on the left, Add on the right.
            add(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                add(JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JLabel(tr("replay.editor.heading"), UiKit.icon(Material2MZ.SLOW_MOTION_VIDEO, 18, Palette.LIME), JLabel.LEFT).apply {
                        font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
                        foreground = Palette.FG
                    })
                    add(sub)
                }, BorderLayout.CENTER)
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(addButton, BorderLayout.NORTH)
                }, BorderLayout.EAST)
            })
            add(Box.createVerticalStrut(10))
            add(list.apply { alignmentX = LEFT_ALIGNMENT })
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
        freezeGroup.wide(freezeSwitch)
        freezeGroup.wide(freezeChoice)
        freezeGroup.wide(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(pickButton)
        })
        commentGroup.wide(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(commentAddButton)
        })
        commentGroup.wide(commentList)
        commentGroup.text(commentNote)
        commentGroup.wide(pointCommentsSwitch)
        freezeGroup.text(freezeNote)
        zoomGroup.wide(zoomChoice)
        zoomGroup.text(zoomNote)
        speedGroup.wide(speedChoice)
        soundGroup.wide(soundChoice)
        effectGroup.wide(effectChoice)
        editors.apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(6, 12, 12, 12)
            listOf(partGroup, freezeGroup, commentGroup, zoomGroup, speedGroup, soundGroup, effectGroup).forEach { group ->
                group.alignmentX = LEFT_ALIGNMENT
                add(group)
                add(Box.createVerticalStrut(10))
            }
            add(summary.apply { alignmentX = LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(10))
            add(badgeSwitch.apply { alignmentX = LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(10))
            add(DialogKit.field(tr("replay.editor.label"), DialogKit.inputBox(labelField), tr("replay.editor.label.note"))
                .apply { alignmentX = LEFT_ALIGNMENT })
        }
        val body = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(editors, BorderLayout.NORTH)
            add(emptyNote.apply { border = BorderFactory.createEmptyBorder(16, 16, 16, 16) }, BorderLayout.CENTER)
        }
        val footer = DialogKit.footer(left = emptyList(), right = listOf(cancelButton, saveButton))
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
        addButton.addActionListener {
            stopPreview()
            clearCommentSelection()
            if (edits.add(player.currentTimeMs())) seekToSelected()
            refresh()
        }
        zoomChoice.onChange { factor -> edits.setZoomFactor(factor); refreshSide() }
        speedChoice.onChange { speed -> edits.setSpeed(speed); refreshSide() }
        soundChoice.onChange { muted -> edits.setMuted(muted); refreshSide() }
        effectChoice.onChange { effect -> edits.setEffect(effect); refreshSide() }
        badgeSwitch.addActionListener { edits.setShowBadge(badgeSwitch.isSelected); refreshSide() }
        freezeSwitch.addActionListener {
            stopPreview()
            edits.setFreeze(freezeSwitch.isSelected, player.currentTimeMs())
            refresh()
        }
        freezeChoice.onChange { durationMs -> edits.setFreezeDuration(durationMs); refreshSide() }
        pickButton.addActionListener { startPick() }
        pickPlayButton.addActionListener { togglePickPlay() }
        pickUseButton.addActionListener { usePickedFrame() }
        pickCancelButton.addActionListener { endPick() }
        commentAddButton.addActionListener { addComment() }
        pointCommentsSwitch.addActionListener {
            edits.setShowPointComments(pointCommentsSwitch.isSelected)
            refreshSide()
            refreshOverlay()
        }
        labelField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = labelTyped()
            override fun removeUpdate(e: DocumentEvent) = labelTyped()
            override fun changedUpdate(e: DocumentEvent) = labelTyped()
        })
        // Enter ends the typing. A blank label shows the default label again when the typing ends.
        labelField.addActionListener { focusVideo() }
        labelField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = refreshLabel(force = true)
        })
        previewButton.addActionListener { togglePreview() }
        cancelButton.addActionListener { close(save = false) }
        saveButton.addActionListener { close(save = true) }
    }

    /**
     * The keys of the editor. While the user types the label, only ESC works, so that the letters and Space go into the
     * label. Enter in the label ends the typing. In pick mode, Left and Right step frames, Enter uses the frame, and ESC
     * ends pick mode without a change.
     */
    private fun installKeys() {
        val root = rootPane
        fun bind(key: String, id: String, action: () -> Unit) {
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), id)
            root.actionMap.put(id, object : AbstractAction() {
                override fun isEnabled() = id == "replay.cancel" || !labelField.isFocusOwner
                override fun actionPerformed(e: ActionEvent?) = action()
            })
        }
        bind("SPACE", "replay.preview") { if (picking) togglePickPlay() else togglePreview() }
        bind("I", "replay.startHere") { if (!picking) setStartHere() }
        bind("O", "replay.endHere") { if (!picking) setEndHere() }
        bind("F", "replay.freezeHere") { if (!picking) setFreezeHere() }
        bind("LEFT", "replay.back") { if (picking) stepFrame(-1) else nudge(-STEP_MS) }
        bind("RIGHT", "replay.forward") { if (picking) stepFrame(1) else nudge(STEP_MS) }
        bind("shift LEFT", "replay.backLong") {
            if (picking) stepFrames(-FramePick.LONG_STEP_FRAMES) else nudge(-LONG_STEP_MS)
        }
        bind("shift RIGHT", "replay.forwardLong") {
            if (picking) stepFrames(FramePick.LONG_STEP_FRAMES) else nudge(LONG_STEP_MS)
        }
        bind("ESCAPE", "replay.cancel") {
            when {
                picking -> endPick()
                selectedCommentId != null -> selectComment(null)
                else -> closeAsked()
            }
        }
        bind("DELETE", "replay.deleteComment") { if (!picking) selectedCommentId?.let { deleteComment(it) } }
        bind("ENTER", "replay.save") { if (picking) usePickedFrame() else close(save = true) }
    }

    // ---- Actions -------------------------------------------------------------------------------------

    private fun focusVideo() {
        contentPane.requestFocusInWindow()
    }

    private fun labelTyped() {
        if (labelSync) return
        edits.setLabel(labelField.text)
        refreshOverlay()
    }

    private fun seek(ms: Long) {
        val part = edits.selected
        val target = if (picking && part != null) {
            ms.coerceIn(part.startMs.toLong(), part.endMs.toLong())
        } else {
            ms.coerceIn(edits.windowStartMs.toLong(), edits.windowEndMs.toLong())
        }
        if (picking) pickMs = target
        player.seek(target)
        partBar.setPlayhead(target)
        refreshClock(target)
        shownMs = target
        if (commentViewOn) refreshOverlay()
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

    /** F puts the still frame at the playhead. It turns the still frame on when it is off. */
    private fun setFreezeHere() {
        stopPreview()
        val now = player.currentTimeMs()
        edits.setFreeze(true, now)
        edits.setFreezeAt(now)
        refresh()
    }

    // ---- Replay comments (decision 24) ----

    /**
     * Selects the comment [id] of the selected replay, or no comment for null. A selected comment turns on the comment
     * view. [seekToStart] moves the playhead to the comment start.
     */
    private fun selectComment(id: Int?, seekToStart: Boolean = true) {
        val comment = id?.let { edits.comment(it) }
        selectedCommentId = comment?.id
        if (comment != null && seekToStart) seek(comment.startMs.toLong())
        refreshCommentView()
        refreshSide()
    }

    private fun clearCommentSelection() {
        if (selectedCommentId == null && dialogComment == null) return
        selectedCommentId = null
        dialogComment = null
        refreshCommentView()
        refreshSide()
    }

    /** The comment view is on while a comment is selected or the comment dialog is open, outside the preview. */
    private fun refreshCommentView() {
        val on = (selectedCommentId != null || dialogComment != null) && !previewing && !picking && edits.selected != null
        if (on != commentViewOn) {
            commentViewOn = on
            if (on) {
                zoomOverlay.visible = false
                applyCommentView()
            } else {
                // The comment outline goes first, so that the zoom frame draws after it.
                commentDrag.refresh()
                player.setReplayView(null)
                zoomOverlay.visible = true
            }
        }
        partBar.selectedCommentId = selectedCommentId
        refreshOverlay()
    }

    /** The video shows the zoom of the selected replay, as the export shows it after the zoom in. */
    private fun applyCommentView() {
        player.setReplayView(edits.selected?.zoom?.let { ReplayView(zoomX = it.x, zoomY = it.y, zoomWidth = it.width) })
    }

    /** The replay comments of the comment view at the playhead, and the selected comment also when it is not on screen. */
    private fun commentViewComments(): List<CommentV1> {
        val replay = edits.selected ?: return emptyList()
        val active = Replays.commentsAtSource(replay, shownMs)
        val chosen = selectedCommentId?.let { edits.comment(it) } ?: return active
        return if (active.any { it.id == chosen.id }) active else active + chosen
    }

    /** The comments of the comment view: the point comments, then the replay comments over them. */
    private fun commentViewOverlay(): List<CommentV1> {
        val replay = edits.selected ?: return emptyList()
        val point = if (replay.showPointComments) CommentAss.activeAt(pointComments, shownMs) else emptyList()
        return point + withDialogComment(commentDrag.displayed(commentViewComments()))
    }

    /** The comments of the preview: the point comments, and the replay comments while the replay plays. */
    private fun previewOverlay(): List<CommentV1> {
        val play = director.activePlay
        val point = if (play != null && !play.replay.showPointComments) emptyList() else CommentAss.activeAt(pointComments, shownMs)
        val replayComments = play?.let { commentClock.comments(it.replay, shownMs, director.isHolding) }.orEmpty()
        return point + replayComments
    }

    /** [comments] with the comment of the open dialog in place of its saved version, or added to the end. */
    private fun withDialogComment(comments: List<CommentV1>): List<CommentV1> {
        val changed = dialogComment ?: return comments
        return if (comments.any { it.id == changed.id }) comments.map { if (it.id == changed.id) changed else it } else comments + changed
    }

    /** Add opens the comment dialog for a new comment at the playhead, or at the part start when the playhead is outside. */
    private fun addComment() {
        val replay = edits.selected ?: return
        stopPreview()
        val now = player.currentTimeMs()
        val start = if (now in replay.startMs.toLong()..replay.endMs.toLong()) now else replay.startMs.toLong()
        seek(start)
        val first = newCommentLook.copy(startMs = start, durationMs = NEW_COMMENT_MS, text = "")
        previewDialogComment(null, first)
        CommentDialog.showCreate(
            this,
            "replay",
            initialStartMs = start,
            defaultColor = newCommentLook.colorHex,
            defaultStyle = newCommentLook.style,
            defaultSizePercent = newCommentLook.sizePercent,
            defaultOpacityPercent = newCommentLook.opacityPercent,
            initialDurationMs = NEW_COMMENT_MS,
            onPreview = { previewDialogComment(null, it) },
        ) { input ->
            val added = edits.addComment(input.toComment(id = 0, position = null))
            onCommentLook(CommentLook(input.style, input.sizePercent, input.opacityPercent))
            refresh()
            added?.let { selectComment(it.id) }
        }
    }

    private fun editComment(id: Int) {
        val comment = edits.comment(id) ?: return
        stopPreview()
        selectComment(id)
        val input = CommentInput(
            comment.startMs.toLong(), comment.durationMs.toLong(), comment.text, comment.colorHex, comment.style,
            comment.sizePercent, comment.opacityPercent, comment.rotationDeg,
        )
        previewDialogComment(id, input)
        CommentDialog.showEdit(this, "replay", id, input, onPreview = { previewDialogComment(id, it) }) { saved ->
            edits.updateComment(saved.toComment(id, comment.position))
            refresh()
            selectComment(id)
        }
    }

    private fun deleteComment(id: Int) {
        stopPreview()
        edits.deleteComment(id)
        if (selectedCommentId == id) selectedCommentId = null
        refreshCommentView()
        refresh()
    }

    /** Shows the comment of the open dialog on the video. A null [input] means that the dialog closed. */
    private fun previewDialogComment(id: Int?, input: CommentInput?) {
        if (input == null) {
            // After Save, the saved comment comes first in the event queue. The preview stops after it.
            EventQueue.invokeLater {
                dialogComment = null
                refreshCommentView()
            }
            return
        }
        val base = id?.let { edits.comment(it) }
        dialogComment = input.toComment(base?.id ?: NEW_COMMENT_ID, base?.position)
        refreshCommentView()
    }

    private fun CommentInput.toComment(id: Int, position: CommentPosition?) = CommentV1(
        id = id,
        startMs = startMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        durationMs = durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        text = text,
        colorHex = colorHex,
        style = style,
        position = position,
        sizePercent = sizePercent,
        opacityPercent = opacityPercent,
        rotationDeg = rotationDeg,
    )

    // ---- Pick mode (decision 23) ----

    /** Starts pick mode on the still frame, or on the start of the part when the replay has no still frame. */
    private fun startPick() {
        val part = edits.selected ?: return
        stopPreview()
        clearCommentSelection()
        focusVideo()
        picking = true
        zoomOverlay.visible = false
        // The video shows the zoom of the replay, as the export shows it after the zoom in.
        player.setReplayView(part.zoom?.let { ReplayView(zoomX = it.x, zoomY = it.y, zoomWidth = it.width) })
        partBar.frameMs = FramePick.frameMs(fps())
        partBar.picking = true
        showCards(CARD_PICK)
        sideLock.isVisible = true
        refresh()
        seek((part.freeze?.atMs ?: part.startMs).toLong())
        refreshPickPlay()
    }

    /** Ends pick mode. The still frame does not change. */
    private fun endPick() {
        if (!picking) return
        pausePick()
        picking = false
        player.setReplayView(null)
        zoomOverlay.visible = true
        partBar.picking = false
        showCards(CARD_EDIT)
        sideLock.isVisible = false
        refresh()
        seek(pickMs)
    }

    /** Puts the still frame on the frame that pick mode shows, and ends pick mode. */
    private fun usePickedFrame() {
        if (!picking) return
        val at = pickMs
        edits.setFreeze(true, at)
        edits.setFreezeAt(at)
        endPick()
    }

    private fun showCards(card: String) {
        (legendCards.layout as CardLayout).show(legendCards, card)
        (transportCards.layout as CardLayout).show(transportCards, card)
    }

    private fun fps(): Double = player.frameRate() ?: FramePick.FALLBACK_FPS

    /** Steps one frame. The step stays inside the part. */
    private fun stepFrame(direction: Int) {
        val part = edits.selected ?: return
        pausePick()
        val target = if (direction > 0) {
            player.stepFrameForward(part.endMs.toLong())
        } else {
            player.stepFrameBackward(part.startMs.toLong())
        }
        showPick(target)
    }

    /** Steps [frames] frames with one exact seek. */
    private fun stepFrames(frames: Int) {
        val part = edits.selected ?: return
        pausePick()
        seek(FramePick.step(pickMs, frames, part.startMs.toLong(), part.endMs.toLong(), fps()))
    }

    private fun showPick(ms: Long) {
        pickMs = ms
        partBar.setPlayhead(ms)
        refreshClock(ms)
    }

    /** Plays the part at the speed of the replay. At the end of the part, the play stops. */
    private fun togglePickPlay() {
        val part = edits.selected ?: return
        if (player.status() == PlayerStatus.PLAYING) {
            pausePick()
            return
        }
        if (pickMs >= part.endMs - END_SLACK_MS) seek(part.startMs.toLong())
        player.setReplaySound(ReplaySound(part.speed, part.muted))
        player.play()
        refreshPickPlay()
    }

    private fun pausePick() {
        if (player.status() == PlayerStatus.PLAYING) player.pause()
        player.setReplaySound(null)
        refreshPickPlay()
    }

    private fun refreshPickPlay() {
        val playing = picking && player.status() == PlayerStatus.PLAYING
        pickPlayButton.text = tr(if (playing) "replay.editor.pick.pause" else "replay.editor.pick.play")
        pickPlayButton.ikon = if (playing) Material2MZ.PAUSE else Material2MZ.PLAY_ARROW
    }

    /**
     * Locks the side panel during pick mode: the glass pane takes the mouse events over the side panel only, and dims
     * it. The video column stays free.
     */
    private inner class SideLock : JComponent() {
        init {
            isOpaque = false
            isVisible = false
            val block = object : MouseAdapter() {}
            addMouseListener(block)
            addMouseMotionListener(block)
            addMouseWheelListener(block)
        }

        private fun area(): java.awt.Rectangle =
            if (!::side.isInitialized || side.parent == null) {
                java.awt.Rectangle()
            } else {
                SwingUtilities.convertRectangle(side.parent, side.bounds, this)
            }

        override fun contains(x: Int, y: Int) = area().contains(x, y)

        override fun paintComponent(g: Graphics) {
            val r = area()
            g.color = Palette.withAlpha(Palette.BG, LOCK_ALPHA)
            g.fillRect(r.x, r.y, r.width, r.height)
        }
    }

    // ---- Preview ----

    /**
     * Preview plays the last second of the point, the selected replay, and one second after the
     * point. The director plays the replay as the Points tab does.
     */
    private fun togglePreview() {
        if (previewing) {
            stopPreview()
            return
        }
        val replay = edits.selected ?: return
        clearCommentSelection()
        previewing = true
        director.stop()
        director.setPoints(listOf(point.copy(replays = listOf(replay))), firstReplayIndex = edits.selectedIndex)
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
        holdTimer.stop()
        commentTicker.stop()
        commentClock.holdEnded()
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
        shownMs = sourceMs
        if (commentViewOn) refreshOverlay()
        if (picking) {
            pickMs = sourceMs
            val end = edits.selected?.endMs?.toLong()
            if (end != null && sourceMs >= end && player.status() == PlayerStatus.PLAYING) {
                pausePick()
                seek(end)
                return
            }
        }
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
                is ReplayDirector.Command.Hold -> {
                    player.pause()
                    player.seek(command.sourceMs)
                    holdTimer.initialDelay = command.durationMs.toInt()
                    holdTimer.restart()
                    commentClock.holdStarted()
                    if (edits.selected?.comments?.isNotEmpty() == true) commentTicker.restart()
                    refreshPreviewButton()
                }
            }
        }
    }

    /** The still frame is over: the preview plays on. After a stop, nothing happens. */
    private fun endHold() {
        holdTimer.stop()
        commentTicker.stop()
        commentClock.holdEnded()
        if (closed || !previewing || !director.endHold()) return
        player.play()
        refreshPreviewButton()
    }

    /** The badge text and the comments that the video shows now, or null before the first overlay. */
    private var overlayShown: Pair<String?, List<CommentV1>>? = null

    /**
     * The overlay of the video. The preview shows the comments (decision 24), and the badge while it plays a replay with
     * the badge on. The preview plays only the selected replay, so the badge has the label of the selected replay. The
     * comment view shows the comments at the playhead. Otherwise the video shows no overlay.
     */
    private fun refreshOverlay() {
        val replay = edits.selected
        val badge = if (previewing && replay != null && director.activePlay?.replay?.showBadge == true) {
            Replays.badgeText(replay, edits.selectedIndex)
        } else {
            null
        }
        val comments = when {
            previewing -> previewOverlay()
            commentViewOn -> commentViewOverlay()
            else -> emptyList()
        }
        val shown = badge to comments
        if (shown != overlayShown) {
            overlayShown = shown
            player.setPreviewOverlay(
                if (badge == null && comments.isEmpty()) {
                    null
                } else {
                    VideoOverlay { area ->
                        CommentAss.previewEvents(comments, area) +
                            (badge?.let { ReplayBadgeAss.events(it, area.width, area.height, area.x, area.y) } ?: emptyList())
                    }
                },
            )
        }
        if (commentViewOn) commentDrag.refresh()
    }

    /** Closes the window without Save and without a question. The app calls it, for example when the tab closes. */
    fun cancel() = close(save = false)

    /**
     * The user closes the window without Save, with the X or with ESC. When there are changes that are not saved, the
     * window asks first, and stays open if the user keeps editing. The Cancel button discards without a question.
     */
    private fun closeAsked() {
        if (closed) return
        if (edits.changed) {
            stopPreview()
            val discard = MessageDialog.confirm(
                this,
                tr("replay.editor.discard.title"),
                tr("replay.editor.discard.question"),
                confirmLabel = tr("replay.editor.discard.confirm"),
                cancelLabel = tr("replay.editor.discard.keep"),
                destructive = true,
            )
            if (!discard) return
        }
        close(save = false)
    }

    private fun close(save: Boolean) {
        if (closed) return
        closed = true
        Toolkit.getDefaultToolkit().removeAWTEventListener(clickOutsideLabel)
        stopPreview()
        commentTicker.stop()
        commentDrag.dispose()
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
        val part = edits.selected
        val pickWindow = picking && part != null
        partBar.setState(
            if (pickWindow) part!!.startMs.toLong() else edits.windowStartMs.toLong(),
            if (pickWindow) part!!.endMs.toLong() else edits.windowEndMs.toLong(),
            point.startMs.toLong(),
            point.endMs.toLong(),
            edits.replays,
            edits.selectedIndex,
        )
        list.setState(edits.replays, edits.selectedIndex)
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
        previewButton.isEnabled = replay != null
        zoomOverlay.zoom = replay?.zoom
        if (replay == null) return
        startValue.text = relative(replay.startMs.toLong())
        endValue.text = relative(replay.endMs.toLong())
        partGroup.showNote(seconds(replay.endMs - replay.startMs.toLong()))
        val freeze = replay.freeze
        freezeSwitch.isSelected = freeze != null
        freezeChoice.isEnabled = freeze != null
        freezeChoice.selected = Replays.FREEZE_DURATIONS_MS.firstOrNull { it == (freeze?.durationMs ?: Replays.DEFAULT_FREEZE_MS) }
        freezeGroup.showNote(freeze?.let { "${seconds(it.durationMs.toLong(), decimals = 0)} · ${relative(it.atMs.toLong())}" }.orEmpty())
        freezeNote.show(if (freeze == null) "" else tr("replay.editor.freeze.hint"))
        val factor = edits.zoomFactor
        zoomChoice.selected = listOf(1.0, 1.5, 2.0, 3.0).firstOrNull { kotlin.math.abs(it - factor) < 0.01 }
        zoomGroup.showNote(if (replay.zoom == null) "" else "${DisplayFormats.decimal(factor, 0, 1)}×")
        zoomNote.show(when {
            replay.zoom == null -> ""
            isSoft(replay) -> tr("replay.editor.zoom.soft")
            else -> tr("replay.editor.zoom.hint")
        })
        speedChoice.selected = Replays.SPEEDS.firstOrNull { kotlin.math.abs(it - replay.speed) < 0.001 }
        speedGroup.showNote("${DisplayFormats.decimal(replay.speed, 0, 2)}×")
        soundChoice.selected = replay.muted
        effectChoice.selected = replay.effect
        badgeSwitch.isSelected = replay.showBadge
        refreshLabel(force = false)
        summary.show(tr(
            "replay.editor.summary",
            "seconds" to DisplayFormats.decimal(Replays.addedMs(replay) / 1000.0, 1),
            "total" to DisplayFormats.decimal(edits.addedMs / 1000.0, 1),
        ))
        commentList.setState(replay.comments, selectedCommentId)
        commentGroup.showNote(if (replay.comments.isEmpty()) "" else replay.comments.size.toString())
        commentNote.show(tr(if (replay.comments.isEmpty()) "replay.editor.comments.empty" else "replay.editor.comments.hint"))
        pointCommentsSwitch.isSelected = replay.showPointComments
        partBar.selectedCommentId = selectedCommentId
        // The zoom choice can change the zoom of the comment view. The zoom frame cleared the comment outline.
        if (commentViewOn) {
            applyCommentView()
            commentDrag.refresh()
        }
        editors.revalidate()
        editors.repaint()
    }

    /** A strong zoom on a small video shows few source pixels: fewer lines than a 540p video. */
    private fun isSoft(replay: ReplayV1): Boolean {
        val zoom = replay.zoom ?: return false
        val height = player.videoSize()?.height ?: return false
        return height * zoom.width < SOFT_LINES
    }

    /**
     * Shows the label of the selected replay. While the user types, the field keeps the typed text, unless the
     * selection changes. [force] also replaces the typed text, for example a blank text with the default label.
     */
    private fun refreshLabel(force: Boolean) {
        if (closed || edits.selected == null) return
        if (!force && labelField.isFocusOwner && labelShownFor == edits.selectedIndex) return
        labelShownFor = edits.selectedIndex
        val text = edits.selectedLabel
        if (labelField.text == text) return
        labelSync = true
        try {
            labelField.text = text
        } finally {
            labelSync = false
        }
    }

    private fun refreshClock(sourceMs: Long) {
        clockNow.text = relative(sourceMs)
        clockAbs.text = Timecode.format(sourceMs)
        if (picking) refreshPickInfo(sourceMs)
    }

    /** For example "Frame 37 of 60 · +1.23 s". The time has 2 decimals, so that each frame step shows. */
    private fun refreshPickInfo(sourceMs: Long) {
        val part = edits.selected ?: return
        val start = part.startMs.toLong()
        val end = part.endMs.toLong()
        val fps = fps()
        val frame = tr(
            "replay.editor.pick.frame",
            "index" to FramePick.index(sourceMs, start, end, fps),
            "count" to FramePick.count(start, end, fps),
        )
        val delta = sourceMs - point.startMs
        val time = (if (delta < 0) "−" else "+") +
            tr("replay.editor.seconds", "value" to DisplayFormats.decimal(kotlin.math.abs(delta) / 1000.0, 2, 2))
        pickInfo.text = "$frame · $time"
    }

    private fun refreshPreviewButton() {
        val playing = previewing && (player.status() == PlayerStatus.PLAYING || director.isHolding)
        previewButton.text = tr(if (playing) "replay.editor.stopPreview" else "replay.editor.preview")
        previewButton.ikon = if (playing) Material2MZ.STOP else Material2MZ.PLAY_ARROW
    }

    /** A time from the point start, for example "+1.2 s" or "−0.8 s". */
    private fun relative(sourceMs: Long): String {
        val delta = sourceMs - point.startMs
        val sign = if (delta < 0) "−" else "+"
        return sign + seconds(kotlin.math.abs(delta))
    }

    /** A time in seconds, for example "1.2 s". [decimals] is the least number of decimals. */
    private fun seconds(ms: Long, decimals: Int = 1) =
        tr("replay.editor.seconds", "value" to DisplayFormats.decimal(ms / 1000.0, decimals, 1))

    /** Replaces the text and keeps the font and the color. */
    private fun WrapText.show(text: String) {
        val run = runs.firstOrNull() ?: return
        runs = listOf(run.copy(text = text))
        revalidate()
    }

    /** Keeps the text at [max] characters or fewer. */
    private class LengthFilter(private val max: Int) : DocumentFilter() {
        override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) =
            replace(fb, offset, 0, text, attr)

        override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
            val room = max - (fb.document.length - length)
            fb.replace(offset, length, text.orEmpty().take(room.coerceAtLeast(0)), attrs)
        }
    }

    private companion object {
        const val SIDE_WIDTH = 340
        const val SCREEN_SHARE = 0.8
        const val MIN_WIDTH = 900
        const val MIN_HEIGHT = 560
        const val NOTE_WIDTH = 290
        const val STEP_MS = 100L
        const val LONG_STEP_MS = 1_000L
        const val PREVIEW_LEAD_MS = 1_000
        const val MAX_REPLAYS = 5
        const val SOFT_LINES = 540.0
        const val CARD_EDIT = "edit"

        /** The id of a new comment in the dialog preview. Saved comments have positive ids. */
        const val NEW_COMMENT_ID = -1

        /** The first time of a new replay comment. A replay is short, so it is shorter than for a point comment. */
        const val NEW_COMMENT_MS = 2_000L
        const val COMMENT_TICK_MS = 40
        const val CARD_PICK = "pick"

        /** A play that starts this close to the part end starts at the part start. */
        const val END_SLACK_MS = 20L
        const val LOCK_ALPHA = 150
    }
}
