package org.litvin.ui.tabs.scoring.ui

import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ScoreboardDisplay
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DialogGroup
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ScoreboardSceneImage
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.SliderValueFormat
import org.litvin.ui.commons.SliderValueRow
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.TextRun
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Modal "Scoreboard style" dialog. It sets up the scoreboard: style, title, player colors, serve ball, app credit line,
 * position, size, background opacity and accent color. The layout comes from design/dialogs-redesign/scoreboard-style.html:
 * the preview and the style gallery on the left, a side panel with three groups on the right.
 *
 * The dialog shows the result in a 16:9 frame. It also sends each change to the `onPreview` callback,
 * so the video preview can show the change at once.
 */
class ScoreboardSettingsDialog private constructor(
    owner: Window?,
    initial: ScoreboardSettingsV1,
    private val sample: ScoreboardDisplay,
    loadFrame: ((BufferedImage?) -> Unit) -> Unit,
    private val onPreview: (ScoreboardSettingsV1) -> Unit,
) : JDialog(owner, "Scoreboard style", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object {
        private const val DIALOG_WIDTH = 1120
        private const val DIALOG_HEIGHT = 800
        private const val SIDE_WIDTH = 340
        private const val STYLE_COLUMNS = 4
        private const val THUMB_HEIGHT = 64
        private const val THUMBNAIL_SCALE = 0.42
        private const val THUMBNAIL_MAX_WIDTH = 150.0
        private const val THUMBNAIL_MAX_HEIGHT = 54.0

        /**
         * Shows the dialog and waits until it closes. Returns the new settings after Save,
         * or null after Cancel. [sample] is the score that the previews show. When the sample has no server,
         * the previews show player 1 as the server, so that the "Serve indicator" option has a visible result.
         * [loadFrame] reads the video frame for the previews and calls its argument with the frame, or with null.
         * Until the frame is there, the previews show a drawn court.
         */
        fun show(
            parent: Component,
            initial: ScoreboardSettingsV1,
            sample: ScoreboardDisplay,
            loadFrame: ((BufferedImage?) -> Unit) -> Unit = {},
            onPreview: (ScoreboardSettingsV1) -> Unit = {},
        ): ScoreboardSettingsV1? {
            val previewSample = if (sample.server == 0) sample.copy(server = 1) else sample
            val dialog = ScoreboardSettingsDialog(SwingUtilities.getWindowAncestor(parent), initial.normalized(), previewSample, loadFrame, onPreview)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    private var settings = initial
    private var result: ScoreboardSettingsV1? = null
    private var updatingControls = false

    private val styleCards = ScoreboardStyleId.entries.associateWith { StyleCard(it) }
    private val styleGroup = DialogGroup("Style", Material2MZ.STYLE, "scoreboard-style-group")
    private val positionChoice = SegmentedChoice(
        "scoreboard-position",
        ScoreboardPosition.entries.map { position ->
            SegmentedChoice.Option(position, "", tooltip = position.title, glyph = CornerGlyph(position))
        },
    ).apply { onChange { position -> update { it.copy(position = position) } } }

    /** The video frame under the previews, or null while it loads or when ffmpeg cannot read it. */
    private var frame: BufferedImage? = null

    /** A small copy of [frame] for the style cards, so that the cards do not scale the large frame on each paint. */
    private var cardFrame: BufferedImage? = null
    private val titleField = JTextField(16).apply {
        name = "scoreboard-title"
        (document as? AbstractDocument)?.documentFilter = MaxLengthFilter(ScoreboardSettingsV1.MAX_TITLE_LENGTH)
        toolTipText = "The text in the title bar of the scoreboard"
        getAccessibleContext().accessibleName = "Title"
    }
    private val showTitle = SwitchBox("Title").apply {
        name = "scoreboard-show-title"
    }
    private val showPlayerColors = SwitchBox("Player colors").apply {
        name = "scoreboard-show-player-colors"
        toolTipText = "Show the color of each player next to the name"
    }
    private val showServe = SwitchBox("Serve indicator").apply {
        name = "scoreboard-show-serve"
        toolTipText = "Show a ball next to the player who serves. Mark the server on the Scoring tab"
    }
    private val showAppCredit = SwitchBox("“${ScoreboardSettingsV1.APP_CREDIT}” line").apply {
        name = "scoreboard-show-app-credit"
        toolTipText = "Show a line with the app name at the bottom of the scoreboard"
    }
    private val sizeRow = SliderValueRow(
        "scoreboard-size", "Size", "The size of the scoreboard",
        ScoreboardSettingsV1.MIN_SIZE_PERCENT, ScoreboardSettingsV1.MAX_SIZE_PERCENT, ScoreboardSettingsV1().sizePercent,
        emptyList(), "${ScoreboardSettingsV1.MIN_SIZE_PERCENT} %", null, "${ScoreboardSettingsV1.MAX_SIZE_PERCENT} %",
        percentFormat(), first = false,
    )
    private val sizeSlider = sizeRow.slider
    private val opacityRow = SliderValueRow(
        "scoreboard-opacity", "Background", "The opacity of the scoreboard background",
        ScoreboardSettingsV1.MIN_OPACITY_PERCENT, 100, ScoreboardLayouts.defaults(initial.style).backgroundOpacityPercent,
        emptyList(), "${ScoreboardSettingsV1.MIN_OPACITY_PERCENT} %", null, "100 %",
        percentFormat(), first = false,
    )
    private val opacitySlider = opacityRow.slider
    private val accentButton = UiButton("Change…").apply {
        name = "scoreboard-accent"
        toolTipText = "Choose the accent color of the scoreboard"
    }
    private val accentDefault = UiButton("Reset to default", kind = UiButton.Kind.QUIET).apply {
        name = "scoreboard-accent-default"
        toolTipText = "Use the color of the style"
    }
    private val accentNote = WrapText("", UiKit.font(11.5f), Palette.FG_3, 1.45f, SIDE_WIDTH - 28)
    private val framePreview = FramePreview()

    init {
        name = "scoreboard-settings-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        titleField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTitleChanged()
            override fun removeUpdate(e: DocumentEvent) = onTitleChanged()
            override fun changedUpdate(e: DocumentEvent) = onTitleChanged()
        })
        showTitle.addActionListener { update { it.copy(showTitle = showTitle.isSelected) } }
        showAppCredit.addActionListener { update { it.copy(showAppCredit = showAppCredit.isSelected) } }
        showPlayerColors.addActionListener { update { it.copy(showPlayerColors = showPlayerColors.isSelected) } }
        showServe.addActionListener { update { it.copy(showServe = showServe.isSelected) } }
        sizeSlider.addChangeListener { update { it.copy(sizePercent = sizeSlider.value) } }
        opacitySlider.addChangeListener { update { it.copy(backgroundOpacityPercent = opacitySlider.value) } }
        accentButton.addActionListener {
            val chosen = ColorPickerDialog.pick(this, "Choose accent color", Color(accentRgb()))
            if (chosen != null) update { it.copy(accentColorHex = ColorPickerDialog.hex(chosen)) }
        }
        accentDefault.addActionListener { update { it.copy(accentColorHex = null) } }

        val saveButton = UiButton("Save", kind = UiButton.Kind.LIME).apply {
            name = "scoreboard-save"
            addActionListener {
                result = settings.normalized()
                dispose()
            }
        }
        val cancelButton = UiButton("Cancel").apply {
            name = "scoreboard-cancel"
            addActionListener { cancel() }
        }
        val resetButton = UiButton("Reset to defaults", Feather.ROTATE_CCW, UiButton.Kind.QUIET).apply {
            name = "scoreboard-reset"
            toolTipText = "Use the default style, title, position, size and colors"
            addActionListener { update { ScoreboardSettingsV1() } }
        }

        val body = JPanel(BorderLayout(16, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(8, DialogKit.PAD_X, 16, DialogKit.PAD_X)
            add(LeftColumn(), BorderLayout.CENTER)
            add(sidePanel(), BorderLayout.EAST)
        }
        contentPane = JPanel(BorderLayout()).apply {
            background = Palette.OVERLAY
            add(head(), BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
            add(DialogKit.footer(right = listOf(resetButton, DialogKit.gap(16), cancelButton, saveButton)), BorderLayout.SOUTH)
        }
        rootPane.defaultButton = saveButton
        DialogKit.onEscape(this) { cancel() }

        syncControls()
        pack()
        setSize(fitToScreen(size.height))
        minimumSize = Dimension(900, 620)
        loadFrame { image ->
            if (image != null && isDisplayable) {
                frame = image
                cardFrame = scaledCopy(image, CARD_FRAME_WIDTH)
                framePreview.repaint()
                styleCards.values.forEach { it.repaint() }
            }
        }
        // Show the selected style when the list is longer than the visible part.
        SwingUtilities.invokeLater {
            styleCards[settings.style]?.let { it.scrollRectToVisible(Rectangle(0, 0, it.width, it.height)) }
            saveButton.requestFocusInWindow()
        }
    }

    /**
     * The dialog size: 1120 × 800, or less on a small screen. When the side panel needs more than 800 px
     * and the screen has the space, the dialog is taller, so that the side panel does not scroll.
     * [packedHeight] is the height that shows all of the side panel.
     */
    private fun fitToScreen(packedHeight: Int): Dimension {
        val height = max(DIALOG_HEIGHT, packedHeight)
        return runCatching {
            val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = toolkit.getScreenInsets(config)
            Dimension(
                min(DIALOG_WIDTH, config.bounds.width - insets.left - insets.right - 40),
                min(height, config.bounds.height - insets.top - insets.bottom - 40),
            )
        }.getOrDefault(Dimension(DIALOG_WIDTH, height))
    }

    /** A copy of [image] that is [width] px wide. It scales in steps of one half, so the small copy stays sharp. */
    private fun scaledCopy(image: BufferedImage, width: Int): BufferedImage {
        var current = image
        while (current.width > width) {
            val w = max(width, current.width / 2)
            val h = max(1, (current.height.toLong() * w / current.width).toInt())
            val next = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = next.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(current, 0, 0, w, h, null)
            } finally {
                g.dispose()
            }
            current = next
        }
        return current
    }

    /** The title, and a line that tells how the preview works. */
    private fun head(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        (layout as FlowLayout).alignOnBaseline = true
        border = BorderFactory.createEmptyBorder(16, DialogKit.PAD_X, 6, DialogKit.PAD_X)
        add(JLabel("Scoreboard style").apply {
            font = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
            foreground = Palette.FG
        })
        add(JLabel("   The video shows each change at once. Cancel restores the saved style.").apply {
            font = UiKit.font(12f)
            foreground = Palette.FG_3
        })
    }

    private fun sidePanel(): JComponent {
        val onBoard = DialogGroup("On the board", Material2MZ.TEXT_FIELDS, labelWidth = 92).apply {
            wide(showTitle)
            indented(DialogKit.inputBox(titleField, 32), left = 54)
            wide(showPlayerColors, line = true)
            wide(showServe)
            wide(showAppCredit)
        }
        val place = DialogGroup("Place and size", Material2MZ.PICTURE_IN_PICTURE, labelWidth = 92).apply {
            row("Position", positionChoice)
            custom(sizeRow)
            custom(opacityRow)
        }
        val accent = DialogGroup("Accent color", Material2MZ.PALETTE, labelWidth = 92).apply {
            wide(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                (layout as FlowLayout).hgap = 0
                add(accentButton)
                add(DialogKit.gap(8))
                add(accentDefault)
            })
            text(accentNote, top = 0)
        }
        val column = Stack().apply {
            add(onBoard, gapAfter = 12)
            add(place, gapAfter = 12)
            add(accent)
        }
        return DialogKit.scrollBody(column).apply {
            // The preferred height shows all groups. The dialog uses it when the screen is tall enough.
            preferredSize = Dimension(SIDE_WIDTH + 8, column.heightForWidth(SIDE_WIDTH + 8) + 2)
            viewport.background = Palette.OVERLAY
        }
    }

    private fun percentFormat() = SliderValueFormat(
        unit = "%",
        step = 1,
        toText = { it.toString() },
        fromText = { SliderValueFormat.parse(it, "%")?.roundToInt() },
    )

    private fun onTitleChanged() {
        update { it.copy(title = titleField.text) }
    }

    /** Closes without a result. The caller restores the saved settings on the video. */
    private fun cancel() = dispose()

    /** Applies [change] to the settings, then refreshes the controls and the previews. */
    private fun update(change: (ScoreboardSettingsV1) -> ScoreboardSettingsV1) {
        if (updatingControls) return
        settings = change(settings)
        syncControls()
        onPreview(settings.normalized())
    }

    private fun syncControls() {
        updatingControls = true
        try {
            val defaults = ScoreboardLayouts.defaults(settings.style)
            styleCards.forEach { (style, card) -> card.isSelected = style == settings.style }
            styleGroup.showNote(settings.style.title)
            positionChoice.selected = settings.position
            if (titleField.text != settings.title) titleField.text = settings.title
            showTitle.isSelected = settings.showTitle
            titleField.isEnabled = settings.showTitle
            showAppCredit.isSelected = settings.showAppCredit
            showPlayerColors.isSelected = settings.showPlayerColors
            showServe.isSelected = settings.showServe
            sizeSlider.value = settings.sizePercent
            // The default of the background slider is the opacity of the style.
            opacityRow.default = defaults.backgroundOpacityPercent
            opacitySlider.value = settings.backgroundOpacityPercent ?: defaults.backgroundOpacityPercent
            val accent = Color(accentRgb())
            accentButton.swatch = accent
            accentButton.detail = ColorPickerDialog.hex(accent)
            accentDefault.isEnabled = settings.accentColorHex != null
            val styleHex = "#%06X".format(defaults.accentRgb and 0xFFFFFF)
            accentNote.runs = listOf(
                TextRun(
                    if (settings.normalized().accentColorHex != null) "Own color. The ${settings.style.title} style color is $styleHex."
                    else "The color of the ${settings.style.title} style.",
                    UiKit.font(11.5f),
                    Palette.FG_3,
                ),
            )
            styleCards.forEach { (style, card) ->
                // A style card shows that style with the other current settings.
                val cardSettings = settings.copy(style = style, backgroundOpacityPercent = null, sizePercent = 100)
                val scene = ScoreboardLayouts.scene(sample, cardSettings)
                // Wide or tall styles get a smaller thumbnail, so that every card has the same size.
                val scale = minOf(THUMBNAIL_SCALE, THUMBNAIL_MAX_WIDTH / scene.width, THUMBNAIL_MAX_HEIGHT / scene.height)
                card.thumbnail = ScoreboardSceneImage.render(scene, scale)
            }
            framePreview.repaint()
        } finally {
            updatingControls = false
        }
    }

    private fun accentRgb(): Int {
        val hex = settings.normalized().accentColorHex
        return hex?.removePrefix("#")?.toIntOrNull(16) ?: ScoreboardLayouts.defaults(settings.style).accentRgb
    }

    /** The preview on the top and the style gallery under it. The preview keeps 16:9. */
    private inner class LeftColumn : JPanel(null) {
        private val stage = Stage()
        private val gallery = GalleryPanel().apply {
            ScoreboardStyleId.entries.forEach { add(styleCards.getValue(it)) }
        }
        private val galleryScroll = JScrollPane(gallery).apply {
            name = "scoreboard-style-list"
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 24
            runCatching { applyDarkScrollbar(this, Palette.CARD) }
        }

        init {
            isOpaque = false
            styleGroup.fill(galleryScroll)
            add(stage)
            add(styleGroup)
        }

        override fun doLayout() {
            val stageHeight = min(width * 9 / 16, height - GAP - MIN_GALLERY).coerceAtLeast(120)
            stage.setBounds(0, 0, width, stageHeight)
            styleGroup.setBounds(0, stageHeight + GAP, width, max(0, height - stageHeight - GAP))
        }

        override fun getPreferredSize() = Dimension(600, 600)
    }

    /** The black stage with the 16:9 frame in its middle, like the video area of the tabs. */
    private inner class Stage : JPanel(null) {
        init {
            isOpaque = false
            add(framePreview)
        }

        override fun doLayout() {
            val frameWidth = min(width.toDouble(), height * 16.0 / 9.0)
            val frameHeight = frameWidth * 9.0 / 16.0
            framePreview.setBounds(
                ((width - frameWidth) / 2).roundToInt(), ((height - frameHeight) / 2).roundToInt(),
                frameWidth.roundToInt(), frameHeight.roundToInt(),
            )
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.VIDEO_BG, Palette.LINE)
            } finally {
                g2.dispose()
            }
        }
    }

    /** The style cards in 4 columns. The panel follows the width of the scroll pane. */
    private class GalleryPanel : JPanel(GridLayout(0, STYLE_COLUMNS, 8, 8)), Scrollable {
        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 12, 12, 12)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 24
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = max(24, visibleRect.height - 24)
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    /** One style of the gallery: the thumbnail of the board and the style name. */
    private inner class StyleCard(private val style: ScoreboardStyleId) : JButton(style.title) {
        var thumbnail: BufferedImage? = null
            set(value) {
                field = value
                repaint()
            }

        init {
            name = "scoreboard-style-${style.name.lowercase().replace('_', '-')}"
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = style.title
            addActionListener { update { it.copy(style = style) } }
        }

        override fun getPreferredSize() = Dimension(150, 6 + THUMB_HEIGHT + 6 + 16 + 7)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val hover = model.isRollover
                val fill = when {
                    isSelected -> Palette.LIME_ROW
                    hover -> Palette.OVERLAY
                    else -> Palette.PANEL_DIM
                }
                UiKit.paintBox(g2, 0, 0, width, height, 6, fill, if (isSelected) Palette.LIME else Palette.LINE)
                if (isSelected) UiKit.paintBox(g2, 1, 1, width - 2, height - 2, 5, null, Palette.LIME)
                if (isFocusOwner && !isSelected) UiKit.paintBox(g2, 0, 0, width, height, 6, null, Palette.LIME_LINE)
                val thumbX = 6
                val thumbW = width - 12
                val clip = RoundRectangle2D.Double(thumbX.toDouble(), 6.0, thumbW.toDouble(), THUMB_HEIGHT.toDouble(), 8.0, 8.0)
                g2.paint = GradientPaint(0f, 6f, Palette.COURT_GRASS, 0f, 6f + THUMB_HEIGHT, Palette.COURT_GRASS_DARK)
                g2.fill(clip)
                val old = g2.clip
                g2.clip(clip)
                // The video frame fills the thumbnail, and the parts outside the thumbnail are cut off.
                cardFrame?.let { image ->
                    val scale = max(thumbW.toDouble() / image.width, THUMB_HEIGHT.toDouble() / image.height)
                    val w = (image.width * scale).roundToInt()
                    val h = (image.height * scale).roundToInt()
                    g2.drawImage(image, thumbX + (thumbW - w) / 2, 6 + (THUMB_HEIGHT - h) / 2, w, h, null)
                }
                thumbnail?.let { image ->
                    val scale = min(1.0, min((thumbW - 8.0) / image.width, (THUMB_HEIGHT - 8.0) / image.height))
                    val w = (image.width * scale).roundToInt()
                    val h = (image.height * scale).roundToInt()
                    g2.drawImage(image, thumbX + (thumbW - w) / 2, 6 + (THUMB_HEIGHT - h) / 2, w, h, null)
                }
                g2.clip = old
                val font = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
                val label = UiKit.ellipsize(style.title, font, width - 12f)
                UiKit.drawText(
                    g2, label, font, if (isSelected || hover) Palette.FG else Palette.FG_2,
                    (width - UiKit.textWidth(label, font)) / 2f, 6f + THUMB_HEIGHT + 4f, 16f,
                )
            } finally {
                g2.dispose()
            }
        }
    }

    /** A small 16:9 frame with the board in one corner, for a segment of the Position choice. */
    private class CornerGlyph(private val position: ScoreboardPosition) : SegmentedChoice.Glyph {
        override fun paint(g2: Graphics2D, area: Rectangle, color: Color, selected: Boolean) {
            g2.color = color
            g2.stroke = BasicStroke(1.2f)
            g2.draw(RoundRectangle2D.Double(area.x + 0.5, area.y + 0.5, area.width - 1.0, area.height - 1.0, 4.0, 4.0))
            val w = (area.width * 0.42).roundToInt()
            val h = (area.height * 0.40).roundToInt()
            val left = position.name.endsWith("LEFT")
            val top = position.name.startsWith("TOP")
            val x = if (left) area.x + CORNER_INSET else area.x + area.width - CORNER_INSET - w
            val y = if (top) area.y + CORNER_INSET else area.y + area.height - CORNER_INSET - h
            g2.color = if (selected) Palette.LIME else color
            g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), 2.0, 2.0))
        }
    }

    /** A 16:9 frame with the video frame, or a drawn court while there is no frame. It places the board with the same rules as the video. */
    private inner class FramePreview : JComponent() {
        init {
            name = "scoreboard-frame-preview"
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val w = width.toDouble()
                val h = height.toDouble()
                val image = frame
                if (image != null) {
                    // The frame fits in the 16:9 area. The export also keeps the frame whole.
                    g2.color = Palette.VIDEO_BG
                    g2.fillRect(0, 0, width, height)
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    val scale = min(w / image.width, h / image.height)
                    val iw = (image.width * scale).roundToInt()
                    val ih = (image.height * scale).roundToInt()
                    g2.drawImage(image, (width - iw) / 2, (height - ih) / 2, iw, ih, null)
                } else {
                    paintCourt(g2, w, h)
                }

                val current = settings.normalized()
                val scene = ScoreboardLayouts.scene(sample, current)
                val placement = ScoreboardAss.place(scene, current, 0.0, 0.0, w, h)
                ScoreboardSceneImage.draw(g2, scene, placement.x, placement.y, placement.scale)
            } finally {
                g2.dispose()
            }
        }

        /** A green court with lines in perspective, so the area reads as a match video. */
        private fun paintCourt(g2: Graphics2D, w: Double, h: Double) {
            g2.paint = GradientPaint(0f, 0f, Palette.COURT_GRASS, 0f, h.toFloat(), Palette.COURT_GRASS_DARK)
            g2.fillRect(0, 0, width, height)
            g2.color = Palette.COURT_LINE
            g2.stroke = BasicStroke(2f)
            g2.draw(Line2D.Double(w * 0.22, h * 0.95, w * 0.36, h * 0.30))
            g2.draw(Line2D.Double(w * 0.78, h * 0.95, w * 0.64, h * 0.30))
            g2.draw(Line2D.Double(w * 0.36, h * 0.30, w * 0.64, h * 0.30))
            g2.draw(Line2D.Double(w * 0.28, h * 0.70, w * 0.72, h * 0.70))
            g2.draw(Line2D.Double(w * 0.50, h * 0.70, w * 0.50, h * 0.30))
            g2.color = Palette.COURT_LINE_2
            g2.stroke = BasicStroke(3f)
            g2.draw(Line2D.Double(w * 0.14, h * 0.52, w * 0.86, h * 0.52))
        }
    }
}

private const val GAP = 12
private const val MIN_GALLERY = 200
private const val CORNER_INSET = 2

/** The width of the small frame copy for the style cards. It is about two times the card width, for high-DPI screens. */
private const val CARD_FRAME_WIDTH = 320
